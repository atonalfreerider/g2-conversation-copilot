import test from "node:test";
import assert from "node:assert/strict";
import { generateSuggestion } from "../backend/provider-adapters.js";

const request = { mode:"playful", tone:{stance:"inquisitive",risk:4}, contextWordCount:20, rollingText:"hello", latestText:"hello" };
const card = { detectedLanguage:"EN", englishContext:"hello", replies:[{english:"Hi."}] };

test("OpenAI and xAI use Responses with bearer auth and schema", async () => {
  for (const provider of ["openai", "xai"]) {
    let call;
    const fake = async (url, options) => { call={url,options}; return {ok:true,json:async()=>({output_text:JSON.stringify(card)})}; };
    assert.deepEqual(await generateSuggestion(provider,{baseUrl:`https://${provider}.test/v1`,model:"model",apiKey:"secret"},request,fake),card);
    assert.equal(call.url,`https://${provider}.test/v1/responses`);
    assert.equal(call.options.headers.Authorization,"Bearer secret");
    assert.equal(JSON.parse(call.options.body).text.format.type,"json_schema");
  }
});

test("Gemini uses API-key header and JSON schema", async () => {
  let call;
  const fake=async(url,options)=>{call={url,options};return{ok:true,json:async()=>({candidates:[{content:{parts:[{text:JSON.stringify(card)}]}}]})};};
  assert.deepEqual(await generateSuggestion("gemini",{baseUrl:"https://google.test/v1beta",model:"flash",apiKey:"secret"},request,fake),card);
  assert.equal(call.options.headers["x-goog-api-key"],"secret");
  assert.equal(JSON.parse(call.options.body).generationConfig.responseMimeType,"application/json");
});
