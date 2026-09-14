package com.g2copilot.settings;

public enum Provider {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5.2"),
    XAI("xAI / Grok", "https://api.x.ai/v1", "grok-4.20-0309-non-reasoning"),
    GEMINI_NANO("Gemini Nano · on-device", "on-device://aicore", "gemini-nano"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.8-flash");

    public final String label, defaultUrl, defaultModel;
    Provider(String label, String defaultUrl, String defaultModel) {
        this.label = label; this.defaultUrl = defaultUrl; this.defaultModel = defaultModel;
    }
    @Override public String toString() { return label; }
}
