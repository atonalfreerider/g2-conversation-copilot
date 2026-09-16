import './style.css'
import {AudioInputSource,OsEventTypeList,StartUpPageCreateResult,waitForEvenAppBridge} from '@evenrealities/even_hub_sdk'

type Branch={english:string;native:string;phonetic:string}
type Turn={speaker:string;text:string}
type SpeechState={engine:string;locale:string;ready:boolean;active:boolean;translationReady:boolean;source:string;english:string;final:boolean;revision:number;error:string}

const BUILD='0.3.0-20260915',AUDIO_FRAME_BYTES=3200
if('scrollRestoration'in history)history.scrollRestoration='manual'
window.addEventListener('pageshow',()=>window.scrollTo(0,0))
const $=<T extends HTMLElement>(id:string)=>document.getElementById(id) as T
const status=$<HTMLParagraphElement>('status'),log=$<HTMLPreElement>('log'),provider=$<HTMLSelectElement>('provider'),language=$<HTMLSelectElement>('language'),style=$<HTMLSelectElement>('style'),native=$<HTMLInputElement>('native')

let bridge:any,recording=false,focus=0,viewport=0,context='Ready',branches:Branch[]=[],turns:Turn[]=[]
let renderedContext='',renderedBranches='',suggesting=false,suggestionQueued=false,forceQueued=false,generation=0
let apiBase='',speechReady=false,speechRevision=-1,lastFinalText='',lastSpeechError='',speechPoll:any=null,audioSending=false,queuedAudio:Uint8Array[]=[]
let localSpeaking=false,finishingSpeech=false,lastVoiceAt=0,preRoll:Uint8Array[]=[]

async function apiFetch(path:string,init?:RequestInit){
  const candidates=apiBase?[apiBase]:['http://127.0.0.1:8787',`${location.origin}/api`]
  let last:any
  for(const base of candidates){try{const response=await fetch(`${base}${path}`,init);apiBase=base;return response}catch(e){last=e}}
  throw new Error(`Phone bridge unreachable (${last?.message||'network blocked'}). Open G2 Copilot on the phone and tap Start EvenHub provider bridge.`)
}
function jsonPost(path:string,value:any){return apiFetch(path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(value)})}

const saved=JSON.parse(localStorage.getItem('g2copilot.settings')||'{}')
language.value=saved.language||'en-US';style.value=saved.style||'P';native.checked=!!saved.native
function save(){localStorage.setItem('g2copilot.settings',JSON.stringify({language:language.value,style:style.value,native:native.checked}))}
language.onchange=async()=>{save();generation++;branches=[];focus=viewport=0;context='Listening…';renderedContext=renderedBranches='';render();if(recording)await startLocalSpeech();requestSuggestions(true)}
style.onchange=()=>{save();requestSuggestions(true)}
native.onchange=()=>{save();renderedBranches='';render()}
provider.onchange=async()=>{try{const r=await jsonPost('/provider',{provider:provider.value}),data=await r.json();if(!r.ok)throw new Error(data.error);if(data.requiresForeground){status.textContent=data.message;write(`Nano paused · ${data.message}`)}else{status.textContent=recording?'Listening locally':'Ready';write(`Backend: ${provider.options[provider.selectedIndex].text}`);requestSuggestions(true)}}catch(e:any){status.textContent=e.message;write(`PROVIDER ERROR ${e.message}`)}}

function write(message:string){log.textContent=`${new Date().toLocaleTimeString()} ${message}\n${log.textContent}`.slice(0,7000)}
function shown(b:Branch){if(language.value.startsWith('en'))return b.english;const second=native.checked?b.native:b.phonetic;return second?`${b.english}\n${second}`:b.english}
function visibleCount(){return language.value.startsWith('en')?4:2}
function branchContent(){if(!branches.length)return 'Listening for a complete turn…';return branches.slice(viewport,viewport+visibleCount()).map((b,i)=>`${focus===viewport+i?'>':'•'} ${shown(b)}`).join('\n')}
async function render(){if(!bridge)return;const tasks:Promise<any>[]=[];if(context!==renderedContext){renderedContext=context;tasks.push(bridge.textContainerUpgrade({containerID:1,containerName:'context',content:context}))}const content=branchContent();if(content!==renderedBranches){renderedBranches=content;tasks.push(bridge.textContainerUpgrade({containerID:4,containerName:'branches',content}))}if(tasks.length)await Promise.all(tasks)}
function normalizeViewport(){const count=visibleCount();focus=Math.max(0,Math.min(branches.length-1,focus));viewport=Math.max(0,Math.min(viewport,Math.max(0,branches.length-count)));if(focus<viewport)viewport=focus;if(focus>=viewport+count)viewport=focus-count+1}
function move(delta:number){if(!branches.length)return;focus=Math.max(0,Math.min(branches.length-1,focus+delta));normalizeViewport();render()}
function bytesBase64(bytes:Uint8Array){let binary='';for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode(...bytes.subarray(i,i+0x8000));return btoa(binary)}

