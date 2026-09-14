package com.g2copilot.settings;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

final class ProviderClient {
    interface LogSink { void log(String message); }
    interface Cancellation { boolean cancelled(); void connected(HttpURLConnection connection); }
    static final class Suggestions {
        final String language, context; final java.util.List<String> branches;
        Suggestions(String language,String context,java.util.List<String> branches){this.language=language;this.context=context;this.branches=branches;}
    }
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
    static Suggestions suggest(Provider provider,String baseUrl,String model,String apiKey,String prompt,LogSink log,Cancellation cancellation) throws Exception {
        if(apiKey.trim().isEmpty())throw new IllegalArgumentException("Configure the selected provider API key first.");
        URL url;JSONObject body=new JSONObject();
        if(provider==Provider.GEMINI){
            url=new URL(trimSlash(baseUrl)+"/models/"+encodePath(model)+":generateContent");
            body.put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))));
            body.put("generationConfig",new JSONObject().put("responseMimeType","application/json").put("temperature",0.85));
        }else{
            url=new URL(trimSlash(baseUrl)+"/chat/completions");body.put("model",model);if(provider==Provider.XAI)body.put("temperature",0.85);
            body.put(provider==Provider.OPENAI?"max_completion_tokens":"max_tokens",provider==Provider.OPENAI?800:300);
            if(provider==Provider.XAI&&model.startsWith("grok-4.6"))body.put("reasoning_effort","low");
            body.put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content","Return only valid JSON. You are a discreet real-time conversation copilot.")).put(new JSONObject().put("role","user").put("content",prompt)));
            body.put("response_format",new JSONObject().put("type","json_object"));
            if(provider==Provider.XAI)body.put("stream",true);
        }
        String raw=provider==Provider.XAI?postStreamingXai(url,apiKey,body,log,cancellation):post(provider,url,apiKey,body);if(cancellation.cancelled())throw new java.io.IOException("SUPERSEDED");String content;
        JSONObject envelope=new JSONObject(raw);
        if(provider==Provider.GEMINI)content=envelope.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        else content=envelope.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        content=content.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");JSONObject result=new JSONObject(content);
        String detected=result.optString("language","EN").toUpperCase();JSONArray values=result.getJSONArray("branches");if(values.length()!=2)throw new IllegalStateException("Provider must return exactly two branches");java.util.List<String> branches=new java.util.ArrayList<>();for(int i=0;i<2;i++){JSONObject branch=values.optJSONObject(i);if(branch==null)throw new IllegalStateException("Provider returned legacy phonetic-only branch");String english=oneLine(branch.optString("english",""));String phonetic=oneLine(OutputSanitizer.cleanPhonetic(branch.optString("phonetic","")));if(english.isEmpty())throw new IllegalStateException("English branch is required");if(!"EN".equals(detected)&&phonetic.isEmpty())throw new IllegalStateException("Foreign branch requires both English and phonetic");if(log!=null&&(english.length()>36||phonetic.length()>36))log.log("WARN branch exceeded 36-char G2 target; preserving complete sentence");branches.add("EN".equals(detected)?english:english+"\n"+phonetic);}
        return new Suggestions(detected,OutputSanitizer.cleanTranslation(result.optString("context","")),branches);
    }
    private static String post(Provider provider,URL url,String apiKey,JSONObject body)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setRequestMethod("POST");connection.setConnectTimeout(12_000);connection.setReadTimeout(30_000);connection.setRequestProperty("Content-Type","application/json");
        if(provider==Provider.GEMINI)connection.setRequestProperty("x-goog-api-key",apiKey);else connection.setRequestProperty("Authorization","Bearer "+apiKey);connection.setDoOutput(true);
        try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=connection.getResponseCode();String response=read(code<400?connection.getInputStream():connection.getErrorStream());if(code<200||code>=300)throw new IllegalStateException("HTTP "+code+": "+safeMessage(response));return response;
    }
    private static String postStreamingXai(URL url,String apiKey,JSONObject body,LogSink log,Cancellation cancellation)throws Exception{
        if(log!=null)log.log("PHASE connecting to api.x.ai");HttpURLConnection connection=(HttpURLConnection)url.openConnection();cancellation.connected(connection);connection.setRequestMethod("POST");connection.setConnectTimeout(12_000);connection.setReadTimeout(20_000);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","text/event-stream");connection.setRequestProperty("Authorization","Bearer "+apiKey);connection.setDoOutput(true);
        try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}if(log!=null)log.log("PHASE request uploaded; awaiting HTTP response");int code=connection.getResponseCode();if(log!=null)log.log("PHASE HTTP "+code+"; stream opened");if(code<200||code>=300)throw new IllegalStateException("HTTP "+code+": "+safeMessage(read(connection.getErrorStream())));
        StringBuilder content=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){for(String line;(line=reader.readLine())!=null;){if(cancellation.cancelled())throw new java.io.IOException("SUPERSEDED");if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if("[DONE]".equals(data)){if(log!=null)log.log("SSE ← [DONE]");break;}JSONObject event=new JSONObject(data);JSONArray choices=event.optJSONArray("choices");if(choices==null||choices.length()==0)continue;JSONObject deltaObject=choices.getJSONObject(0).optJSONObject("delta");if(deltaObject!=null){String delta=deltaObject.optString("content","");content.append(delta);if(log!=null&&!delta.isEmpty())log.log("SSE ← "+delta);}}}
        return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",content.toString())))).toString();
    }
    private static String trimSlash(String value) { return value.endsWith("/") ? value.substring(0,value.length()-1) : value; }
    private static String oneLine(String value){return value.replace('\n',' ').replace('\r',' ').replaceAll("\\s+"," ").trim();}
    private static String encodePath(String value) { return value.replace("/", "%2F").replace(" ", "%20"); }
    private static String read(InputStream stream) throws Exception { if (stream == null) return ""; BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(String line;(line=r.readLine())!=null;) b.append(line); return b.toString(); }
    private static String safeMessage(String raw) { try { JSONObject j=new JSONObject(raw); JSONObject e=j.optJSONObject("error"); return e == null ? "Provider rejected the request" : e.optString("message","Provider rejected the request"); } catch(Exception ignored) { return "Provider rejected the request"; } }
}
