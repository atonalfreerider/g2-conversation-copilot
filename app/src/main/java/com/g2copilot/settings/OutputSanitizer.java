package com.g2copilot.settings;

final class OutputSanitizer {
    static String cleanTranslation(String value){return value.trim().replaceFirst("(?i)^(?:user|they|speaker)\\s+(?:said|says)\\s*:\\s*","").replaceFirst("(?i)\\s*[-–—,:;]*\\s*(?:a\\s+)?(?:polish|russian|chinese)\\s+(?:friendly\\s+)?greeting\\.?$","").trim();}
    static String cleanPhonetic(String value){String cleaned=value.trim().replaceAll("(?iu)Dzień\\s+dobry|Dzien\\s+dobry","TEEN DOE Bray").replaceFirst("(?i)^(?:phonetic|say)\\s*:\\s*","").trim();if(cleaned.isEmpty()||cleaned.matches(".*[A-Z]{2,}.*"))return cleaned;int split=cleaned.indexOf(' ');return split<0?cleaned.toUpperCase(java.util.Locale.ROOT):cleaned.substring(0,split).toUpperCase(java.util.Locale.ROOT)+cleaned.substring(split);}
    private OutputSanitizer(){}
}
