package com.g2copilot.settings;

final class PromptBuilder {
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript){
        String riskRule=risk==7
            ? "RISK 7 IS MAXIMUM SOCIAL BOLDNESS. Take a real social chance: be daring, provocative, confidently flirtatious, vulnerable, surprising, or playfully challenging. Do not retreat to generic empathy, bland agreement, safe small talk, or merely ask what happened next. Never use deception, coercion, illegality, harassment, or ignore boundaries."
            : "Risk "+risk+"/7 must materially control social boldness instead of defaulting to generic safe phrasing.";
        String bio=biography==null||biography.trim().isEmpty()?"No biography provided.":biography.trim();
        return "CONTROL VARIABLES: language="+language+"; persona="+(playful?"P":"S")+"; stance="+(inquisitive?"I":"D")+"; risk="+risk+"/7.\n"+
            "SPEAKER BIOGRAPHY: "+bio+"\n"+riskRule+"\nConversation transcript (oldest to newest):\n"+transcript+"\n\n"+
            "Infer the active language only when language=AUTO. Return only JSON: {\"language\":\"two-letter code\",\"context\":\"...\",\"branches\":[{\"english\":\"...\",\"phonetic\":\"...\"},{\"english\":\"...\",\"phonetic\":\"...\"}]}. context must contain ONLY a clean English translation of the last 20 microphone words—no 'user said', 'they said', speaker label, language label, explanation, or commentary. Generate exactly two short, organic next things the biography's speaker could say, grounded in the whole conversation and their point of view. P=playful; S=strategic/trust-building; I=questions; D=statements. For English speech, english is the suggested sentence and phonetic is empty. For non-English speech, BOTH fields are mandatory: english is the clean English meaning and phonetic is the exact same reply rendered ONLY as stupidly easy English sound spelling. Use familiar English syllables, spaces, and simple hyphens; no native spelling, diacritics, IPA, labels, or commentary. Mandatory example: Polish Dzień dobry → Teen Doe Bray. STRICT G2 LINE POLICY: each field is one complete line, contains no newline, and is at most 36 characters. Never omit either foreign field and never cut a word.";
    }
    private PromptBuilder(){}
}
