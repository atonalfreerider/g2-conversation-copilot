package com.g2copilot.settings;

import android.app.*;import android.content.Intent;import android.os.IBinder;import android.util.Base64;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;import org.json.*;

public final class CompanionBridgeService extends Service {
    private static final int PORT=8787;private ServerSocket server;private final ExecutorService pool=Executors.newCachedThreadPool();private volatile boolean running;
    @Override public void onCreate(){super.onCreate();NotificationChannel channel=new NotificationChannel("g2_bridge","G2 Copilot bridge",android.app.NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(channel);Notification n=new Notification.Builder(this,"g2_bridge").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("G2 Conversation Copilot").setContentText("EvenHub provider bridge active").setOngoing(true).build();startForeground(8787,n);startServer();}
    @Override public int onStartCommand(Intent i,int f,int id){return START_STICKY;}@Override public IBinder onBind(Intent i){return null;}
    private void startServer(){running=true;pool.execute(()->{try{server=new ServerSocket(PORT,16,InetAddress.getByName("127.0.0.1"));while(running){Socket socket=server.accept();pool.execute(()->handle(socket));}}catch(Exception ignored){}});}
    private void handle(Socket socket) {
        if (socket == null) return;
        try {
            socket.setSoTimeout(90_000);
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int state = 0, b;
            while ((b = in.read()) != -1) {
                header.write(b);
                state = (state == 0 && b == '\r') ? 1
                        : (state == 1 && b == '\n') ? 2
                        : (state == 2 && b == '\r') ? 3
                        : (state == 3 && b == '\n') ? 4 : 0;
                if (state == 4) break;
            }
            String h = header.toString("UTF-8");
            String first = h.split("\r\n")[0];
            int length = 0;
            for (String line : h.split("\r\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    length = Integer.parseInt(line.substring(15).trim());
                }
            }
            byte[] body = new byte[length];
            for (int off = 0; off < length;) {
                int n = in.read(body, off, length - off);
                if (n < 0) break;
                off += n;
            }
            if (first.startsWith("OPTIONS")) {
                respond(socket, 204, "");
                return;
            }
            JSONObject req = length == 0
                    ? new JSONObject()
                    : new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (first.contains(" /config ")) {
                SecureSettings s = new SecureSettings(this);
                respond(socket, 200, new JSONObject().put("provider", s.activeProvider().name()).toString());
                return;
            }
            if (first.contains(" /provider ")) {
                Provider p = Provider.valueOf(req.getString("provider"));
                new SecureSettings(this).setActiveProvider(p);
                respond(socket, 200, new JSONObject().put("provider", p.name()).toString());
                return;
            }
            if (first.contains(" /transcribe ")) {
                String transcript = transcribe(
                        Base64.decode(req.getString("audio"), Base64.DEFAULT),
                        req.optString("language", "en-US"));
                JSONObject out = new JSONObject().put("noSpeech", transcript.isEmpty());
                if (!transcript.isEmpty()) out.put("transcript", transcript).put("speaker", "unknown");
                respond(socket, 200, out.toString());
                return;
            }
            if (first.contains(" /turn ")) {
                String transcript = transcribe(
                        Base64.decode(req.getString("audio"), Base64.DEFAULT),
                        req.optString("language", "en-US"));
                if (transcript.isEmpty()) {
                    respond(socket, 200, new JSONObject().put("noSpeech", true).toString());
                    return;
                }
                appendTurn(req, "unknown", transcript);
                JSONObject out = suggest(req).put("transcript", transcript).put("speaker", "unknown");
                respond(socket, 200, out.toString());
                return;
            }
            if (first.contains(" /suggest ")) {
                respond(socket, 200, suggest(req).toString());
                return;
            }
            respond(socket, 404, new JSONObject().put("error", "Not found").toString());
        } catch (Exception e) {
            try {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                respond(socket, 500, new JSONObject().put("error", message).toString());
            } catch (Exception ignored) {
            }
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
    private void appendTurn(JSONObject req,String speaker,String text)throws Exception{JSONArray turns=req.optJSONArray("turns");if(turns==null){turns=new JSONArray();req.put("turns",turns);}turns.put(new JSONObject().put("speaker",speaker).put("text",text));}
    private JSONObject suggest(JSONObject req)throws Exception{SecureSettings settings=new SecureSettings(this);Provider p=settings.activeProvider();String prompt=prompt(req);ProviderClient.Suggestions s;if(p==Provider.GEMINI_NANO)s=OnDeviceGeminiClient.suggest(prompt,null);else s=ProviderClient.suggest(p,settings.url(p),settings.model(p),settings.key(p),prompt,null,new ProviderClient.Cancellation(){public boolean cancelled(){return false;}public void connected(HttpURLConnection c){}});JSONObject out=new JSONObject().put("context",s.context).put("provider",p.name());JSONArray branches=new JSONArray();for(Branch b:s.branches)branches.put(new JSONObject().put("english",b.english).put("native",b.nativeText).put("phonetic",b.phonetic));return out.put("branches",branches);}
    private String prompt(JSONObject req) {
        String tag = req.optString("language", "en-US");
        String code = Locale.forLanguageTag(tag).getLanguage().toUpperCase(Locale.ROOT);
        String lang = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.US);
        String style = "S".equals(req.optString("style"))
                ? "strategic and trust-building"
                : "playful, warm, lightly flirty when welcome";
        StringBuilder turns = new StringBuilder();
        JSONArray a = req.optJSONArray("turns");
        if (a != null) for (int i = Math.max(0, a.length() - 16); i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t != null) turns.append(t.optString("speaker")).append(": ").append(t.optString("text")).append('\n');
        }
        return "CONTROL VARIABLES: language=" + code + "; persona="
                + ("S".equals(req.optString("style")) ? "S" : "P") + ".\n"
                + "LANGUAGE LOCK: all suggested replies must be spoken in " + lang + ".\n"
                + "Field contract example: " + languageExample(code) + ".\n"
                + "The english field is the English meaning. The native field is its " + lang
                + " translation and MUST NOT contain the English wording. The phonetic field pronounces native using simple American-English sound chunks.\n"
                + "Style: " + style + ".\nConversation:\n" + turns
                + "Return only JSON with language, context, and exactly EIGHT branch objects shaped {english,native,phonetic}, "
                + "ordered inquisitive risk 1, declarative risk 1, inquisitive risk 2, declarative risk 2, inquisitive risk 3, "
                + "declarative risk 3, inquisitive risk 4, declarative risk 4. Context is only a clean English translation of the latest turn. "
                + "Risk 4 is maximum socially bold but never coercive or unsafe. Every field is one complete line of at most 36 characters and MUST be non-empty. "
                + "For English only, copy english into native and phonetic; the display ignores those two fields.";
    }
    private String languageExample(String code) {
        if ("ES".equals(code)) return "{english:'Hello',native:'Hola',phonetic:'OH-lah'}";
        if ("FR".equals(code)) return "{english:'Hello',native:'Bonjour',phonetic:'bohn-ZHOOR'}";
        if ("DE".equals(code)) return "{english:'Hello',native:'Guten Tag',phonetic:'GOO-ten tahk'}";
        if ("PL".equals(code)) return "{english:'Good day',native:'Dzień dobry',phonetic:'TEEN DOE-bray'}";
        if ("RU".equals(code)) return "{english:'Hello',native:'Привет',phonetic:'pree-VYET'}";
        if ("ZH".equals(code)) return "{english:'Hello',native:'你好',phonetic:'nee HOW'}";
        return "{english:'Hello',native:'Hello',phonetic:'Hello'}";
    }
    private String transcribe(byte[] wav,String tag)throws Exception{SecureSettings s=new SecureSettings(this);String sourceLanguage=Locale.forLanguageTag(tag).getLanguage();boolean translate=!"en".equals(sourceLanguage);String endpoint=translate?"translations":"transcriptions";String boundary="----g2"+System.currentTimeMillis();HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/audio/"+endpoint).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(12_000);c.setReadTimeout(30_000);c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+s.key(Provider.OPENAI));c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);ByteArrayOutputStream body=new ByteArrayOutputStream();part(body,boundary,"model",null,(translate?"whisper-1":"gpt-4o-mini-transcribe").getBytes(StandardCharsets.UTF_8));if(!translate)part(body,boundary,"language",null,sourceLanguage.getBytes(StandardCharsets.UTF_8));part(body,boundary,"file","turn.wav",wav);body.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));try(OutputStream out=c.getOutputStream()){body.writeTo(out);}int code=c.getResponseCode();InputStream stream=code<400?c.getInputStream():c.getErrorStream();String raw=read(stream);if(code>=400)throw new IllegalStateException("Transcription HTTP "+code);String text=new JSONObject(raw).optString("text").trim();return text.equals("...")?"":text;}
    private void part(OutputStream out,String boundary,String name,String filename,byte[] value)throws Exception{out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\""+(filename==null?"":"; filename=\""+filename+"\"")+"\r\n"+(filename==null?"":"Content-Type: audio/wav\r\n")+"\r\n").getBytes(StandardCharsets.UTF_8));out.write(value);out.write("\r\n".getBytes(StandardCharsets.UTF_8));}
    private String read(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];for(int n;(n=in.read(x))>0;)b.write(x,0,n);return b.toString("UTF-8");}
    private void respond(Socket socket,int code,String body)throws Exception{if(socket==null)return;byte[] bytes=body.getBytes(StandardCharsets.UTF_8);String status=code==200?"OK":code==204?"No Content":code==404?"Not Found":"Error";OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+" "+status+"\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: content-type\r\nAccess-Control-Allow-Methods: GET,POST,OPTIONS\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.flush();}
    @Override public void onDestroy(){running=false;try{server.close();}catch(Exception ignored){}pool.shutdownNow();super.onDestroy();}
}
