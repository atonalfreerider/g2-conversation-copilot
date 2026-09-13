import { suggestionSchema, buildPrompt } from "../src/provider-contract.js";

export async function generateSuggestion(provider, config, request, fetchImpl = fetch) {
  const prompt = buildPrompt(request);
  if (provider === "gemini") return gemini(config, prompt, fetchImpl);
  if (provider === "openai" || provider === "xai") return responses(config, prompt, fetchImpl);
  throw new Error(`Unsupported provider: ${provider}`);
}

async function responses(config, prompt, fetchImpl) {
  const response = await fetchImpl(`${trim(config.baseUrl)}/responses`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${config.apiKey}` },
    body: JSON.stringify({
      model: config.model, input: prompt, store: false, max_output_tokens: 300,
      text: { format: { type: "json_schema", name: "suggestion_card", strict: true, schema: suggestionSchema } },
    }),
  });
  const json = await checked(response);
  const text = json.output_text ?? json.output?.flatMap(x => x.content ?? []).find(x => x.type === "output_text")?.text;
  return validateCard(JSON.parse(text));
}

async function gemini(config, prompt, fetchImpl) {
  const response = await fetchImpl(`${trim(config.baseUrl)}/models/${encodeURIComponent(config.model)}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": config.apiKey },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }] }],
      generationConfig: { responseMimeType: "application/json", responseJsonSchema: suggestionSchema, maxOutputTokens: 300 },
    }),
  });
  const json = await checked(response);
  return validateCard(JSON.parse(json.candidates?.[0]?.content?.parts?.[0]?.text));
}

export function validateCard(card) {
  if (!card || !/^[A-Z]{2}$/.test(card.detectedLanguage) || typeof card.englishContext !== "string" || !Array.isArray(card.replies)) throw new Error("Provider returned an invalid card");
  if (card.replies.length < 1 || card.replies.length > 3) throw new Error("Provider returned the wrong number of branches");
  return card;
}

async function checked(response) {
  const json = await response.json();
  if (!response.ok) throw new Error(json.error?.message ?? `Provider HTTP ${response.status}`);
  return json;
}
const trim = value => value.replace(/\/$/, "");
