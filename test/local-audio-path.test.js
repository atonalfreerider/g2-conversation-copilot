import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'

const read=path=>readFile(new URL(`../${path}`,import.meta.url),'utf8')

test('G2 microphone prefers realtime transcription with Pixel fallback',async()=>{
  const client=await read('evenhub/main.ts')
  assert.match(client,/\/speech\/start/)
  assert.match(client,/\/speech\/chunk/)
  assert.match(client,/AUDIO_FRAME_BYTES=3200/)
  assert.match(client,/\/realtime-token/)
  assert.match(client,/wss:\/\/api\.openai\.com\/v1\/realtime/)
  assert.match(client,/Pixel offline fallback/)
  assert.doesNotMatch(client,/\/transcribe/)
})

test('Android bridge recognizes G2 PCM and translates on device',async()=>{
  const engine=await read('app/src/main/java/com/g2copilot/settings/LocalSpeechEngine.kt')
  const gradle=await read('app/build.gradle')
  const service=await read('app/src/main/java/com/g2copilot/settings/CompanionBridgeService.java')
  assert.match(engine,/createOnDeviceSpeechRecognizer/)
  assert.match(engine,/EXTRA_AUDIO_SOURCE/)
  assert.match(engine,/FORMATTING_OPTIMIZE_LATENCY/)
  assert.match(engine,/Translation\.getClient/)
  assert.doesNotMatch(gradle,/genai-speech-recognition/)
  assert.match(gradle,/translate:17\.0\.3/)
  assert.match(service,/Access-Control-Allow-Private-Network: true/)
  assert.match(service,/\/translate/)
  assert.match(service,/gpt-live-transcribe/)
  assert.match(service,/"delay","minimal"/)
  assert.match(service,/"turn_detection",JSONObject\.NULL/)
})
