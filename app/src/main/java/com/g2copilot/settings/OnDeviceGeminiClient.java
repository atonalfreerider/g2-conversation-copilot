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
        String raw=response.getCandidates().get(0).getText().trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");JSONObject result=new JSONObject(raw);java.util.regex.Matcher lm=java.util.regex.Pattern.compile("CONTROL VARIABLES: language=([A-Z]{2,8})").matcher(prompt);String detected=lm.find()?lm.group(1):"EN";JSONArray values=result.getJSONArray("branches");java.util.List<Branch> branches=new java.util.ArrayList<>();for(int i=0;i<Math.min(8,values.length());i++){JSONObject branch=values.optJSONObject(i);if(branch==null)continue;String english=branch.optString("english","").replaceAll("\\s+"," ").trim();String nativeText=branch.optString("native","").replaceAll("\\s+"," ").trim();String phonetic=OutputSanitizer.cleanPhonetic(branch.optString("phonetic","")).replaceAll("\\s+"," ").trim();if(!english.isEmpty()&&("EN".equals(detected)||(!phonetic.isEmpty()&&!nativeText.isEmpty())))branches.add(new Branch(english,phonetic,nativeText));}if(branches.isEmpty())throw new IllegalStateException("Gemini Nano returned no complete branches");int seed=branches.size();for(int i=0;branches.size()<8;i++)branches.add(branches.get(i%seed));
        return new ProviderClient.Suggestions(detected,OutputSanitizer.cleanTranslation(result.optString("context","")),branches);
    }
}
