import test from "node:test";
import assert from "node:assert/strict";
import { ConversationCopilot, lastWords, mockSuggestionEngine } from "../src/copilot.js";

test("rolling transcript is capped to configured N words", () => {
  const text = Array.from({length: 25}, (_, i) => `w${i + 1}`).join(" ");
  assert.equal(lastWords(text).split(" ").length, 20);
  assert.equal(lastWords(text).startsWith("w6 "), true);
});

test("foreign off-script English becomes a phonetic branch", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "unknown", text: "Cześć, miło cię poznać" }, mockSuggestionEngine);
  c.ingest({ speaker: "user", language: "English", text: "I would love to visit Warsaw" }, mockSuggestionEngine);
  assert.doesNotMatch(c.displayLines().join("\n"), /YOU SAID/);
  assert.match(c.displayLines().join("\n"), /mock phonetic translation/);
});

test("ring scroll selects and press requests all-branch refresh", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "English", text: "I changed careers last year" }, mockSuggestionEngine);
  c.ring("swipe_down"); c.ring("swipe_down"); c.ring("press");
  assert.equal(c.state.refreshRequested,true); assert.equal(c.state.viewport,1);
  assert.doesNotMatch(c.displayLines().join("\n"), /\b(?:EN|RU|PL)\b|\b[PID]\s*[1-7]\b/);
  c.ring("double_press"); assert.equal(c.state.listening, false);
});

test("foreign output defaults phonetic and can toggle native characters",()=>{
  const c=new ConversationCopilot();c.start();c.ingest({speaker:"other",language:"unknown",text:"Привет"},mockSuggestionEngine);
  assert.match(c.displayLines().join("\n"),/mock: tell me more/);c.setNativeCharacters(true);assert.match(c.displayLines().join("\n"),/native one/);
});

test("foreign branches display English above phonetics", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "unknown", text: "Привет, как дела сегодня?" }, mockSuggestionEngine);
  assert.equal(c.state.detectedLanguage, "RU");
  const display = c.displayLines().join("\n");
  assert.match(display, /^Hello/m); assert.match(display, /Tell me more\.\n  mock: tell me more/);
  assert.doesNotMatch(display, /THEY SAID|^RU |\[P\]| I 4/m); assert.match(c.displayLines()[1], /^> /);
});

test("refresh preserves selected branch and immediate neighbors", () => {
  const c=new ConversationCopilot(); c.start(); let version=0;
  const engine=req=>({detectedLanguage:"EN",englishContext:req.latestText,replies:Array.from({length:8},(_,i)=>({english:`v${version}-${i}`}))});
  c.ingest({speaker:"other",language:"English",text:"first"},engine);c.ring("swipe_down");c.ring("swipe_down");c.ring("swipe_down");version=1;
  c.ingest({speaker:"other",language:"English",text:"second"},engine);
  assert.deepEqual(c.state.card.replies.map(x=>x.english),["v1-0","v1-1","v0-2","v0-3","v0-4","v1-5","v1-6","v1-7"]);
});
