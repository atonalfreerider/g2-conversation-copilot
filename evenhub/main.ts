import './style.css'
import {AudioInputSource,AudioSpeakerRole,OsEventTypeList,StartUpPageCreateResult,waitForEvenAppBridge} from '@evenrealities/even_hub_sdk'

type Branch={english:string;native:string;phonetic:string;relevance?:number;score?:number;votes?:number;pinUntil?:number}
type Turn={speaker:string;text:string}
type SpeechState={engine:string;locale:string;ready:boolean;active:boolean;translationReady:boolean;source:string;english:string;final:boolean;revision:number;error:string}
type BranchLayout={branchIndex:number;containerID:number;x:number;y:number;width:number;height:number;content:string}

const BUILD='0.3.4-20260917',AUDIO_FRAME_BYTES=3200
const MENU={menuItems:[{itemName:'Refresh branches',itemID:1},{itemName:'Start / stop mic',itemID:2}]}
if('scrollRestoration'in history)history.scrollRestoration='manual'
window.addEventListener('pageshow',()=>window.scrollTo(0,0))
const $=<T extends HTMLElement>(id:string)=>document.getElementById(id) as T
const status=$<HTMLParagraphElement>('status'),log=$<HTMLPreElement>('log'),provider=$<HTMLSelectElement>('provider'),language=$<HTMLSelectElement>('language'),style=$<HTMLSelectElement>('style'),native=$<HTMLInputElement>('native')

let bridge:any,recording=false,focus=0,viewport=0,context='Ready',branches:Branch[]=[],turns:Turn[]=[]
let renderedContext='',renderedBranches='',renderedLayout='',suggesting=false,suggestionQueued=false,forceQueued=false,generation=0
let rendering=false,renderQueued=false
let apiBase='',speechReady=false,speechRevision=-1,lastFinalText='',lastSpeechError='',speechPoll:any=null,audioSending=false,queuedAudio:Uint8Array[]=[]
let localSpeaking=false,finishingSpeech=false,lastVoiceAt=0,voiceStartedAt=0,firstPartialLogged=false,preRoll:Uint8Array[]=[]
let realtime:WebSocket|null=null,realtimeReady=false,realtimeConnecting=false,realtimeRetry:any=null,realtimePartialTimer:any=null,realtimeTranslateSeq=0
let realtimeSpeaking=false,realtimeLastVoice=0,realtimeSpeechBytes=0,pendingRealtimeCommit=false
const realtimePartials=new Map<string,string>(),pendingRealtime:Uint8Array[]=[],realtimePreRoll:Uint8Array[]=[]
let realtimeMode:'transcription'|'translation'='transcription',selfVotes=0,otherVotes=0,lastCommittedRole=AudioSpeakerRole.Unknown
const realtimeRoles=new Map<string,AudioSpeakerRole>()
let translatedEnglish='',translatedSource='',translationFinalizeTimer:any=null,translationMatch:Branch|null=null
let branchCycle=0,lastPreviewSentence='',queuedPreview=''

async function apiFetch(path:string,init?:RequestInit){
  const defaults=['http://127.0.0.1:8787',`${location.origin}/api`],candidates=apiBase?[apiBase,...defaults.filter(x=>x!==apiBase)]:defaults
  let last:any
  for(const base of candidates){try{const response=await fetch(`${base}${path}`,init),sample=(await response.clone().text()).trim(),type=response.headers.get('content-type')||'';if(!sample||!type.toLowerCase().includes('application/json')){last=new Error(`${base}${path} returned ${sample.startsWith('<')?'HTML':'an empty/non-JSON response'} (HTTP ${response.status})`);if(apiBase)apiBase='';continue}JSON.parse(sample);apiBase=base;return response}catch(e){last=e;if(apiBase)apiBase=''}}
  throw new Error(`Phone bridge unreachable (${last?.message||'network blocked'}). Open G2 Copilot on the phone and tap Start EvenHub provider bridge.`)
}
function jsonPost(path:string,value:any){return apiFetch(path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(value)})}

