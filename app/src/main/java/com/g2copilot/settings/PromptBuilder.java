package com.g2copilot.settings;

final class PromptBuilder {
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript){
        return build(language,playful,inquisitive,risk,biography,transcript,"");
    }
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript,String instantEnglish){
        String riskRule="The queue is an exact four-level risk ladder. Risk 4 is ABSOLUTE MAXIMUM SOCIAL BOLDNESS: take a real social chance; be daring, provocative, confidently flirtatious, vulnerable, surprising, or playfully challenging. It must be dramatically bolder than risk 3 and never collapse into generic empathy, agreement, or small talk. Never use deception, coercion, illegality, harassment, or ignore boundaries.";
        String bio=biography==null||biography.trim().isEmpty()?"No biography provided.":biography.trim();
        String instant=instantEnglish==null||instantEnglish.trim().isEmpty()?"":"\nPINNED TRANSLATION: Branch 1 MUST have english exactly `"+instantEnglish.trim()+"` and phonetic must be its natural translation into the active foreign language, written as English-readable sounds.\n";
        return "CONTROL VARIABLES: language="+language+"; persona="+(playful?"P":"S")+".\n"+
            "SPEAKER BIOGRAPHY: "+bio+"\n"+riskRule+"\nConversation transcript (oldest to newest):\n"+transcript+"\n\n"+
            instant+"Infer the active language only when language=AUTO. Return only JSON with language, context, and exactly EIGHT branch objects, each shaped {english, phonetic}. context contains ONLY a clean English translation of the last 20 microphone words—no labels or commentary. The eight branches MUST occur in this exact order: inquisitive risk 1, declarative risk 1, inquisitive risk 2, declarative risk 2, inquisitive risk 3, declarative risk 3, inquisitive risk 4, declarative risk 4. Questions must end in ?. Declarative branches must not be questions. Generate organic next things grounded in the whole conversation and biography. When context is sparse in foreign mode, use common greetings and inquisitive phrases while retaining the matrix. P=playful; S=strategic/trust-building. For American English, phonetic is empty. For non-English, BOTH fields are mandatory: english is the clean English meaning and phonetic is that reply in the target language rendered ONLY as stupidly easy American-English sound spelling. CAPITALIZE stressed syllables. Examples: Polish Dzień dobry → TEEN DOE Bray; Spanish buenos días → BWAY-nohs DEE-ahs; French bonjour → bohn-ZHOOR; German guten Tag → GOO-ten tahk. No native spelling, diacritics, IPA, labels, or commentary. Each field must contain no newline, be complete, and be at most 72 characters so it occupies no more than two G2 lines. Never omit either foreign field and never cut a word.";
    }
    private PromptBuilder(){}
}
