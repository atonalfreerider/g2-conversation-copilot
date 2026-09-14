import fs from "node:fs";
import assert from "node:assert/strict";
import { generateSuggestion } from "../backend/provider-adapters.js";

const provider=process.argv[2]??"openai";const turns=Number(process.argv[3]??10);
const keyFile=provider==="openai"?"/home/john/Documents/oai-key":"/home/john/Documents/xAI-Key";
const config=provider==="openai"?{baseUrl:"https://api.openai.com/v1",model:"gpt-5.2",apiKey:fs.readFileSync(keyFile,"utf8").trim()}:{baseUrl:"https://api.x.ai/v1",model:"grok-4.20-0309-non-reasoning",apiKey:fs.readFileSync(keyFile,"utf8").trim()};
const lines=["I just moved to Seattle.","I work in architecture.","The rain is oddly comforting.","I found a tiny jazz bar.","I usually go there alone.","I want to meet more adventurous people.","I photograph strange buildings.","Travel keeps me sane.","Lisbon is next on my list.","I leave in three weeks.","I have no itinerary yet.","That is part of the fun."];
let transcript="";const timings=[];for(let i=0;i<turns;i++){transcript+=(transcript?" ":"")+lines[i%lines.length];const started=Date.now();const card=await generateSuggestion(provider,config,{mode:"playful",tone:{stance:i%2?"declarative":"inquisitive",risk:7},biography:"I am a direct, adventurous photographer from Chicago.",contextWordCount:20,rollingText:transcript,latestText:lines[i%lines.length]});timings.push(Date.now()-started);assert.ok(card.replies.length);assert.ok(card.replies.every(r=>r.english.trim()));process.stdout.write(`turn ${i+1}/${turns} ok ${timings.at(-1)}ms\n`);}
console.log(JSON.stringify({provider,turns,minMs:Math.min(...timings),maxMs:Math.max(...timings),averageMs:Math.round(timings.reduce((a,b)=>a+b,0)/turns)}));
