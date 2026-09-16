package com.g2copilot.settings

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Streams G2's 16 kHz mono PCM into Android's on-device speech service. */
internal class LocalSpeechEngine(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private val translationSequence = AtomicLong()
    private var recognizer: SpeechRecognizer? = null
    private var readEnd: ParcelFileDescriptor? = null
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
        val run = generation.incrementAndGet()
        closePipe()
        localeTag = tag.ifBlank { "en-US" }
        sourceText = ""; englishText = ""; finalText = false; error = ""; ready = false; active = false
        revision++
        prepareTranslator(run)
        val created = CountDownLatch(1)
        main.post {
            try {
                recognizer?.destroy(); recognizer = null
                if (run != generation.get()) return@post
                if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    fail("Pixel on-device speech recognition is unavailable")
                    return@post
                }
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                    it.setRecognitionListener(listener(run))
                }
                ready = true; revision++
            } catch (t: Throwable) {
                if (run == generation.get()) fail(t.message ?: t.javaClass.simpleName)
            } finally { created.countDown() }
        }
        created.await(2, TimeUnit.SECONDS)
        return snapshot()
    }

    private fun recognitionIntent(pipe: ParcelFileDescriptor) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }

    @Synchronized private fun beginRecognitionLocked(run: Long) {
        if (!ready || active || recognizer == null) return
        closePipe()
        val pipe = ParcelFileDescriptor.createPipe()
        readEnd = pipe[0]
        writer = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        sourceText = ""; englishText = ""; finalText = false; error = ""; active = true; revision++
        val request = recognitionIntent(pipe[0])
        main.post {
            if (run != generation.get()) return@post
            try { recognizer?.startListening(request) }
            catch (t: Throwable) { failSession(t.message ?: t.javaClass.simpleName, false) }
        }
    }

    private fun listener(run: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onPartialResults(results: Bundle?) { result(results, false, run) }
        override fun onResults(results: Bundle?) { result(results, true, run); finishSession() }
        override fun onSegmentResults(segmentResults: Bundle) { result(segmentResults, false, run) }
        override fun onEndOfSegmentedSession() { finalText = sourceText.isNotBlank(); revision++; finishSession() }
        override fun onError(code: Int) {
            if (run != generation.get()) return
            val quiet = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            failSession(if (quiet) "" else speechError(code), quiet)
        }
    }

    private fun result(bundle: Bundle?, isFinal: Boolean, run: Long) {
        if (run != generation.get()) return
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        update(text, isFinal, run)
    }

    @Synchronized private fun finishSession() {
        active = false
        closePipe()
        revision++
    }

    @Synchronized private fun failSession(message: String, quiet: Boolean) {
        active = false
        closePipe()
        error = if (quiet) "" else message
        revision++
    }

    private fun speechError(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "On-device speech audio error"
        SpeechRecognizer.ERROR_CLIENT -> "On-device speech session reset"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Speech language is not supported on this Pixel"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Download the speech model for $localeTag in Android settings"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "On-device speech recognizer busy"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "On-device speech service disconnected"
        else -> "On-device speech error $code"
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
        try { writer?.write(bytes); writer?.flush() } catch (e: Exception) { failSession("Audio pipe: ${e.message}", false) }
    }

    @Synchronized fun finishInput() { try { writer?.close() } catch (_: Exception) { }; writer = null }

    @Synchronized fun translateText(text: String): JSONObject {
        val clean = text.trim()
        if (clean.isEmpty()) return JSONObject().put("english", "")
        if (localeTag.startsWith("en", true)) return JSONObject().put("english", clean)
        val client = translator ?: throw IllegalStateException("No on-device translation model for $localeTag")
        if (!translationReady) throw IllegalStateException("Offline translation model for $localeTag is still preparing")
        val translated = Tasks.await(client.translate(clean), 4, TimeUnit.SECONDS).trim()
        return JSONObject().put("english", translated).put("source", clean).put("locale", localeTag)
    }

    @Synchronized fun stop() {
        generation.incrementAndGet(); ready = false; active = false; error = ""; closePipe(); revision++
        translator?.close(); translator = null; translationReady = false
        main.post { try { recognizer?.cancel() } catch (_: Throwable) { }; try { recognizer?.destroy() } catch (_: Throwable) { }; recognizer = null }
    }

    @Synchronized private fun closePipe() {
        try { writer?.close() } catch (_: Exception) { }; writer = null
        try { readEnd?.close() } catch (_: Exception) { }; readEnd = null
    }

    private fun fail(message: String) { error = message; active = false; ready = false; revision++ }
    @Synchronized fun snapshot(): JSONObject = JSONObject()
        .put("engine", "pixel-android-on-device")
        .put("locale", localeTag).put("ready", ready).put("active", active)
        .put("translationReady", translationReady).put("source", sourceText)
        .put("english", englishText).put("final", finalText).put("revision", revision)
        .put("error", error)

    fun close() = stop()
}
