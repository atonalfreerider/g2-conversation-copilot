import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'

const read=path=>readFile(new URL(`../${path}`,import.meta.url),'utf8')

test('G2 microphone uses only the Pixel local speech bridge',async()=>{
  const client=await read('evenhub/main.ts')
  assert.match(client,/\/speech\/start/)
  assert.match(client,/\/speech\/chunk/)
  assert.match(client,/AUDIO_FRAME_BYTES=3200/)
  assert.doesNotMatch(client,/\/realtime-token/)
  assert.doesNotMatch(client,/\/transcribe/)
  assert.doesNotMatch(client,/wss:\/\/api\.openai\.com/)
})

test('Android bridge recognizes G2 PCM and translates on device',async()=>{
  const engine=await read('app/src/main/java/com/g2copilot/settings/LocalSpeechEngine.kt')
  const gradle=await read('app/build.gradle')
  const service=await read('app/src/main/java/com/g2copilot/settings/CompanionBridgeService.java')
  assert.match(engine,/AudioSource\.fromPfd/)
  assert.match(engine,/MODE_BASIC/)
  assert.match(engine,/Translation\.getClient/)
  assert.match(gradle,/genai-speech-recognition:1\.0\.0-alpha1/)
  assert.match(gradle,/translate:17\.0\.3/)
  assert.match(service,/Access-Control-Allow-Private-Network: true/)
})
