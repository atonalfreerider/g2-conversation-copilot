export const suggestionSchema = {
  type: "object", additionalProperties: false,
  required: ["detectedLanguage", "englishContext", "replies"],
  properties: {
    detectedLanguage: { type: "string", pattern: "^[A-Z]{2}$" },
    englishContext: { type: "string", maxLength: 180 },
    replies: { type: "array", minItems: 1, maxItems: 3, items: {
      type: "object", additionalProperties: false, required: ["english"],
      properties: {
        english: { type: "string", maxLength: 80 },
        native: { type: "string", maxLength: 80 },
        phonetic: { type: "string", maxLength: 100 },
      },
    }},
  },
};

export function buildPrompt(req) {
  const style = req.mode === "playful"
    ? "warm, playful, lightly sarcastic when clearly welcome; never sexual or coercive"
    : "curious and trust-building; never manipulate, pressure, diagnose, or exploit vulnerability";
  return `Act as an unobtrusive conversation coach. Style: ${style}.
Tone stance is ${req.tone.stance}/7 (0 inquisitive, 7 declarative). Risk is ${req.tone.risk}/7 (0 socially safe, 7 bold but still respectful, truthful, and consent-aware).
Return only the supplied schema. Detect the spoken language and return its ISO 639-1 two-letter code. Always translate the latest ${req.contextWordCount} microphone words into English for englishContext. For foreign speech, provide English meaning, native translation, and an English-speaker-friendly phonetic rendering for every branch.
Context: ${req.rollingText}
Latest utterance: ${req.latestText}`;
}