const saved=JSON.parse(localStorage.getItem('g2copilot.settings')||'{}')
language.value=saved.language||'en-US';style.value=saved.style||'P';native.checked=!!saved.native
function save(){localStorage.setItem('g2copilot.settings',JSON.stringify({language:language.value,style:style.value,native:native.checked}))}
language.onchange=async()=>{save();generation++;branches=[];focus=viewport=0;context='Listening…';renderedContext=renderedBranches='';render();if(recording)await startTranscription();requestSuggestions(true)}
style.onchange=()=>{save();requestSuggestions(true)}
native.onchange=()=>{save();renderedBranches='';render()}
provider.onchange=async()=>{try{const r=await jsonPost('/provider',{provider:provider.value}),data=await r.json();if(!r.ok)throw new Error(data.error);if(data.requiresForeground){status.textContent=data.message;write(`Nano paused · ${data.message}`)}else{status.textContent=recording?'Listening locally':'Ready';write(`Backend: ${provider.options[provider.selectedIndex].text}`);requestSuggestions(true)}}catch(e:any){status.textContent=e.message;write(`PROVIDER ERROR ${e.message}`)}}

function write(message:string){log.textContent=`${new Date().toLocaleTimeString()} ${message}\n${log.textContent}`.slice(0,7000)}
function shown(b:Branch){if(language.value.startsWith('en'))return b.english;const second=native.checked?b.native:b.phonetic;return second?`${b.english}\n${second}`:b.english}
function estimatedLines(text:string,width:number){const capacity=width<576?20:42;return text.split('\n').reduce((sum,line)=>sum+Math.max(1,Math.ceil(Array.from(line).length/capacity)),0)}
function pairable(b:Branch){const lines=shown(b).split('\n');return lines.length<=2&&lines.every(line=>Array.from(line).length<=20)}
function branchLayout(start=viewport){
  const out:BranchLayout[]=[]
  if(!branches.length)return [{branchIndex:-1,containerID:10,x:0,y:72,width:576,height:216,content:'Listening for a complete turn…'}]
  let y=72,index=Math.max(0,start),slot=0
  while(index<branches.length&&slot<7&&y<288){
    const left=branches[index],right=branches[index+1]
    if(right&&slot<=5&&pairable(left)&&pairable(right)){
      const height=27*Math.max(estimatedLines(shown(left),284),estimatedLines(shown(right),284))
      if(y+height>288)break
      out.push({branchIndex:index,containerID:10+slot++,x:0,y,width:284,height,content:`${focus===index?'>':'•'} ${shown(left)}`})
      out.push({branchIndex:index+1,containerID:10+slot++,x:292,y,width:284,height,content:`${focus===index+1?'>':'•'} ${shown(right)}`})
      y+=height;index+=2;continue
    }
    const height=27*Math.min(4,estimatedLines(shown(left),576))
    if(y+height>288)break
    out.push({branchIndex:index,containerID:10+slot++,x:0,y,width:576,height,content:`${focus===index?'>':'•'} ${shown(left)}`})
    y+=height;index++
  }
  return out.length?out:[{branchIndex:index,containerID:10,x:0,y:72,width:576,height:216,content:`> ${shown(branches[index])}`}]
}
function pageFor(layout:BranchLayout[]){return {containerTotalNum:1+layout.length,textObject:[{xPosition:0,yPosition:0,width:576,height:72,paddingLength:0,containerID:1,containerName:'context',content:context,isEventCapture:0},...layout.map((item,i)=>({xPosition:item.x,yPosition:item.y,width:item.width,height:item.height,paddingLength:0,containerID:item.containerID,containerName:`branch-${i}`,content:item.content,isEventCapture:i===0?1:0}))],menuObject:MENU}}
function geometry(layout:BranchLayout[]){return layout.map(x=>`${x.x},${x.y},${x.width},${x.height}`).join('|')}
function branchRenderKey(layout:BranchLayout[]){return layout.map(x=>`${x.branchIndex}:${x.content}`).join('|')}
async function render(){if(!bridge)return;if(rendering){renderQueued=true;return}rendering=true;do{
  renderQueued=false
  const nextContext=context,layout=branchLayout(),nextLayout=geometry(layout),nextBranches=branchRenderKey(layout)
  if(nextLayout!==renderedLayout){await bridge.rebuildPageContainer(pageFor(layout));renderedLayout=nextLayout;renderedContext=nextContext;renderedBranches=nextBranches;continue}
  const tasks:Promise<any>[]=[]
  if(nextContext!==renderedContext){renderedContext=nextContext;tasks.push(bridge.textContainerUpgrade({containerID:1,containerName:'context',content:nextContext}))}
  if(nextBranches!==renderedBranches){renderedBranches=nextBranches;for(let i=0;i<layout.length;i++)tasks.push(bridge.textContainerUpgrade({containerID:layout[i].containerID,containerName:`branch-${i}`,content:layout[i].content}))}
  if(tasks.length)await Promise.all(tasks)
}while(renderQueued||context!==renderedContext||branchRenderKey(branchLayout())!==renderedBranches);rendering=false}
function normalizeViewport(){
  if(!branches.length){focus=viewport=0;return}
  focus=Math.max(0,Math.min(branches.length-1,focus));viewport=Math.max(0,Math.min(viewport,branches.length-1));if(focus<viewport)viewport=focus
  let guard=branches.length
  while(!branchLayout(viewport).some(item=>item.branchIndex===focus)&&viewport<focus&&guard-->0)viewport++
}
function move(delta:number){if(!branches.length)return;focus=Math.max(0,Math.min(branches.length-1,focus+delta));const selected=branches[focus];selected.votes=(selected.votes||0)+1;selected.score=(selected.score||50)+2;normalizeViewport();render()}
function normalizedWords(value:string){return value.normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('en-US').replace(/[^\p{L}\p{N}]+/gu,' ').trim()}
function editSimilarity(a:string,b:string){const x=normalizedWords(a),y=normalizedWords(b);if(!x||!y)return 0;if(x===y)return 1;const row=Array.from({length:y.length+1},(_,i)=>i);for(let i=1;i<=x.length;i++){let previous=row[0];row[0]=i;for(let j=1;j<=y.length;j++){const old=row[j];row[j]=Math.min(row[j]+1,row[j-1]+1,previous+(x[i-1]===y[j-1]?0:1));previous=old}}return 1-row[y.length]/Math.max(x.length,y.length)}
function branchIntent(spoken:string,role:AudioSpeakerRole){if(role===AudioSpeakerRole.Other||normalizedWords(spoken).length<4)return null;let best:Branch|null=null,score=0;for(let i=0;i<branches.length;i++){const b=branches[i],candidates=language.value.startsWith('en')?[b.english]:[b.native,b.phonetic,b.english];const candidate=Math.max(...candidates.map(x=>editSimilarity(spoken,x)))+(i===focus ? .08 : 0);if(candidate>score){score=candidate;best=b}}if(score<.46||!best)return null;best.pinUntil=branchCycle+2;best.votes=(best.votes||0)+10;best.score=Math.max(200,best.score||0);return best}
function maybeAnticipate(text:string){const clean=text.trim();if(!/[.!?]["')\]]?$/.test(clean)||normalizedWords(clean)===normalizedWords(lastPreviewSentence))return;lastPreviewSentence=clean;requestSuggestions(false,clean)}
function noteRole(role:AudioSpeakerRole,voice:boolean){if(!voice)return;if(role===AudioSpeakerRole.Self)selfVotes++;else if(role===AudioSpeakerRole.Other)otherVotes++}
function dominantRole(){return selfVotes>otherVotes?AudioSpeakerRole.Self:otherVotes>selfVotes?AudioSpeakerRole.Other:AudioSpeakerRole.Unknown}
function bytesBase64(bytes:Uint8Array){let binary='';for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode(...bytes.subarray(i,i+0x8000));return btoa(binary)}
function resample16to24(bytes:Uint8Array){const input=new Int16Array(bytes.buffer,bytes.byteOffset,Math.floor(bytes.byteLength/2)),length=Math.max(1,Math.floor(input.length*1.5)),out=new Int16Array(length);for(let i=0;i<length;i++){const at=i/1.5,left=Math.floor(at),right=Math.min(input.length-1,left+1),mix=at-left;out[i]=Math.max(-32768,Math.min(32767,Math.round(input[left]*(1-mix)+input[right]*mix)))}return new Uint8Array(out.buffer)}
function audioAppendEvent(audio:Uint8Array){return {type:realtimeMode==='translation'?'session.input_audio_buffer.append':'input_audio_buffer.append',audio:bytesBase64(audio)}}
function sendRealtime(bytes:Uint8Array){const audio=resample16to24(bytes);if(realtimeReady&&realtime?.readyState===WebSocket.OPEN)realtime.send(JSON.stringify(audioAppendEvent(audio)));else{pendingRealtime.push(audio);while(pendingRealtime.reduce((n,x)=>n+x.length,0)>192000)pendingRealtime.shift()}}
function commitRealtime(){lastCommittedRole=dominantRole();if(realtimeReady&&realtime?.readyState===WebSocket.OPEN)realtime.send(JSON.stringify({type:'input_audio_buffer.commit'}));else pendingRealtimeCommit=true;realtimeSpeaking=false;realtimeSpeechBytes=0;realtimePreRoll.length=0;selfVotes=otherVotes=0}
function queueRealtimeAudio(bytes:Uint8Array,role:AudioSpeakerRole){const copy=new Uint8Array(bytes),now=Date.now(),voice=pcmEnergy(copy)>550;noteRole(role,voice);if(realtimeMode==='translation'){sendRealtime(copy);if(voice){realtimeSpeaking=true;realtimeLastVoice=now;if(!voiceStartedAt){voiceStartedAt=now;firstPartialLogged=false}}return}if(!realtimeSpeaking){realtimePreRoll.push(copy);let total=realtimePreRoll.reduce((n,x)=>n+x.length,0);while(total>9600&&realtimePreRoll.length){total-=realtimePreRoll.shift()!.length}if(!voice)return;realtimeSpeaking=true;realtimeLastVoice=now;realtimeSpeechBytes=total;voiceStartedAt=now;firstPartialLogged=false;for(const chunk of realtimePreRoll)sendRealtime(chunk);realtimePreRoll.length=0;return}sendRealtime(copy);realtimeSpeechBytes+=copy.length;if(voice)realtimeLastVoice=now;if((!voice&&now-realtimeLastVoice>250)||realtimeSpeechBytes>384000)commitRealtime()}
async function translatePartial(text:string,commit=false){const seq=++realtimeTranslateSeq;try{const r=await jsonPost('/translate',{text}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);if(seq!==realtimeTranslateSeq)return '';const english=String(data.english||'').trim();if(english){context=english;render()}return english}catch(e:any){if(commit)write(`LOCAL TRANSLATION ERROR ${e.message}`);return ''}}
function showRealtimePartial(id:string){clearTimeout(realtimePartialTimer);realtimePartialTimer=setTimeout(async()=>{const text=realtimePartials.get(id)?.trim();if(!text)return;if(!firstPartialLogged&&voiceStartedAt){firstPartialLogged=true;write(`REALTIME FIRST TEXT ${Date.now()-voiceStartedAt}ms`)}if(language.value.startsWith('en')){context=text;render();maybeAnticipate(text)}else await translatePartial(text)},60)}
async function acceptRealtimeTranscript(source:string,role=lastCommittedRole){const clean=source.trim();if(!clean)return;const intended=branchIntent(clean,role),english=intended?.english||(language.value.startsWith('en')?clean:await translatePartial(clean,true));if(!english||english===lastFinalText)return;if(intended)write(`BRANCH INTENT matched: ${intended.english}`);lastFinalText=english;context=english;render();turns=[...turns,{speaker:role===AudioSpeakerRole.Self?'self':role===AudioSpeakerRole.Other?'other':'unknown',text:english}].slice(-20);write(`REALTIME ${language.value.startsWith('en')?'TRANSCRIPT':'TRANSLATION'}: ${english}`);requestSuggestions(false)}
function scheduleTranslationFinal(){clearTimeout(translationFinalizeTimer);translationFinalizeTimer=setTimeout(()=>{const english=(translationMatch?.english||translatedEnglish).trim(),role=dominantRole();if(english&&english!==lastFinalText){lastFinalText=english;context=english;render();turns=[...turns,{speaker:role===AudioSpeakerRole.Self?'self':role===AudioSpeakerRole.Other?'other':'unknown',text:english}].slice(-20);if(translationMatch)write(`BRANCH INTENT matched: ${translationMatch.english}`);write(`REALTIME TRANSLATION: ${english}`);requestSuggestions(false)}translatedEnglish='';translatedSource='';translationMatch=null;selfVotes=otherVotes=0;voiceStartedAt=0},650)}
function onRealtimeMessage(event:MessageEvent){let data:any;try{data=JSON.parse(String(event.data))}catch{return}if(data.type==='session.created'){realtimeReady=true;realtimeConnecting=false;status.textContent=recording?(realtimeMode==='translation'?'Listening · live English translation':'Listening · live captions'):'Ready';while(pendingRealtime.length)realtime?.send(JSON.stringify(audioAppendEvent(pendingRealtime.shift()!)));if(pendingRealtimeCommit&&realtimeMode==='transcription'){pendingRealtimeCommit=false;realtime?.send(JSON.stringify({type:'input_audio_buffer.commit'}))}write(realtimeMode==='translation'?'LIVE TRANSLATION connected · English target locked':'LIVE CAPTIONS connected · gpt-live-transcribe · minimal delay');return}if(data.type==='session.output_transcript.delta'){translatedEnglish+=String(data.delta||'');if(!firstPartialLogged&&voiceStartedAt){firstPartialLogged=true;write(`REALTIME FIRST ENGLISH ${Date.now()-voiceStartedAt}ms`)}context=(translationMatch?.english||translatedEnglish).trim()||context;render();maybeAnticipate(context);scheduleTranslationFinal();return}if(data.type==='session.input_transcript.delta'){translatedSource+=String(data.delta||'');translationMatch=branchIntent(translatedSource,dominantRole())||translationMatch;if(translationMatch){context=translationMatch.english;render();scheduleTranslationFinal()}return}if(data.type==='conversation.item.input_audio_transcription.delta'){const id=String(data.item_id||'current');if(!realtimeRoles.has(id))realtimeRoles.set(id,dominantRole());realtimePartials.set(id,(realtimePartials.get(id)||'')+String(data.delta||''));showRealtimePartial(id);return}if(data.type==='conversation.item.input_audio_transcription.completed'){const id=String(data.item_id||'current'),text=String(data.transcript||realtimePartials.get(id)||''),role=realtimeRoles.get(id)||lastCommittedRole;realtimePartials.delete(id);realtimeRoles.delete(id);acceptRealtimeTranscript(text,role);return}if(data.type==='conversation.item.input_audio_transcription.failed'||data.type==='error')write(`LIVE CAPTION ERROR ${data.error?.message||'transcription failed'}`)}
async function configureRealtime(){clearTimeout(realtimeRetry);clearTimeout(translationFinalizeTimer);realtimeRetry=null;if(realtime){realtime.onclose=null;realtime.close();realtime=null}realtimeReady=false;realtimeConnecting=false;pendingRealtime.length=0;realtimePartials.clear();realtimeRoles.clear();translatedEnglish=translatedSource='';translationMatch=null;selfVotes=otherVotes=0;realtimeSpeaking=false;realtimeSpeechBytes=0;realtimePreRoll.length=0;pendingRealtimeCommit=false;realtimeConnecting=true;try{const r=await jsonPost('/realtime-token',{language:language.value}),data=await r.json();if(!r.ok||!data.value)throw new Error(data.error||`HTTP ${r.status}`);realtimeMode=data.mode==='translation'?'translation':'transcription';const url=realtimeMode==='translation'?'wss://api.openai.com/v1/realtime/translations?model=gpt-realtime-translate':'wss://api.openai.com/v1/realtime?intent=transcription',ws=new WebSocket(url,['realtime',`openai-insecure-api-key.${data.value}`]);realtime=ws;ws.onmessage=onRealtimeMessage;ws.onerror=()=>write('Fast speech socket failed · Pixel offline fallback active');ws.onclose=()=>{const wasReady=realtimeReady;realtimeReady=false;realtimeConnecting=false;realtime=null;if(wasReady)write('Fast speech disconnected · Pixel fallback active');if(recording)realtimeRetry=setTimeout(configureRealtime,2500)}}catch(e:any){realtimeConnecting=false;write(`Fast speech unavailable · Pixel offline fallback: ${e.message}`);if(recording)realtimeRetry=setTimeout(configureRealtime,5000)}}
async function startTranscription(){await startLocalSpeech();await configureRealtime()}
async function stopTranscription(){clearTimeout(realtimeRetry);realtimeRetry=null;if(realtime){realtime.onclose=null;realtime.close();realtime=null}realtimeReady=realtimeConnecting=false;pendingRealtime.length=0;await stopLocalSpeech()}

async function startLocalSpeech(){
  clearInterval(speechPoll);speechReady=false;speechRevision=-1;lastFinalText='';lastSpeechError='';queuedAudio=[];preRoll=[];localSpeaking=false;finishingSpeech=false
  try{const r=await jsonPost('/speech/start',{language:language.value}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);handleSpeech(data);speechPoll=setInterval(pollSpeech,100);write(`LOCAL SPEECH ${language.value} · Android on-device`)}catch(e:any){status.textContent=e.message;if(e.message!==lastSpeechError){lastSpeechError=e.message;write(`LOCAL SPEECH ERROR ${e.message}`)}setTimeout(()=>{if(recording&&!speechReady)startLocalSpeech()},1000)}
}
async function stopLocalSpeech(){clearInterval(speechPoll);speechPoll=null;speechReady=false;queuedAudio=[];try{await jsonPost('/speech/stop',{})}catch{}}
async function pollSpeech(){try{const r=await apiFetch('/speech/poll'),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);handleSpeech(data)}catch(e:any){if(e.message!==lastSpeechError){lastSpeechError=e.message;write(`LOCAL SPEECH POLL ${e.message}`)}}}
function handleSpeech(data:SpeechState){
  speechReady=!!data.ready
  if(data.error&&data.error!==lastSpeechError){lastSpeechError=data.error;write(`LOCAL SPEECH ERROR ${data.error}`)}
  if(data.revision===speechRevision)return
  speechRevision=data.revision
  if(!data.ready){status.textContent=data.error||'Preparing on-device speech model…';if(data.error)setTimeout(()=>{if(recording&&!speechReady)startLocalSpeech()},300);return}
  const intended=branchIntent(data.source||data.english||'',AudioSpeakerRole.Unknown),displayEnglish=intended?.english||data.english?.trim()||''
  if(displayEnglish){if(!firstPartialLogged&&voiceStartedAt){firstPartialLogged=true;write(`LOCAL FIRST TEXT ${Date.now()-voiceStartedAt}ms`)}context=displayEnglish;render();if(!data.final)maybeAnticipate(context);status.textContent=data.final?'Local turn complete':'Hearing locally…'}
  else status.textContent=data.translationReady?'Listening locally':'Preparing offline translation model…'
  if(data.final&&displayEnglish&&displayEnglish!==lastFinalText){lastFinalText=displayEnglish;turns=[...turns,{speaker:'unknown',text:lastFinalText}].slice(-20);if(intended)write(`BRANCH INTENT matched: ${intended.english}`);write(`LOCAL ${language.value.startsWith('en')?'TRANSCRIPT':'TRANSLATION'}: ${lastFinalText}`);requestSuggestions(false)}
}
function pcmEnergy(bytes:Uint8Array){let sum=0,n=0;for(let i=0;i+1<bytes.length;i+=2){const x=(bytes[i]|bytes[i+1]<<8)<<16>>16;sum+=x*x;n++}return n?Math.sqrt(sum/n):0}
function queueLocalAudio(bytes:Uint8Array){
  const copy=new Uint8Array(bytes),now=Date.now(),voice=pcmEnergy(copy)>550
  if(!speechReady||finishingSpeech){preRoll.push(copy);trimPreRoll();return}
  if(!localSpeaking){preRoll.push(copy);trimPreRoll();if(!voice)return;localSpeaking=true;lastVoiceAt=now;voiceStartedAt=now;firstPartialLogged=false;queuedAudio.push(...preRoll);preRoll=[]}
  else{queuedAudio.push(copy);if(voice)lastVoiceAt=now}
  if(queuedAudio.reduce((n,x)=>n+x.length,0)>=AUDIO_FRAME_BYTES)drainLocalAudio()
  if(!voice&&now-lastVoiceAt>450)finishLocalUtterance()
}
function queueInputAudio(bytes:Uint8Array,role:AudioSpeakerRole){if(realtimeReady||realtimeConnecting||realtime)queueRealtimeAudio(bytes,role);else queueLocalAudio(bytes)}
function trimPreRoll(){let total=preRoll.reduce((n,x)=>n+x.length,0);while(total>AUDIO_FRAME_BYTES&&preRoll.length){total-=preRoll.shift()!.length}}
async function finishLocalUtterance(){if(!localSpeaking||finishingSpeech)return;localSpeaking=false;finishingSpeech=true;await drainLocalAudio();while(audioSending)await new Promise(r=>setTimeout(r,10));try{const r=await jsonPost('/speech/finish',{}),data=await r.json();if(r.ok)handleSpeech(data)}catch(e:any){write(`LOCAL SPEECH FINISH ${e.message}`)}finally{finishingSpeech=false}}
async function drainLocalAudio(){if(audioSending)return;audioSending=true;try{while(queuedAudio.length){const chunks=queuedAudio.splice(0),size=chunks.reduce((n,x)=>n+x.length,0),joined=new Uint8Array(size);let at=0;for(const chunk of chunks){joined.set(chunk,at);at+=chunk.length}const r=await jsonPost('/speech/chunk',{audio:bytesBase64(joined)}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);handleSpeech(data)}}catch(e:any){speechReady=false;write(`LOCAL AUDIO ERROR ${e.message}`)}finally{audioSending=false;if(queuedAudio.reduce((n,x)=>n+x.length,0)>=AUDIO_FRAME_BYTES)drainLocalAudio()}}

function validBranches(value:any):value is Branch[]{return Array.isArray(value)&&value.length>0&&value.every((b:any)=>b&&typeof b.english==='string'&&typeof b.native==='string'&&typeof b.phonetic==='string')}
function prepareBranch(b:Branch,index:number){const relevance=Math.max(0,Math.min(100,Number(b.relevance??(64-index))));return {...b,relevance,score:relevance,votes:Number(b.votes||0),pinUntil:Number(b.pinUntil||0)}}
function sameBranch(a:Branch,b:Branch){return Math.max(editSimilarity(a.english,b.english),editSimilarity(a.native,b.native))>.84}
function merge(next:Branch[],force:boolean){if(!validBranches(next)){write('BRANCH ERROR provider returned no usable branch array');return}branchCycle++;const incoming=next.slice(0,8).map(prepareBranch);if(force||!branches.length){const pinned=branches.filter(b=>(b.pinUntil||0)>=branchCycle&&!incoming.some(x=>sameBranch(x,b)));branches=incoming.slice(0,8);for(const kept of pinned){if(branches.length<8)branches.push(kept);else{let loser=0;for(let i=1;i<branches.length;i++)if((branches[i].score||0)<(branches[loser].score||0))loser=i;branches[loser]=kept}}normalizeViewport();render();return}for(const b of branches){b.votes=(b.votes||0)*.75;if((b.pinUntil||0)<branchCycle)b.score=(b.score||b.relevance||50)*.68+(b.votes||0)*2}let replaced=0;for(const candidate of incoming.sort((a,b)=>(b.score||0)-(a.score||0))){const duplicate=branches.find(x=>sameBranch(x,candidate));if(duplicate){duplicate.score=Math.max(duplicate.score||0,candidate.score||0);duplicate.relevance=Math.max(duplicate.relevance||0,candidate.relevance||0);continue}if(branches.length<8){branches.push(candidate);replaced++;continue}let loser=-1,loserScore=Infinity;for(let i=0;i<branches.length;i++){const b=branches[i];if((b.pinUntil||0)>=branchCycle)continue;const score=b.score||0;if(score<loserScore){loser=i;loserScore=score}}if(loser>=0&&(candidate.score||0)>loserScore){branches[loser]=candidate;replaced++}}normalizeViewport();render();write(`Relevance vote · ${replaced} branch${replaced===1?'':'es'} replaced · ${branches.filter(b=>(b.pinUntil||0)>=branchCycle).length} pinned`)}
async function requestSuggestions(force:boolean,preview=''){generation++;queuedPreview=preview;forceQueued=forceQueued||force;if(suggesting){suggestionQueued=true;return}suggesting=true;do{suggestionQueued=false;const thisGeneration=generation,replaceAll=forceQueued,livePreview=queuedPreview;forceQueued=false;queuedPreview='';const count=replaceAll||!branches.length?8:4;try{const r=await jsonPost('/suggest',{language:language.value,style:style.value,turns,preview:livePreview,count,activeBranches:branches.map(b=>({english:b.english,native:b.native,phonetic:b.phonetic,relevance:b.relevance||0}))}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);if(!validBranches(data.branches))throw new Error('Provider response did not contain branches');if(thisGeneration===generation||!replaceAll||!branches.length){merge(data.branches,replaceAll&&thisGeneration===generation);write(`${count===8?'Full queue':'Anticipatory candidates'} received · ${data.provider||provider.value}${thisGeneration===generation?'':' · newer context queued'}`)}}catch(e:any){write(`BRANCH ERROR ${e.message}`);if(e.message?.includes('foreground'))status.textContent=e.message}}while(suggestionQueued);suggesting=false}
function refreshAll(){requestSuggestions(true)}
async function toggleMic(){
  recording=!recording
  if(recording)await startTranscription();else await stopTranscription()
  const ok=await bridge.audioControl(recording,AudioInputSource.Glasses)
  if(!ok){recording=false;await stopTranscription();throw new Error('G2 microphone could not be opened')}
  status.textContent=recording?(realtimeReady?'Listening · live captions':'Listening · Pixel offline fallback'):'Microphone stopped';$('mic').textContent=recording?'Stop G2 microphone':'Start G2 microphone'
}
$('mic').onclick=()=>toggleMic().catch((e:any)=>{status.textContent=e.message;write(`ERROR ${e.message}`)})
$('refresh').onclick=refreshAll

async function start(){
  window.scrollTo(0,0);setTimeout(()=>window.scrollTo(0,0),100);write(`BUILD ${BUILD}`)
  bridge=await waitForEvenAppBridge()
  try{const r=await apiFetch('/config'),data=await r.json();if(r.ok&&data.provider){provider.value=data.provider;if(data.provider==='GEMINI_NANO'&&!data.nanoForeground){status.textContent='Tap phone notification to activate Nano';write('Nano selected · phone companion must stay foreground')}}write(`Bridge: ${apiBase}`)}catch(e:any){status.textContent=e.message;write(e.message)}
  const initialLayout=branchLayout(),page=pageFor(initialLayout)
  const created=await bridge.createStartUpPageContainer(page);if(created!==StartUpPageCreateResult.success){const rebuilt=await bridge.rebuildPageContainer(page);if(!rebuilt)throw new Error(`Glasses page failed: ${created}`)}
  renderedContext=context;renderedLayout=geometry(initialLayout);renderedBranches=branchRenderKey(initialLayout)
  bridge.onEvenHubEvent((event:any)=>{
    const audio=event.audioEvent;if(audio&&recording)queueInputAudio(audio.audioPcm as Uint8Array,audio.speakerRole||AudioSpeakerRole.Unknown)
    const eventType=(value:any)=>value?(OsEventTypeList.fromJson(value.eventType)??(value.eventType==null?OsEventTypeList.CLICK_EVENT:undefined)):undefined
    const sysType=eventType(event.sysEvent),textType=eventType(event.textEvent),listType=eventType(event.listEvent)
    if(sysType===OsEventTypeList.DOUBLE_CLICK_EVENT){stopTranscription();bridge.shutDownPageContainer(0)}
    else if(sysType===OsEventTypeList.SCROLL_TOP_EVENT||textType===OsEventTypeList.SCROLL_TOP_EVENT||listType===OsEventTypeList.SCROLL_TOP_EVENT)move(-1)
    else if(sysType===OsEventTypeList.SCROLL_BOTTOM_EVENT||textType===OsEventTypeList.SCROLL_BOTTOM_EVENT||listType===OsEventTypeList.SCROLL_BOTTOM_EVENT)move(1)
    else if(sysType===OsEventTypeList.CLICK_EVENT||textType===OsEventTypeList.CLICK_EVENT||listType===OsEventTypeList.CLICK_EVENT){write(`R1 press · source ${event.sysEvent?.eventSource??'captured'}`);refreshAll()}
    if(event.menuItemClickEvent?.itemID===1)refreshAll();if(event.menuItemClickEvent?.itemID===2)toggleMic()
  })
  await toggleMic();refreshAll();write('Even G2 bridge connected · audio remains on Pixel')
}
start().catch((e:any)=>{status.textContent=e.message;write(`STARTUP ERROR ${e.stack||e.message}`)})
