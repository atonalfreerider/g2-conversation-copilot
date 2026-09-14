package com.g2copilot.settings;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class OnDeviceGeminiClient {
    static ProviderClient.Suggestions suggest(String prompt,ProviderClient.LogSink log) throws Exception {
        GenerativeModelFutures model=GenerativeModelFutures.from(Generation.INSTANCE.getClient());
        int status=model.checkStatus().get(15,TimeUnit.SECONDS);
        if(status==FeatureStatus.DOWNLOADABLE){
            if(log!=null)log.log("Gemini Nano: downloading AICore model…");
            model.download(new DownloadCallback(){public void onDownloadStarted(long bytes){if(log!=null)log.log("Nano download: "+bytes+" bytes");}public void onDownloadProgress(long bytes){}public void onDownloadCompleted(){if(log!=null)log.log("Nano download complete");}public void onDownloadFailed(GenAiException e){if(log!=null)log.log("Nano download failed: "+e.getMessage());}}).get(10,TimeUnit.MINUTES);
        }else if(status!=FeatureStatus.AVAILABLE)throw new IllegalStateException("Gemini Nano is unavailable in AICore on this Pixel");
        if(log!=null)log.log("Nano ← generating locally (no API key)");
        GenerateContentResponse response=model.generateContent(prompt).get(45,TimeUnit.SECONDS);
        String raw=response.getCandidates().get(0).getText().trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");JSONObject result=new JSONObject(raw);String detected=result.optString("language","EN").toUpperCase();JSONArray values=result.getJSONArray("branches");if(values.length()!=2)throw new IllegalStateException("Provider must return exactly two branches");java.util.List<String> branches=new java.util.ArrayList<>();for(int i=0;i<2;i++){JSONObject branch=values.optJSONObject(i);if(branch==null)throw new IllegalStateException("Provider returned legacy phonetic-only branch");String english=branch.optString("english","").replaceAll("\\s+"," ").trim();String phonetic=OutputSanitizer.cleanPhonetic(branch.optString("phonetic","")).replaceAll("\\s+"," ").trim();if(english.isEmpty())throw new IllegalStateException("English branch is required");if(!"EN".equals(detected)&&phonetic.isEmpty())throw new IllegalStateException("Foreign branch requires both English and phonetic");if(log!=null&&(english.length()>36||phonetic.length()>36))log.log("WARN branch exceeded 36-char G2 target; preserving complete sentence");branches.add("EN".equals(detected)?english:english+"\n"+phonetic);}
        return new ProviderClient.Suggestions(detected,OutputSanitizer.cleanTranslation(result.optString("context","")),branches);
    }
}
