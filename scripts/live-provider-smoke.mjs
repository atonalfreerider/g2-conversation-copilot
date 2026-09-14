import fs from "node:fs";
import assert from "node:assert/strict";
import { generateSuggestion } from "../backend/provider-adapters.js";

const provider=process.argv[2];
if(!["openai","xai"].includes(provider))throw new Error("Usage: node scripts/live-provider-smoke.mjs openai|xai [key-file]");
const keyFile=process.argv[3]??(provider==="openai"?"/home/john/Documents/oai-key":"/home/john/Documents/xAI-Key");
const apiKey=fs.readFileSync(keyFile,"utf8").trim();
const config=provider==="openai"
  ?{baseUrl:"https://api.openai.com/v1",model:"gpt-5.2",apiKey}
  :{baseUrl:"https://api.x.ai/v1",model:"grok-4.20-0309-non-reasoning",apiKey};
const request={mode:"playful",tone:{stance:"inquisitive",risk:7},biography:"I am an adventurous photographer who likes direct humor.",contextWordCount:20,rollingText:"She said she moved to Seattle last week and loves unusual coffee shops.",latestText:"I love unusual coffee shops."};
const started=Date.now();const card=await generateSuggestion(provider,config,request);console.log(JSON.stringify({provider,elapsedMs:Date.now()-started,fitsG2Target:card.replies.every(r=>r.english.length<=36),card},null,2));assert.equal(card.detectedLanguage,"EN");assert.ok(card.replies.length>=1);assert.ok(!/^(user|they|speaker) said:/i.test(card.englishContext));for(const reply of card.replies)assert.match(reply.english,/[.!?]$/);
