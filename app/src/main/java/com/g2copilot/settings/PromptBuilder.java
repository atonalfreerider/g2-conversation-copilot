package com.g2copilot.settings;

final class PromptBuilder {
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript){
        return build(language,playful,inquisitive,risk,biography,transcript,"");
    }
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript,String instantEnglish){
        String riskRule=risk==7
            ? "RISK 7 IS MAXIMUM SOCIAL BOLDNESS. Take a real social chance: be daring, provocative, confidently flirtatious, vulnerable, surprising, or playfully challenging. Do not retreat to generic empathy, bland agreement, safe small talk, or merely ask what happened next. Never use deception, coercion, illegality, harassment, or ignore boundaries."
            : "Risk "+risk+"/7 must materially control social boldness instead of defaulting to generic safe phrasing.";
        String bio=biography==null||biography.trim().isEmpty()?"No biography provided.":biography.trim();
        String instant=instantEnglish==null||instantEnglish.trim().isEmpty()?"":"\nPINNED TRANSLATION: Branch 1 MUST have english exactly `"+instantEnglish.trim()+"` and phonetic must be its natural translation into the active foreign language, written as English-readable sounds.\n";
        return "CONTROL VARIABLES: language="+language+"; persona="+(playful?"P":"S")+"; stance="+(inquisitive?"I":"D")+"; risk="+risk+"/7.\n"+
            "SPEAKER BIOGRAPHY: "+bio+"\n"+riskRule+"\nConversation transcript (oldest to newest):\n"+transcript+"\n\n"+
            instant+"Infer the active language only when language=AUTO. Return only JSON with language, context, and exactly SIX branch objects, each shaped {english, phonetic}. context contains ONLY a clean English translation of the last 20 microphone words—no labels or commentary. Generate six short, organic next things the biography's speaker could say, grounded in the whole conversation and their point of view. When there is little context in foreign mode, fill the queue with common greetings and inquisitive phrases. P=playful; S=strategic/trust-building; I=questions; D=statements. For American English, phonetic is empty. For non-English, BOTH fields are mandatory: english is the clean English meaning and phonetic is that reply in the target language rendered ONLY as stupidly easy American-English sound spelling. CAPITALIZE stressed syllables. Examples: Polish Dzień dobry → TEEN DOE Bray; Spanish buenos días → BWAY-nohs DEE-ahs; French bonjour → bohn-ZHOOR; German guten Tag → GOO-ten tahk. No native spelling, diacritics, IPA, labels, or commentary. Each field must contain no newline, be a complete thought, and be at most 72 characters so it occupies no more than two G2 lines. Never omit either foreign field and never cut a word.";
    }
    private PromptBuilder(){}
}