async function startLocalSpeech(){
  clearInterval(speechPoll);speechReady=false;speechRevision=-1;lastFinalText='';lastSpeechError='';queuedAudio=[];preRoll=[];localSpeaking=false;finishingSpeech=false
  try{const r=await jsonPost('/speech/start',{language:language.value}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);handleSpeech(data);speechPoll=setInterval(pollSpeech,100);write(`LOCAL SPEECH ${language.value} · Pixel ML Kit`)}catch(e:any){status.textContent=e.message;if(e.message!==lastSpeechError){lastSpeechError=e.message;write(`LOCAL SPEECH ERROR ${e.message}`)}setTimeout(()=>{if(recording&&!speechReady)startLocalSpeech()},1000)}
}
async function stopLocalSpeech(){clearInterval(speechPoll);speechPoll=null;speechReady=false;queuedAudio=[];try{await jsonPost('/speech/stop',{})}catch{}}
async function pollSpeech(){try{const r=await apiFetch('/speech/poll'),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);handleSpeech(data)}catch(e:any){if(e.message!==lastSpeechError){lastSpeechError=e.message;write(`LOCAL SPEECH POLL ${e.message}`)}}}
function handleSpeech(data:SpeechState){
  speechReady=!!data.ready
  if(data.error&&data.error!==lastSpeechError){lastSpeechError=data.error;write(`LOCAL SPEECH ERROR ${data.error}`)}
  if(data.revision===speechRevision)return
  speechRevision=data.revision
  if(!data.ready){status.textContent=data.error||'Preparing on-device speech model…';if(data.error)setTimeout(()=>{if(recording&&!speechReady)startLocalSpeech()},300);return}
  if(data.english?.trim()){context=data.english.trim();render();status.textContent=data.final?'Local turn complete':'Hearing locally…'}
  else status.textContent=data.translationReady?'Listening locally':'Preparing offline translation model…'
  if(data.final&&data.english?.trim()&&data.english.trim()!==lastFinalText){lastFinalText=data.english.trim();turns=[...turns,{speaker:'unknown',text:lastFinalText}].slice(-20);write(`LOCAL ${language.value.startsWith('en')?'TRANSCRIPT':'TRANSLATION'}: ${lastFinalText}`);requestSuggestions(false)}
}
function pcmEnergy(bytes:Uint8Array){let sum=0,n=0;for(let i=0;i+1<bytes.length;i+=2){const x=(bytes[i]|bytes[i+1]<<8)<<16>>16;sum+=x*x;n++}return n?Math.sqrt(sum/n):0}
function queueLocalAudio(bytes:Uint8Array){
  const copy=new Uint8Array(bytes),now=Date.now(),voice=pcmEnergy(copy)>550
  if(!speechReady||finishingSpeech){preRoll.push(copy);trimPreRoll();return}
  if(!localSpeaking){preRoll.push(copy);trimPreRoll();if(!voice)return;localSpeaking=true;lastVoiceAt=now;queuedAudio.push(...preRoll);preRoll=[]}
  else{queuedAudio.push(copy);if(voice)lastVoiceAt=now}
  if(queuedAudio.reduce((n,x)=>n+x.length,0)>=AUDIO_FRAME_BYTES)drainLocalAudio()
  if(!voice&&now-lastVoiceAt>450)finishLocalUtterance()
}
function trimPreRoll(){let total=preRoll.reduce((n,x)=>n+x.length,0);while(total>AUDIO_FRAME_BYTES&&preRoll.length){total-=preRoll.shift()!.length}}
async function finishLocalUtterance(){if(!localSpeaking||finishingSpeech)return;localSpeaking=false;finishingSpeech=true;await drainLocalAudio();while(audioSending)await new Promise(r=>setTimeout(r,10));try{const r=await jsonPost('/speech/finish',{}),data=await r.json();if(r.ok)handleSpeech(data)}catch(e:any){write(`LOCAL SPEECH FINISH ${e.message}`)}finally{finishingSpeech=false}}
async function drainLocalAudio(){if(audioSending)return;audioSending=true;try{while(queuedAudio.length){const chunks=queuedAudio.splice(0),size=chunks.reduce((n,x)=>n+x.length,0),joined=new Uint8Array(size);let at=0;for(const chunk of chunks){joined.set(chunk,at);at+=chunk.length}const r=await jsonPost('/speech/chunk',{audio:bytesBase64(joined)}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);handleSpeech(data)}}catch(e:any){speechReady=false;write(`LOCAL AUDIO ERROR ${e.message}`)}finally{audioSending=false;if(queuedAudio.reduce((n,x)=>n+x.length,0)>=AUDIO_FRAME_BYTES)drainLocalAudio()}}

