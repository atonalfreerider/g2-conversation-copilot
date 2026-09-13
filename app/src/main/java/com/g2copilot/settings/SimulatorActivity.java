package com.g2copilot.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SimulatorActivity extends Activity {
    private static final int MICROPHONE_PERMISSION=41;
    private static final long SPEECH_SETTLE_MS=650;
    private final String[] axes={"persona","stance","risk"};
    private int axis=0, risk=4; private boolean playful=true, inquisitive=true, listening=true;
    private String language="EN", context="Ready"; private List<String> branches=new ArrayList<>();
    private TextView contextLine, branchOne, branchTwo, axisHint, liveLog; private EditText utterance; private Button microphone; private Spinner speechLanguage;
    private SpeechRecognizer recognizer; private boolean microphoneActive=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor(); private SecureSettings settings; private boolean requestInFlight=false, queuedRefresh=false; private volatile long desiredGeneration=0; private volatile java.net.HttpURLConnection activeConnection;
    private final List<String> conversation=new ArrayList<>();
    private String pendingSpeech="", lastSuggestedSpeech="";
    private final Runnable settledSpeech=()->refreshLiveSuggestions();

    @Override public void onCreate(Bundle state){super.onCreate(state);settings=new SecureSettings(this);setContentView(build());render();}

    private View build(){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(22),dp(20),dp(30));root.setBackgroundColor(Color.rgb(243,246,242));scroll.addView(root);
        TextView title=text("G2 glasses simulator",26,Color.rgb(24,38,27));root.addView(title);
        root.addView(text("Uses your configured provider for live branches",14,Color.DKGRAY));
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setGravity(Gravity.CENTER_VERTICAL);frame.setPadding(dp(24),dp(14),dp(24),dp(14));frame.setBackgroundColor(Color.rgb(2,7,3));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(230));fp.setMargins(0,dp(18),0,dp(18));root.addView(frame,fp);
        contextLine=lensRegion(3);branchOne=lensRegion(2);branchTwo=lensRegion(2);frame.addView(contextLine,new LinearLayout.LayoutParams(-1,0,3));frame.addView(branchOne,new LinearLayout.LayoutParams(-1,0,2));frame.addView(branchTwo,new LinearLayout.LayoutParams(-1,0,2));
        axisHint=text("",15,Color.rgb(35,78,43));root.addView(axisHint);
        speechLanguage=new Spinner(this);speechLanguage.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Auto (phone default)","English","Polish — polski","Russian — русский","Chinese — 中文"}));root.addView(speechLanguage);
        utterance=new EditText(this);utterance.setHint("What the microphone heard");utterance.setMinLines(2);utterance.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);utterance.setText("I changed careers because I wanted something meaningful");root.addView(utterance);
        LinearLayout turns=row();Button other=button("Hear them");Button me=button("I said it");turns.addView(other,weight());turns.addView(me,weight());root.addView(turns);
        LinearLayout ring=row();Button up=button("Scroll ↑");Button press=button("R1 press");Button down=button("Scroll ↓");ring.addView(up,weight());ring.addView(press,weight());ring.addView(down,weight());root.addView(ring);
        microphone=button("Start microphone");root.addView(microphone);
        Button stop=button("Stop / resume");root.addView(stop);
        root.addView(text("Live provider log",18,Color.rgb(24,38,27)));ScrollView logScroll=new ScrollView(this);liveLog=text("Waiting for provider traffic…",12,Color.rgb(170,255,176));liveLog.setTypeface(android.graphics.Typeface.MONOSPACE);liveLog.setPadding(dp(10),dp(10),dp(10),dp(10));logScroll.setBackgroundColor(Color.rgb(2,7,3));logScroll.addView(liveLog);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(260));lp.setMargins(0,dp(6),0,0);root.addView(logScroll,lp);
        other.setOnClickListener(v->ingest(false));me.setOnClickListener(v->ingest(true));up.setOnClickListener(v->scrollTone(-1));down.setOnClickListener(v->scrollTone(1));press.setOnClickListener(v->{axis=(axis+1)%axes.length;render();});stop.setOnClickListener(v->{listening=!listening;render();});microphone.setOnClickListener(v->toggleMicrophone());
        speechLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){language=languageCode();lastSuggestedSpeech="";if(microphoneActive&&recognizer!=null){recognizer.cancel();handler.postDelayed(()->beginRecognition(),250);}render();}public void onNothingSelected(AdapterView<?> p){}});
        return scroll;
    }

    private void toggleMicrophone(){
        if(microphoneActive){stopMicrophone();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION);return;}
        startMicrophone();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==MICROPHONE_PERMISSION&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startMicrophone();else{axisHint.setText("Microphone permission is required for live simulation");}}
    private void startMicrophone(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){axisHint.setText("Speech recognition is unavailable on this phone");return;}
        microphoneActive=true;microphone.setText("Stop microphone");
        if(recognizer==null){recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){axisHint.setText("Listening");}
            public void onBeginningOfSpeech(){} public void onRmsChanged(float rms){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
            public void onError(int error){if(microphoneActive)handler.postDelayed(()->beginRecognition(),500);}
            public void onResults(Bundle b){handler.removeCallbacks(settledSpeech);String value=best(b);if(value!=null){invalidateActiveRequest();utterance.setText(value);commitSpeech(value,false);}pendingSpeech="";if(microphoneActive)handler.postDelayed(()->beginRecognition(),250);}
            public void onPartialResults(Bundle b){String value=best(b);if(value!=null){pendingSpeech=value.trim();utterance.setText(value);invalidateActiveRequest();handler.removeCallbacks(settledSpeech);handler.postDelayed(settledSpeech,SPEECH_SETTLE_MS);}}
            public void onEvent(int type,Bundle b){}
        });}
        beginRecognition();
    }
    private void beginRecognition(){
        if(!microphoneActive||recognizer==null)return;Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);String tag=languageTag();if(tag!=null){intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,tag);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,tag);}recognizer.startListening(intent);
    }
    private String best(Bundle bundle){ArrayList<String> values=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return values==null||values.isEmpty()?null:values.get(0);}
    private void stopMicrophone(){microphoneActive=false;handler.removeCallbacksAndMessages(null);if(recognizer!=null)recognizer.cancel();microphone.setText("Start microphone");render();}
    private void invalidateActiveRequest(){desiredGeneration++;queuedRefresh=false;java.net.HttpURLConnection connection=activeConnection;if(connection!=null){activeConnection=null;connection.disconnect();}}

    private void ingest(boolean wearer){String heard=utterance.getText().toString().trim();if(!heard.isEmpty())commitSpeech(heard,wearer);}
    private void commitSpeech(String heard,boolean wearer){
        if(!listening||heard.isEmpty())return;
        conversation.add(heard);while(conversation.size()>12)conversation.remove(0);pendingSpeech="";
        updateSuggestions(rollingText(""),wearer);lastSuggestedSpeech=heard;
    }
    private void refreshLiveSuggestions(){
        if(!listening||pendingSpeech.isEmpty()||pendingSpeech.equals(lastSuggestedSpeech))return;
        updateSuggestions(rollingText(pendingSpeech),false);
    }
    private void updateContext(String heard,boolean wearer){String selected=languageCode();if(!wearer)language="AUTO".equals(selected)?detect(heard):selected;context=englishContext(heard,language,wearer);}
    private void updateSuggestions(String heard,boolean wearer){
        updateContext(heard,wearer);branches=new ArrayList<>();render();requestSuggestions(heard);
    }
    private void requestSuggestions(String heard){
        if(heard.trim().isEmpty())return;long generation=++desiredGeneration;if(requestInFlight){queuedRefresh=true;return;}requestInFlight=true;lastSuggestedSpeech=pendingSpeech.isEmpty()?heard:pendingSpeech;Provider provider=settings.activeProvider();
        final String prompt="CONTROL VARIABLES: language="+languageCode()+"; persona="+(playful?"P":"S")+"; stance="+(inquisitive?"I":"D")+"; risk="+risk+"/7.\nConversation transcript (oldest to newest):\n"+heard+"\n\nInfer the active language only when language=AUTO. Return JSON: {\"language\":\"two-letter code\",\"context\":\"English translation/synopsis of the last 20 words\",\"branches\":[\"...\",\"...\"]}. Generate two short, organic next things I could say that respond specifically to the whole conversation. P=playful, S=strategic/trust-building, I=questions, D=statements. Risk controls social boldness, never coercion. If speech is not English, branches must contain only easy English phonetic spellings of replies in that language, not native script. Each branch must be one complete sentence of at most 52 characters so it fits one locked lens line.";
        appendLog("→ #"+generation+" "+provider.label+" / "+settings.model(provider)+"\n  "+(playful?"P":"S")+" "+(inquisitive?"I":"D")+" "+risk+"/7 · "+heard);
        network.execute(()->{try{ProviderClient.Cancellation control=new ProviderClient.Cancellation(){public boolean cancelled(){return desiredGeneration!=generation;}public void connected(java.net.HttpURLConnection connection){if(!cancelled())activeConnection=connection;else connection.disconnect();}};ProviderClient.Suggestions result=ProviderClient.suggest(provider,settings.url(provider),settings.model(provider),settings.key(provider),prompt,this::appendLog,control);runOnUiThread(()->{activeConnection=null;requestInFlight=false;if(generation==desiredGeneration){language=result.language;if(!"EN".equals(language))context=result.context;branches=result.branches;appendLog("← #"+generation+" COMPLETE "+result.language+"\n  "+result.context+"\n  "+android.text.TextUtils.join(" | ",result.branches));render();}else appendLog("× #"+generation+" discarded as stale");runQueuedRefresh();});}catch(Exception e){runOnUiThread(()->{activeConnection=null;requestInFlight=false;if(generation!=desiredGeneration||"SUPERSEDED".equals(e.getMessage()))appendLog("× #"+generation+" cancelled for newer speech");else{appendLog("! #"+generation+" ERROR "+provider.label+": "+e.getMessage());axisHint.setText(provider.label+": "+e.getMessage());}runQueuedRefresh();});}});
    }
    private void runQueuedRefresh(){if(queuedRefresh||(!pendingSpeech.isEmpty()&&!pendingSpeech.equals(lastSuggestedSpeech))){queuedRefresh=false;String latest=rollingText(pendingSpeech);if(!latest.isEmpty())requestSuggestions(latest);}}
    private String rollingText(String partial){StringBuilder out=new StringBuilder();for(String turn:conversation){if(out.length()>0)out.append(' ');out.append(turn);}if(!partial.isEmpty()){if(out.length()>0)out.append(' ');out.append(partial);}return out.toString();}
    private String languageCode(){if(speechLanguage==null)return"AUTO";switch(speechLanguage.getSelectedItemPosition()){case 1:return"EN";case 2:return"PL";case 3:return"RU";case 4:return"ZH";default:return"AUTO";}}
    private String languageTag(){switch(languageCode()){case"EN":return"en-US";case"PL":return"pl-PL";case"RU":return"ru-RU";case"ZH":return"zh-CN";default:return null;}}
    private void scrollTone(int direction){
        if(axis==0)playful=!playful;else if(axis==1)inquisitive=!inquisitive;else risk=Math.max(1,Math.min(7,risk+direction));String heard=rollingText(pendingSpeech);if(!heard.isEmpty())updateSuggestions(heard,false);else render();
    }
    private void render(){
        String p=playful?"P":"S",s=inquisitive?"I":"D",r=String.valueOf(risk);if(axis==0)p="["+p+"]";if(axis==1)s="["+s+"]";if(axis==2)r="["+r+"]";
        String status=language+" "+p+" "+s+" "+r;contextLine.setText(context);branchOne.setText((listening?status:"PAUSED "+status)+" • "+(branches.size()>0?branches.get(0):"Waiting for a complete suggestion…"));branchTwo.setText(branches.size()>1?"• "+branches.get(1):"");axisHint.setText(microphoneActive?"Listening":"Active: "+axes[axis]+" · lens commits complete response chunks");
    }
    private String detect(String value){if(value.matches(".*[А-Яа-я].*"))return"RU";if(value.matches(".*[\u4E00-\u9FFF].*"))return"ZH";if(value.matches(".*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*"))return"PL";return"EN";}
    private String englishContext(String value,String lang,boolean wearer){String[] words=value.split("\\s+");StringBuilder b=new StringBuilder();for(int i=Math.max(0,words.length-20);i<words.length;i++){if(b.length()>0)b.append(' ');b.append(words[i]);}return"EN".equals(lang)?b.toString():context;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private Button button(String value){Button b=new Button(this);b.setText(value);return b;}
    private TextView lensRegion(int lines){TextView t=text("",18,Color.rgb(145,255,151));t.setTypeface(android.graphics.Typeface.MONOSPACE);t.setMaxLines(lines);t.setGravity(Gravity.CENTER_VERTICAL);t.setAutoSizeTextTypeUniformWithConfiguration(10,18,1,TypedValue.COMPLEX_UNIT_SP);t.setPadding(0,dp(3),0,dp(3));return t;}
    private void appendLog(String message){if(Looper.myLooper()!=Looper.getMainLooper()){runOnUiThread(()->appendLog(message));return;}String current=liveLog.getText().toString();if(current.startsWith("Waiting for"))current="";String next=current+(current.isEmpty()?"":"\n")+message;if(next.length()>12000)next=next.substring(next.length()-12000);liveLog.setText(next);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){stopMicrophone();network.shutdownNow();if(recognizer!=null){recognizer.destroy();recognizer=null;}super.onDestroy();}
}
