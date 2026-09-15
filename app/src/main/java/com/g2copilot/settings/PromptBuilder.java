package com.g2copilot.settings;

final class PromptBuilder {
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript){
        return build(language,playful,inquisitive,risk,biography,transcript,"");
    }
    static String build(String language,boolean playful,boolean inquisitive,int risk,String biography,String transcript,String instantEnglish){
        String riskRule="The queue is an exact four-level risk ladder. Risk 4 is ABSOLUTE MAXIMUM SOCIAL BOLDNESS: take a real social chance; be daring, provocative, confidently flirtatious, vulnerable, surprising, or playfully challenging. It must be dramatically bolder than risk 3 and never collapse into generic empathy, agreement, or small talk. Never use deception, coercion, illegality, harassment, or ignore boundaries.";
        String bio=biography==null||biography.trim().isEmpty()?"No biography provided.":biography.trim();
        String instant=instantEnglish==null||instantEnglish.trim().isEmpty()?"":"\nPINNED TRANSLATION: Branch 1 MUST have english exactly `"+instantEnglish.trim()+"`; native must be its translation in the selected language; phonetic must be an English-readable pronunciation of that exact native translation.\n";
        return "CONTROL VARIABLES: language="+language+"; persona="+(playful?"P":"S")+".\n"+
            "SPEAKER BIOGRAPHY: "+bio+"\n"+riskRule+"\nConversation transcript (oldest to newest):\n"+transcript+"\n\n"+
            instant+"The phone-selected language is authoritative. NEVER answer in English unless language=EN. Return only JSON with language, context, and exactly EIGHT branch objects, each shaped {english, native, phonetic}. context contains ONLY a clean English translation of the last 20 microphone words—no labels or commentary. The eight branches MUST occur in this exact order: inquisitive risk 1, declarative risk 1, inquisitive risk 2, declarative risk 2, inquisitive risk 3, declarative risk 3, inquisitive risk 4, declarative risk 4. Questions must end in ?. Declarative branches must not be questions. Generate organic next things grounded in the whole conversation and biography. When context is sparse in foreign mode, use common greetings and inquisitive phrases while retaining the matrix. P=playful; S=strategic/trust-building. For American English, native and phonetic are empty. For every non-English language, ALL THREE fields are mandatory: english is meaning only; native contains the actual reply in the selected language's normal writing system; phonetic is an American-English-readable pronunciation of that exact native reply. Phonetic must never pronounce the English meaning. CAPITALIZE stressed syllables. Examples: native Dzień dobry, phonetic TEEN DOE Bray; native buenos días, phonetic BWAY-nohs DEE-ahs; native bonjour, phonetic bohn-ZHOOR; native guten Tag, phonetic GOO-ten tahk. No IPA or commentary. Each field contains no newline, is complete, and is at most 72 characters. Never omit a foreign field and never cut a word.";
    }
    private PromptBuilder(){}
}
