package com.g2copilot.settings;

final class OutputSanitizer {
    static String cleanTranslation(String value){return value.trim().replaceFirst("(?i)^(?:user|they|speaker)\\s+(?:said|says)\\s*:\\s*","").replaceFirst("(?i)\\s*[-–—,:;]*\\s*(?:a\\s+)?(?:polish|russian|chinese)\\s+(?:friendly\\s+)?greeting\\.?$","").trim();}
    static String cleanPhonetic(String value){return value.trim().replaceAll("(?iu)Dzień\\s+dobry|Dzien\\s+dobry","Teen Doe Bray").replaceFirst("(?i)^(?:phonetic|say)\\s*:\\s*","").trim();}
    private OutputSanitizer(){}
}
