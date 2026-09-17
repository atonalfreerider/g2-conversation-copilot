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
  const client=await read('evenhub/main.ts')
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
  assert.match(service,/realtime\/translations\/client_secrets/)
  assert.match(service,/gpt-realtime-translate/)
  assert.match(service,/put\("language","en"\)/)
  assert.match(client,/session\.input_audio_buffer\.append/)
  assert.match(client,/session\.output_transcript\.delta/)
})

test('G2 page uses the complete display and packs short branches into two columns',async()=>{
  const client=await read('evenhub/main.ts')
  assert.match(client,/yPosition:0,width:576,height:72,paddingLength:0/)
  assert.match(client,/x:0,y,width:284/)
  assert.match(client,/x:292,y,width:284/)
  assert.match(client,/x:0,y,width:576/)
  assert.match(client,/slot<7/)
  assert.match(client,/rebuildPageContainer\(pageFor\(layout\)\)/)
  assert.doesNotMatch(client,/line-left|line-right|updateImageRawData/)
})

test('argumentative contrarian is available on glasses and phone',async()=>{
  const html=await read('index.html')
  const service=await read('app/src/main/java/com/g2copilot/settings/CompanionBridgeService.java')
  const activity=await read('app/src/main/java/com/g2copilot/settings/MainActivity.java')
  assert.match(html,/value="C">Argumentative \/ contrarian/)
  assert.match(service,/challenge assumptions, expose contradictions/)
  assert.match(activity,/"Argumentative \/ contrarian"/)
})

test('ring scroll accepts system events and speech can resolve a suggested branch intent',async()=>{
  const client=await read('evenhub/main.ts')
  assert.match(client,/sysType===OsEventTypeList\.SCROLL_TOP_EVENT/)
  assert.match(client,/sysType===OsEventTypeList\.SCROLL_BOTTOM_EVENT/)
  assert.match(client,/OsEventTypeList\.fromJson/)
  assert.match(client,/value\.eventType==null\?OsEventTypeList\.CLICK_EVENT/)
  assert.match(client,/renderQueued/)
  assert.match(client,/function branchIntent/)
  assert.match(client,/BRANCH INTENT matched/)
  assert.match(client,/AudioSpeakerRole\.Other/)
})

test('Greek and Ukrainian are selectable and supported by the foreign pipeline',async()=>{
  const html=await read('index.html')
  const service=await read('app/src/main/java/com/g2copilot/settings/CompanionBridgeService.java')
  assert.match(html,/value="el-GR">Greek/)
  assert.match(html,/value="uk-UA">Ukrainian/)
  assert.match(service,/"EL"\.equals\(code\)/)
  assert.match(service,/"UK"\.equals\(code\)/)
})

test('automatic branch refresh ranks four candidates and pins spoken branches',async()=>{
  const client=await read('evenhub/main.ts')
  const service=await read('app/src/main/java/com/g2copilot/settings/CompanionBridgeService.java')
  assert.match(client,/count=replaceAll\|\|!branches\.length\?8:4/)
  assert.match(client,/Relevance vote/)
  assert.match(client,/best\.pinUntil=branchCycle\+2/)
  assert.match(client,/maybeAnticipate/)
  assert.match(client,/thisGeneration===generation\|\|!replaceAll\|\|!branches\.length/)
  assert.match(service,/Predict the next one or two likely conversational turns/)
  assert.match(service,/immediate contextual fit plus usefulness on the most predictable next turn/)
})

test('phone bridge rejects HTML and empty API fallbacks before JSON parsing',async()=>{
  const client=await read('evenhub/main.ts')
  assert.match(client,/returned \$\{sample\.startsWith\('<'\)\?'HTML':'an empty\/non-JSON response'/)
  assert.match(client,/type\.toLowerCase\(\)\.includes\('application\/json'\)/)
})
