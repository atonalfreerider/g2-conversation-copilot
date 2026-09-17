import test from "node:test";
import assert from "node:assert/strict";
import { buildPrompt } from "../src/provider-contract.js";

const prompt = overrides => buildPrompt({mode:"playful",tone:{stance:"inquisitive",risk:7},biography:"I am a direct, adventurous photographer from Chicago.",contextWordCount:20,rollingText:"Cześć. Dzień dobry.",latestText:"Dzień dobry.",...overrides});

test("risk seven explicitly demands a real social chance",()=>{
  const value=prompt();assert.match(value,/MAXIMUM SOCIAL BOLDNESS/);assert.match(value,/daring, provocative/);assert.match(value,/Do not retreat to generic empathy/);
});

test("speaker biography is injected as point-of-view context",()=>{
  assert.match(prompt(),/adventurous photographer from Chicago/);assert.match(prompt(),/shape suggestions naturally/);
});

test("translation is clean and phonetics use English sound chunks",()=>{
  const value=prompt();assert.match(value,/ONLY a clean English translation/);assert.match(value,/no labels, speaker attribution/);assert.match(value,/Dzień dobry” → “TEEN DOE Bray/);assert.match(value,/CAPITALIZE every stressed/);
});

test("foreign branches require two strict one-line fields",()=>{const value=prompt();assert.match(value,/BOTH english and phonetic are mandatory/);assert.match(value,/one complete line of at most 36 characters/);});

test("contrarian mode challenges claims without becoming hostile",()=>{
  const value=prompt({mode:"contrarian"});assert.match(value,/persona=C/);assert.match(value,/challenge assumptions/);assert.match(value,/evidence-seeking/);assert.match(value,/non-hostile/);
});
