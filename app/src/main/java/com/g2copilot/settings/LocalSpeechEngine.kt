package com.g2copilot.settings

import android.content.Context
import android.os.ParcelFileDescriptor
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Streams G2's native 16 kHz mono PCM through Pixel's on-device recognizer. */
internal class LocalSpeechEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generation = AtomicLong()
    private val translationSequence = AtomicLong()
    private var prepareJob: Job? = null
    private var recognitionJob: Job? = null
    private var recognizer: SpeechRecognizer? = null
    private var writer: OutputStream? = null
    private var translator: Translator? = null
    private var translationReady = false
    private var localeTag = "en-US"
    @Volatile private var ready = false
    @Volatile private var active = false
    @Volatile private var sourceText = ""
    @Volatile private var englishText = ""
    @Volatile private var finalText = false
    @Volatile private var revision = 0L
    @Volatile private var error = ""

    @Synchronized fun start(tag: String): JSONObject {
        stopLocked()
        localeTag = tag.ifBlank { "en-US" }
        sourceText = ""; englishText = ""; finalText = false; error = ""; ready = false; active = false
        revision++
        val run = generation.incrementAndGet()
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.forLanguageTag(localeTag)
            preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
        }.build()
        recognizer = SpeechRecognition.getClient(options)
        prepareTranslator(run)
        prepareJob = scope.launch {
            try {
                val r = recognizer ?: return@launch
                when (r.checkStatus()) {
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> r.download().collect { }
                    FeatureStatus.UNAVAILABLE -> throw IllegalStateException("On-device speech model is unavailable for $localeTag")
                }
                if (run != generation.get()) return@launch
                ready = true; revision++
            } catch (t: Throwable) {
                if (run == generation.get()) fail(t.message ?: t.javaClass.simpleName)
            }
        }
        return snapshot()
    }

    @Synchronized private fun beginRecognitionLocked(run: Long) {
        if (!ready || active || recognizer == null) return
        recognitionJob?.cancel()
        val pipe = ParcelFileDescriptor.createPipe()
        writer = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        sourceText = ""; englishText = ""; finalText = false; error = ""; active = true; revision++
        recognitionJob = scope.launch {
            try {
                val request = SpeechRecognizerRequest.Builder().apply { audioSource = AudioSource.fromPfd(pipe[0]) }.build()
                recognizer?.startRecognition(request)?.collect { response ->
                    if (run != generation.get()) return@collect
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> update(response.text, false, run)
                        is SpeechRecognizerResponse.FinalTextResponse -> update(response.text, true, run)
                        is SpeechRecognizerResponse.ErrorResponse -> fail(response.toString())
                        is SpeechRecognizerResponse.CompletedResponse -> { active = false; revision++ }
                    }
                }
            } catch (t: Throwable) {
                if (run == generation.get()) fail(t.message ?: t.javaClass.simpleName)
            } finally {
                if (run == generation.get()) { active = false; revision++ }
                try { pipe[0].close() } catch (_: Exception) { }
            }
        }
    }

    private fun prepareTranslator(run: Long) {
        translator?.close(); translator = null; translationReady = localeTag.startsWith("en", true)
        if (translationReady) return
        val source = TranslateLanguage.fromLanguageTag(Locale.forLanguageTag(localeTag).language)
        if (source == null) { error = "No on-device translation model for $localeTag"; revision++; return }
        translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.ENGLISH).build())
        translator!!.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { if (run == generation.get()) { translationReady = true; revision++; if (sourceText.isNotBlank()) translate(sourceText, finalText, run) } }
            .addOnFailureListener { if (run == generation.get()) fail("Translation model: ${it.message}") }
    }

    private fun update(text: String, isFinal: Boolean, run: Long) {
        val clean = text.trim(); if (clean.isEmpty()) return
        sourceText = clean
        if (localeTag.startsWith("en", true)) { englishText = clean; finalText = isFinal; revision++ }
        else if (translationReady) translate(clean, isFinal, run)
    }

    private fun translate(text: String, isFinal: Boolean, run: Long) {
        val requested = translationSequence.incrementAndGet()
        translator?.translate(text)?.addOnSuccessListener {
            if (run == generation.get() && requested == translationSequence.get() && sourceText == text) { englishText = it.trim(); finalText = isFinal; revision++ }
        }?.addOnFailureListener { if (run == generation.get()) fail("Local translation: ${it.message}") }
    }

    @Synchronized fun write(bytes: ByteArray) {
        if (!ready || bytes.isEmpty()) return
        if (!active) beginRecognitionLocked(generation.get())
        try { writer?.write(bytes); writer?.flush() } catch (e: Exception) { fail("Audio pipe: ${e.message}") }
    }

    @Synchronized fun finishInput() {
        try { writer?.close() } catch (_: Exception) { }
        writer = null
    }

    @Synchronized fun stop() { generation.incrementAndGet(); stopLocked(); revision++ }
    private fun stopLocked() {
        ready = false; active = false
        try { writer?.close() } catch (_: Exception) { }; writer = null
        prepareJob?.cancel(); prepareJob = null
        recognitionJob?.cancel(); recognitionJob = null
        val old = recognizer; recognizer = null
        scope.launch { try { old?.stopRecognition() } catch (_: Throwable) { }; try { old?.close() } catch (_: Throwable) { } }
        translator?.close(); translator = null; translationReady = false
    }

    private fun fail(message: String) { error = message; active = false; ready = false; revision++ }
    @Synchronized fun snapshot(): JSONObject = JSONObject()
        .put("engine", "pixel-mlkit-on-device")
        .put("locale", localeTag).put("ready", ready).put("active", active)
        .put("translationReady", translationReady).put("source", sourceText)
        .put("english", englishText).put("final", finalText).put("revision", revision)
        .put("error", error)

    fun close() { stop(); scope.cancel() }
}
