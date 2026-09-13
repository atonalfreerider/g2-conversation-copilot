package com.g2copilot.settings;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class ProviderClient {
    static String test(Provider provider, String baseUrl, String model, String apiKey) throws Exception {
        if (apiKey.trim().isEmpty()) throw new IllegalArgumentException("Enter an API key first.");
        URL url; JSONObject body = new JSONObject();
        if (provider == Provider.GEMINI) {
            url = new URL(trimSlash(baseUrl) + "/models/" + encodePath(model) + ":generateContent");
            body.put("contents", new org.json.JSONArray().put(new JSONObject().put("parts", new org.json.JSONArray().put(new JSONObject().put("text", "Reply with exactly: connected")))));
        } else {
            url = new URL(trimSlash(baseUrl) + "/responses"); body.put("model", model).put("input", "Reply with exactly: connected").put("store", false).put("max_output_tokens", 24);
        }
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(12_000); connection.setReadTimeout(25_000);
        connection.setRequestProperty("Content-Type", "application/json");
        if (provider == Provider.GEMINI) connection.setRequestProperty("x-goog-api-key", apiKey);
        else connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = connection.getResponseCode(); InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
        String response = read(stream);
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + safeMessage(response));
        return "Connected · HTTP " + code;
    }
    private static String trimSlash(String value) { return value.endsWith("/") ? value.substring(0,value.length()-1) : value; }
    private static String encodePath(String value) { return value.replace("/", "%2F").replace(" ", "%20"); }
    private static String read(InputStream stream) throws Exception { if (stream == null) return ""; BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(String line;(line=r.readLine())!=null;) b.append(line); return b.toString(); }
    private static String safeMessage(String raw) { try { JSONObject j=new JSONObject(raw); JSONObject e=j.optJSONObject("error"); return e == null ? "Provider rejected the request" : e.optString("message","Provider rejected the request"); } catch(Exception ignored) { return "Provider rejected the request"; } }
}