function validBranches(value:any):value is Branch[]{return Array.isArray(value)&&value.length>0&&value.every((b:any)=>b&&typeof b.english==='string'&&typeof b.native==='string'&&typeof b.phonetic==='string')}
function merge(next:Branch[],force:boolean){if(!validBranches(next)){write('BRANCH ERROR provider returned no usable branch array');return}const incoming=next.slice(0,8);if(force||!branches.length)branches=incoming;else branches=incoming.map((x,i)=>Math.abs(i-focus)<=1?(branches[i]||x):x);normalizeViewport();render()}
async function requestSuggestions(force:boolean){generation++;forceQueued=forceQueued||force;if(suggesting){suggestionQueued=true;return}suggesting=true;do{suggestionQueued=false;const thisGeneration=generation,replaceAll=forceQueued;forceQueued=false;try{const r=await jsonPost('/suggest',{language:language.value,style:style.value,turns}),data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);if(!validBranches(data.branches))throw new Error('Provider response did not contain branches');if(thisGeneration===generation){merge(data.branches,replaceAll);write(`All eight branches refreshed · ${data.provider||provider.value}`)}}catch(e:any){write(`BRANCH ERROR ${e.message}`);if(e.message?.includes('foreground'))status.textContent=e.message}}while(suggestionQueued);suggesting=false}
function refreshAll(){requestSuggestions(true)}
async function toggleMic(){
  recording=!recording
  if(recording)await startLocalSpeech();else await stopLocalSpeech()
  const ok=await bridge.audioControl(recording,AudioInputSource.Glasses)
  if(!ok){recording=false;await stopLocalSpeech();throw new Error('G2 microphone could not be opened')}
  status.textContent=recording?'Preparing local speech…':'Microphone stopped';$('mic').textContent=recording?'Stop G2 microphone':'Start G2 microphone'
}
$('mic').onclick=()=>toggleMic().catch((e:any)=>{status.textContent=e.message;write(`ERROR ${e.message}`)})
$('refresh').onclick=refreshAll

async function start(){
  window.scrollTo(0,0);setTimeout(()=>window.scrollTo(0,0),100);write(`BUILD ${BUILD}`)
  bridge=await waitForEvenAppBridge()
  try{const r=await apiFetch('/config'),data=await r.json();if(r.ok&&data.provider){provider.value=data.provider;if(data.provider==='GEMINI_NANO'&&!data.nanoForeground){status.textContent='Tap phone notification to activate Nano';write('Nano selected · phone companion must stay foreground')}}write(`Bridge: ${apiBase}`)}catch(e:any){status.textContent=e.message;write(e.message)}
  const page={containerTotalNum:4,textObject:[{xPosition:0,yPosition:0,width:576,height:78,paddingLength:1,containerID:1,containerName:'context',content:context,isEventCapture:0},{xPosition:0,yPosition:79,width:576,height:209,paddingLength:1,containerID:4,containerName:'branches',content:branchContent(),isEventCapture:1}],imageObject:[{xPosition:0,yPosition:78,width:288,height:20,containerID:2,containerName:'line-left'},{xPosition:288,yPosition:78,width:288,height:20,containerID:3,containerName:'line-right'}],menuObject:{menuItems:[{itemName:'Refresh branches',itemID:1},{itemName:'Start / stop mic',itemID:2}]}}
  const created=await bridge.createStartUpPageContainer(page);if(created!==StartUpPageCreateResult.success){const rebuilt=await bridge.rebuildPageContainer(page);if(!rebuilt)throw new Error(`Glasses page failed: ${created}`)}
  const line=new Array(288*20).fill(0);for(let x=0;x<288;x++)line[x]=255
  await Promise.all([bridge.updateImageRawData({containerID:2,containerName:'line-left',imageData:line}),bridge.updateImageRawData({containerID:3,containerName:'line-right',imageData:line})])
  bridge.onEvenHubEvent((event:any)=>{
    const audio=event.audioEvent;if(audio&&recording)queueLocalAudio(audio.audioPcm as Uint8Array)
    const sysType=event.sysEvent?(event.sysEvent.eventType??OsEventTypeList.CLICK_EVENT):null,textType=event.textEvent?(event.textEvent.eventType??OsEventTypeList.CLICK_EVENT):null,listType=event.listEvent?(event.listEvent.eventType??OsEventTypeList.CLICK_EVENT):null
    if(sysType===OsEventTypeList.DOUBLE_CLICK_EVENT){stopLocalSpeech();bridge.shutDownPageContainer(0)}
    else if(textType===OsEventTypeList.SCROLL_TOP_EVENT||listType===OsEventTypeList.SCROLL_TOP_EVENT)move(-1)
    else if(textType===OsEventTypeList.SCROLL_BOTTOM_EVENT||listType===OsEventTypeList.SCROLL_BOTTOM_EVENT)move(1)
    else if(sysType===OsEventTypeList.CLICK_EVENT||textType===OsEventTypeList.CLICK_EVENT||listType===OsEventTypeList.CLICK_EVENT){write(`R1 press · source ${event.sysEvent?.eventSource??'captured'}`);refreshAll()}
    if(event.menuItemClickEvent?.itemID===1)refreshAll();if(event.menuItemClickEvent?.itemID===2)toggleMic()
  })
  await toggleMic();refreshAll();write('Even G2 bridge connected · audio remains on Pixel')
}
start().catch((e:any)=>{status.textContent=e.message;write(`STARTUP ERROR ${e.stack||e.message}`)})
