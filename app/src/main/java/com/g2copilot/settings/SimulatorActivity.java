package com.g2copilot.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SimulatorActivity extends Activity {
    private static final int MICROPHONE_PERMISSION=41;
    private static final long SPEECH_SETTLE_MS=650;
    private static final long PROVIDER_DEADLINE_MS=30_000;
    private int focus=0, viewport=0; private boolean playful=true, listening=true, showNative=false, queuedForceAll=false;
    private String language="EN", context="Ready"; private final List<Branch> branches=new ArrayList<>();
    private TextView contextLine, branchOne, branchTwo, axisHint, backendStatus, liveLog, transcriptLog; private EditText utterance, instantPhrase; private Button microphone; private Spinner speechLanguage, backend;
    private TextToSpeech tts; private ConversationMemory memory;
    private SpeechRecognizer recognizer; private boolean microphoneActive=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor(); private SecureSettings settings; private boolean requestInFlight=false, queuedRefresh=false; private volatile long desiredGeneration=0; private volatile java.net.HttpURLConnection activeConnection;
    private final List<String> conversation=new ArrayList<>();
    private final List<String> speechTags=new ArrayList<>();
    private String pendingSpeech="", lastSuggestedSpeech="", queuedInstant="";
    private final Runnable settledSpeech=()->refreshLiveSuggestions();
    private long requestStartedAt=0; private volatile long timedOutGeneration=-1; private Runnable providerDeadline;
    private final Runnable elapsedUpdate=new Runnable(){public void run(){if(requestInFlight){long seconds=(System.currentTimeMillis()-requestStartedAt)/1000;backendStatus.setText("Provider working · "+seconds+"s elapsed · 30s deadline");handler.postDelayed(this,1000);}}};

    @Override public void onCreate(Bundle state){super.onCreate(state);settings=new SecureSettings(this);String bio=getSharedPreferences("profile",MODE_PRIVATE).getString("biography","");showNative=getSharedPreferences("profile",MODE_PRIVATE).getBoolean("native_characters",false);memory=new ConversationMemory(this,bio);tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS)tts.setLanguage(Locale.US);});setContentView(build());updateTranscript();render();}

    private View build(){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(22),dp(20),dp(30));root.setBackgroundColor(Color.rgb(243,246,242));scroll.addView(root);
        TextView title=text("G2 glasses simulator",26,Color.rgb(24,38,27));root.addView(title);
        root.addView(text("Uses your configured provider for live branches",14,Color.DKGRAY));
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setGravity(Gravity.CENTER_VERTICAL);frame.setPadding(dp(24),dp(14),dp(24),dp(14));frame.setBackgroundColor(Color.rgb(2,7,3));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(230));fp.setMargins(0,dp(18),0,dp(18));root.addView(frame,fp);
        contextLine=lensRegion(3);branchOne=lensRegion(2);branchTwo=lensRegion(2);frame.addView(contextLine,new LinearLayout.LayoutParams(-1,0,3));frame.addView(branchOne,new LinearLayout.LayoutParams(-1,0,2));frame.addView(branchTwo,new LinearLayout.LayoutParams(-1,0,2));
        axisHint=text("",15,Color.rgb(35,78,43));root.addView(axisHint);
        backendStatus=text("Provider idle",14,Color.rgb(35,78,43));root.addView(backendStatus);
        backend=new Spinner(this);backend.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,Provider.values()));backend.setSelection(settings.activeProvider().ordinal());root.addView(backend);
        speechLanguage=new Spinner(this);setSpeechLanguages(fallbackLanguageTags());root.addView(speechLanguage);loadRecognizerLanguages();
        utterance=new EditText(this);utterance.setHint("What the microphone heard");utterance.setMinLines(2);utterance.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);utterance.setText("I changed careers because I wanted something meaningful");root.addView(utterance);
        LinearLayout turns=row();Button other=button("Hear them");Button me=button("I said it");turns.addView(other,weight());turns.addView(me,weight());root.addView(turns);
        LinearLayout ring=row();Button up=button("Scroll ↑");Button press=button("R1 refresh");Button down=button("Scroll ↓");ring.addView(up,weight());ring.addView(press,weight());ring.addView(down,weight());root.addView(ring);Button legacySpeak=button("Speak selected (legacy)");root.addView(legacySpeak);
        microphone=button("Start microphone");root.addView(microphone);
        Button stop=button("Stop / resume");root.addView(stop);
        instantPhrase=new EditText(this);instantPhrase.setHint("Type English phrase for instant translation");instantPhrase.setSingleLine(true);root.addView(instantPhrase);Button translate=button("Translate & pin");root.addView(translate);
        Button breakpoint=button("Conversation breakpoint");root.addView(breakpoint);
        root.addView(text("Transcript",18,Color.rgb(24,38,27)));ScrollView transcriptScroll=new ScrollView(this);transcriptLog=text("",13,Color.rgb(24,38,27));transcriptLog.setPadding(dp(8),dp(8),dp(8),dp(8));transcriptScroll.addView(transcriptLog);root.addView(transcriptScroll,new LinearLayout.LayoutParams(-1,dp(220)));
        root.addView(text("Live provider log",18,Color.rgb(24,38,27)));ScrollView logScroll=new ScrollView(this);liveLog=text("Waiting for provider traffic…",12,Color.rgb(170,255,176));liveLog.setTypeface(android.graphics.Typeface.MONOSPACE);liveLog.setPadding(dp(10),dp(10),dp(10),dp(10));logScroll.setBackgroundColor(Color.rgb(2,7,3));logScroll.addView(liveLog);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(260));lp.setMargins(0,dp(6),0,0);root.addView(logScroll,lp);
        other.setOnClickListener(v->ingest(false));me.setOnClickListener(v->ingest(true));up.setOnClickListener(v->moveFocus(-1));down.setOnClickListener(v->moveFocus(1));press.setOnClickListener(v->refreshAllBranches());legacySpeak.setOnClickListener(v->speakSelected());stop.setOnClickListener(v->{listening=!listening;render();});microphone.setOnClickListener(v->toggleMicrophone());translate.setOnClickListener(v->submitInstantTranslation());breakpoint.setOnClickListener(v->insertBreakpoint());
        speechLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){language=languageCode();getSharedPreferences("profile",MODE_PRIVATE).edit().putString("language_tag",languageTag()).apply();lastSuggestedSpeech="";if(microphoneActive&&recognizer!=null){recognizer.cancel();handler.postDelayed(()->beginRecognition(),250);}if(!"EN".equals(language))requestSuggestions("Provide the eight-slot greeting and conversation risk matrix for this language.","",true);render();}public void onNothingSelected(AdapterView<?> p){}});
        backend.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){Provider selected=Provider.values()[pos];if(settings.activeProvider()!=selected){settings.setActiveProvider(selected);invalidateActiveRequest();String heard=rollingText(pendingSpeech);if(!heard.isEmpty())updateSuggestions(heard,false);appendLog("BACKEND → "+selected.label);}}public void onNothingSelected(AdapterView<?> p){}});
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
            public void onResults(Bundle b){handler.removeCallbacks(settledSpeech);String value=best(b);if(value!=null){markSpeechChanged();utterance.setText(value);commitSpeech(value,false);}pendingSpeech="";if(microphoneActive)handler.postDelayed(()->beginRecognition(),250);}
            public void onPartialResults(Bundle b){String value=best(b);if(value!=null){pendingSpeech=value.trim();utterance.setText(value);markSpeechChanged();handler.removeCallbacks(settledSpeech);handler.postDelayed(settledSpeech,SPEECH_SETTLE_MS);}}
            public void onEvent(int type,Bundle b){}
        });}
        beginRecognition();
    }
    private void beginRecognition(){
        if(!microphoneActive||recognizer==null)return;Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);String tag=languageTag();if(tag!=null){intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,tag);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,tag);}recognizer.startListening(intent);
    }
    private String best(Bundle bundle){ArrayList<String> values=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return values==null||values.isEmpty()?null:values.get(0);}
    private void stopMicrophone(){microphoneActive=false;invalidateActiveRequest();handler.removeCallbacksAndMessages(null);if(recognizer!=null)recognizer.cancel();microphone.setText("Start microphone");render();}
    private void invalidateActiveRequest(){desiredGeneration++;queuedRefresh=false;java.net.HttpURLConnection connection=activeConnection;if(connection!=null){activeConnection=null;connection.disconnect();}}
    private void markSpeechChanged(){desiredGeneration++;if(requestInFlight)queuedRefresh=true;}

    private void ingest(boolean wearer){String heard=utterance.getText().toString().trim();if(!heard.isEmpty())commitSpeech(heard,wearer);}
    private void commitSpeech(String heard,boolean wearer){
        if(!listening||heard.isEmpty())return;
        memory.append(wearer,heard);updateTranscript();
        conversation.add(heard);while(conversation.size()>12)conversation.remove(0);pendingSpeech="";
        updateContext(heard,wearer);render();if(wearer&&!"EN".equals(languageCode()))requestSuggestions(rollingText(""),heard);else requestSuggestions(rollingText(""),"");lastSuggestedSpeech=heard;
    }
    private void refreshLiveSuggestions(){
        if(!listening||pendingSpeech.isEmpty()||pendingSpeech.equals(lastSuggestedSpeech))return;
        updateSuggestions(rollingText(pendingSpeech),false);
    }
    private void updateContext(String heard,boolean wearer){String selected=languageCode();if(!wearer)language="AUTO".equals(selected)?detect(heard):selected;context=englishContext(heard,language,wearer);}
    private void updateSuggestions(String heard,boolean wearer){
        updateContext(heard,wearer);render();requestSuggestions(heard,"");
    }
    private void requestSuggestions(String heard){requestSuggestions(heard,"",false);}
    private void requestSuggestions(String heard,String instant){requestSuggestions(heard,instant,false);}
    private void requestSuggestions(String heard,String instant,boolean forceAll){
        if(heard.trim().isEmpty())return;long generation=++desiredGeneration;if(requestInFlight){queuedRefresh=true;if(!instant.trim().isEmpty())queuedInstant=instant;queuedForceAll|=forceAll;java.net.HttpURLConnection c=activeConnection;if(c!=null)c.disconnect();return;}requestInFlight=true;lastSuggestedSpeech=pendingSpeech.isEmpty()?heard:pendingSpeech;Provider provider=settings.activeProvider();
        String biography=getSharedPreferences("profile",MODE_PRIVATE).getString("biography","");final String prompt=PromptBuilder.build(languageCode(),playful,true,1,biography,memory.compressed()+"\nLive: "+heard,instant);final boolean pin=!instant.trim().isEmpty();
        appendLog("→ #"+generation+" "+provider.label+" / "+settings.model(provider)+"\n  eight-branch risk matrix · "+heard);startProviderMonitor(generation,provider);
        network.execute(()->{try{ProviderClient.Cancellation control=new ProviderClient.Cancellation(){public boolean cancelled(){return desiredGeneration!=generation;}public void connected(java.net.HttpURLConnection connection){if(!cancelled())activeConnection=connection;else connection.disconnect();}};ProviderClient.LogSink sink=message->{appendLog(message);if(message.startsWith("PHASE "))setBackendStatus(message.substring(6));else if(message.startsWith("SSE ←"))setBackendStatus("xAI streaming response");};ProviderClient.Suggestions result=provider==Provider.GEMINI_NANO?OnDeviceGeminiClient.suggest(prompt,sink):ProviderClient.suggest(provider,settings.url(provider),settings.model(provider),settings.key(provider),prompt,sink,control);runOnUiThread(()->{stopProviderMonitor();activeConnection=null;requestInFlight=false;if(generation==desiredGeneration){language=languageCode();if(!"EN".equals(language))context=result.context;if(pin)pinQueue(result.branches);else if(forceAll)hardReplaceQueue(result.branches);else replaceQueue(result.branches);appendLog("← #"+generation+" COMPLETE "+language+"\n  "+result.context+"\n  "+android.text.TextUtils.join(" | ",result.branches));backendStatus.setText(provider.label+" complete");render();}else appendLog("× #"+generation+" discarded as stale");runQueuedRefresh();});}catch(Exception e){runOnUiThread(()->{stopProviderMonitor();activeConnection=null;requestInFlight=false;if(generation==timedOutGeneration)backendStatus.setText(provider.label+" timed out after 30s");else if(generation!=desiredGeneration||"SUPERSEDED".equals(e.getMessage())){appendLog("× #"+generation+" cancelled for newer speech");backendStatus.setText("Superseded; sending newest speech");}else{appendLog("! #"+generation+" ERROR "+provider.label+": "+e.getMessage());backendStatus.setText(provider.label+" error: "+e.getMessage());}runQueuedRefresh();});}});
    }
    private void startProviderMonitor(long generation,Provider provider){requestStartedAt=System.currentTimeMillis();timedOutGeneration=-1;handler.removeCallbacks(elapsedUpdate);handler.post(elapsedUpdate);providerDeadline=()->{if(requestInFlight&&generation==desiredGeneration){timedOutGeneration=generation;appendLog("! #"+generation+" HARD TIMEOUT after 30s");backendStatus.setText(provider.label+" timed out after 30s");desiredGeneration++;java.net.HttpURLConnection connection=activeConnection;if(connection!=null)connection.disconnect();}};handler.postDelayed(providerDeadline,PROVIDER_DEADLINE_MS);}
    private void stopProviderMonitor(){handler.removeCallbacks(elapsedUpdate);if(providerDeadline!=null)handler.removeCallbacks(providerDeadline);}
    private void setBackendStatus(String value){runOnUiThread(()->backendStatus.setText(value));}
    private void runQueuedRefresh(){if(queuedRefresh||(!pendingSpeech.isEmpty()&&!pendingSpeech.equals(lastSuggestedSpeech))){queuedRefresh=false;String latest=rollingText(pendingSpeech);String instant=queuedInstant;boolean force=queuedForceAll;queuedInstant="";queuedForceAll=false;if(!latest.isEmpty())requestSuggestions(latest,instant,force);}}
    private String rollingText(String partial){StringBuilder out=new StringBuilder();for(String turn:conversation){if(out.length()>0)out.append(' ');out.append(turn);}if(!partial.isEmpty()){if(out.length()>0)out.append(' ');out.append(partial);}return out.toString();}
    private String languageCode(){return Locale.forLanguageTag(languageTag()).getLanguage().toUpperCase(Locale.ROOT);}
    private String languageTag(){int position=speechLanguage==null?0:speechLanguage.getSelectedItemPosition();return speechTags.isEmpty()?"en-US":speechTags.get(Math.max(0,Math.min(position,speechTags.size()-1)));}
    private List<String> fallbackLanguageTags(){return Arrays.asList("en-US","es-ES","es-US","fr-FR","fr-CA","de-DE","pl-PL","ru-RU","zh-CN","zh-TW","bn-BD","bg-BG","cs-CZ","da-DK","nl-NL","fi-FI","hi-IN","hu-HU","id-ID","xh-ZA","it-IT","ja-JP","kn-IN","km-KH","rw-RW","ko-KR","ml-IN","mr-IN","pt-BR","tn-ZA","st-ZA","ss-ZA","sv-SE","ta-IN","te-IN","tr-TR","ts-ZA");}
    private void setSpeechLanguages(List<String> tags){String selected=getSharedPreferences("profile",MODE_PRIVATE).getString("language_tag","en-US");LinkedHashSet<String> filtered=new LinkedHashSet<>();filtered.add("en-US");for(String tag:tags){Locale locale=Locale.forLanguageTag(tag);if(!"en".equalsIgnoreCase(locale.getLanguage()))filtered.add(tag);}speechTags.clear();speechTags.addAll(filtered);List<String> labels=new ArrayList<>();for(String tag:speechTags){Locale locale=Locale.forLanguageTag(tag);labels.add(locale.getDisplayName()+" — "+tag);}speechLanguage.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));int index=speechTags.indexOf(selected);speechLanguage.setSelection(index>=0?index:0);}
    private void loadRecognizerLanguages(){Intent details=new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);sendOrderedBroadcast(details,null,new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){Bundle extras=getResultExtras(true);ArrayList<String> supported=extras==null?null:extras.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);if(supported!=null&&!supported.isEmpty())setSpeechLanguages(supported);}},null,Activity.RESULT_OK,null,null);}
    private void moveFocus(int direction){if(branches.isEmpty())return;focus=Math.max(0,Math.min(branches.size()-1,focus+direction));if(focus<viewport)viewport=focus;if(focus>viewport+1)viewport=focus-1;viewport=Math.max(0,Math.min(Math.max(0,branches.size()-2),viewport));render();}
    private void refreshAllBranches(){String heard=rollingText(pendingSpeech);if(heard.isEmpty())heard=memory.compressed();requestSuggestions(heard,"",true);}
    private void speakSelected(){if(focus<branches.size()&&tts!=null){tts.setLanguage(Locale.forLanguageTag(languageTag()));tts.speak(branches.get(focus).speech(!"EN".equals(language)),TextToSpeech.QUEUE_FLUSH,null,"branch-"+focus);}}
    private void hardReplaceQueue(List<Branch> next){branches.clear();branches.addAll(next.subList(0,Math.min(8,next.size())));focus=Math.min(focus,Math.max(0,branches.size()-1));viewport=Math.max(0,Math.min(focus,Math.max(0,branches.size()-2)));}
    private void replaceQueue(List<Branch> next){if(branches.isEmpty()){branches.addAll(next.subList(0,Math.min(8,next.size())));focus=0;viewport=0;return;}int selected=Math.min(focus,branches.size()-1);for(int i=0;i<Math.min(8,next.size());i++)if(Math.abs(i-selected)>1){if(i<branches.size())branches.set(i,next.get(i));else branches.add(next.get(i));}while(branches.size()>8)branches.remove(branches.size()-1);}
    private void pinBranch(Branch branch){branches.removeIf(b->b.english.equals(branch.english));branches.add(0,branch);while(branches.size()>8)branches.remove(branches.size()-1);viewport=0;focus=0;}
    private void pinQueue(List<Branch> incoming){Branch pinned=incoming.get(0);List<Branch> old=new ArrayList<>(branches);branches.clear();branches.add(pinned);for(Branch b:old)if(branches.size()<8&&!b.english.equals(pinned.english))branches.add(b);for(int i=1;i<incoming.size()&&branches.size()<8;i++)if(!incoming.get(i).english.equals(pinned.english))branches.add(incoming.get(i));viewport=0;focus=0;}
    private void submitInstantTranslation(){String phrase=instantPhrase.getText().toString().trim();if(phrase.isEmpty())return;memory.append(true,phrase);updateTranscript();instantPhrase.setText("");if("EN".equals(languageCode()))pinBranch(new Branch(phrase,""));else requestSuggestions(rollingText(""),phrase);render();}
    private void insertBreakpoint(){invalidateActiveRequest();conversation.clear();branches.clear();pendingSpeech="";lastSuggestedSpeech="";context="Ready";focus=0;viewport=0;memory.breakpoint();updateTranscript();render();}
    private void updateTranscript(){if(transcriptLog!=null)transcriptLog.setText(memory.transcript());}
    private void render(){
        contextLine.setText(context);branchOne.setText(renderBranch(viewport));branchTwo.setText(renderBranch(viewport+1));axisHint.setText((microphoneActive?"Listening · ":"")+"Response "+(branches.isEmpty()?0:focus+1)+" of "+branches.size()+" · scroll selects, press refreshes all");
    }
    private String renderBranch(int index){if(index>=branches.size())return index==0?"Waiting for complete suggestions…":"";return (focus==index?"> ":"• ")+branches.get(index).display(!"EN".equals(language),showNative);}
    private String detect(String value){if(value.matches(".*[А-Яа-я].*"))return"RU";if(value.matches(".*[\u4E00-\u9FFF].*"))return"ZH";if(value.matches(".*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*"))return"PL";return"EN";}
    private String englishContext(String value,String lang,boolean wearer){String[] words=value.split("\\s+");StringBuilder b=new StringBuilder();for(int i=Math.max(0,words.length-20);i<words.length;i++){if(b.length()>0)b.append(' ');b.append(words[i]);}return"EN".equals(lang)?b.toString():context;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private Button button(String value){Button b=new Button(this);b.setText(value);return b;}
    private TextView lensRegion(int lines){TextView t=text("",18,Color.rgb(145,255,151));t.setTypeface(android.graphics.Typeface.MONOSPACE);t.setMaxLines(lines);t.setGravity(Gravity.CENTER_VERTICAL);t.setAutoSizeTextTypeUniformWithConfiguration(10,18,1,TypedValue.COMPLEX_UNIT_SP);t.setPadding(0,dp(3),0,dp(3));return t;}
    private void appendLog(String message){if(Looper.myLooper()!=Looper.getMainLooper()){runOnUiThread(()->appendLog(message));return;}String current=liveLog.getText().toString();if(current.startsWith("Waiting for"))current="";String next=current+(current.isEmpty()?"":"\n")+message;if(next.length()>12000)next=next.substring(next.length()-12000);liveLog.setText(next);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){stopMicrophone();network.shutdownNow();if(tts!=null)tts.shutdown();if(recognizer!=null){recognizer.destroy();recognizer=null;}super.onDestroy();}
    @Override protected void onResume(){super.onResume();showNative=getSharedPreferences("profile",MODE_PRIVATE).getBoolean("native_characters",false);if(contextLine!=null)render();}
}
