export const suggestionSchema = {
  type: "object", additionalProperties: false,
  required: ["detectedLanguage", "englishContext", "replies"],
  properties: {
    detectedLanguage: { type: "string", pattern: "^[A-Z]{2}$" },
    englishContext: { type: "string", maxLength: 180 },
    replies: { type: "array", minItems: 1, maxItems: 3, items: {
      type: "object", additionalProperties: false, required: ["english", "native", "phonetic"],
      properties: {
        english: { type: "string" },
        native: { type: "string" },
        phonetic: { type: "string" },
      },
    }},
  },
};

export function buildPrompt(req) {
  const style = req.mode === "playful"
    ? "warm, playful, lightly sarcastic when clearly welcome; never sexual or coercive"
    : "curious and trust-building; never manipulate, pressure, diagnose, or exploit vulnerability";
  const variables = `CONTROL VARIABLES: language=AUTO; persona=${req.mode === "playful" ? "P" : "S"}; stance=${req.tone.stance === "inquisitive" ? "I" : "D"}; risk=${req.tone.risk}/7.`;
  const biography = (req.biography ?? "").trim() || "No biography provided.";
  const risk = req.tone.risk === 7
    ? "RISK 7 IS MAXIMUM SOCIAL BOLDNESS. Prefer daring, provocative, confidently flirtatious, vulnerable, surprising, or playfully challenging branches. Take a real social chance. Do not retreat to generic empathy, bland agreement, safe small talk, or merely ask what happened next. Still never use deception, coercion, illegality, harassment, or ignore boundaries."
    : `Risk ${req.tone.risk}/7 controls social boldness; calibrate it materially rather than defaulting to generic safe phrasing.`;
  return `${variables}
Act as an unobtrusive conversation coach. Style: ${style}.
Interpret P as playful, S as strategic, I as inquisitive, and D as declarative. ${risk}
Speaker biography (the user's identity and point of view; use it to shape suggestions naturally, but never invent facts beyond it): ${biography}
Return only the supplied schema. Detect the spoken language and return its ISO 639-1 two-letter code. englishContext must be ONLY a clean English translation of the latest ${req.contextWordCount} microphone words: no labels, speaker attribution, explanation, language name, or commentary.
Every branch must be a complete, grammatical sentence of at most 60 characters; never cut a word or sentence to meet the limit. For English speech, set native and phonetic to empty strings. For foreign speech, phonetic must be stupidly easy for an English speaker to pronounce: use familiar English sound chunks, spaces, and simple hyphens; never native spelling or diacritics. Required calibration: Polish “Dzień dobry” becomes exactly “Teen Doe Bray”. Prefer readable approximation over linguistic notation.
Context: ${req.rollingText}
Latest utterance: ${req.latestText}`;
}
