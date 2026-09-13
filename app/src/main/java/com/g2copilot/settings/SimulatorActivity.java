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
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SimulatorActivity extends Activity {
    private static final int MICROPHONE_PERMISSION=41;
    private final String[] axes={"persona","stance","risk"};
    private int axis=0, risk=4; private boolean playful=true, inquisitive=true, listening=true;
    private String language="EN", context="Ready"; private List<String> branches=new ArrayList<>();
    private TextView lens, axisHint; private EditText utterance; private Button microphone;
    private SpeechRecognizer recognizer; private boolean microphoneActive=false;
    private final Handler handler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(build());render();}

    private View build(){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(22),dp(20),dp(30));root.setBackgroundColor(Color.rgb(243,246,242));scroll.addView(root);
        TextView title=text("G2 glasses simulator",26,Color.rgb(24,38,27));root.addView(title);
        root.addView(text("No glasses or network required",14,Color.DKGRAY));
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(2,7,3));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(230));fp.setMargins(0,dp(18),0,dp(18));root.addView(frame,fp);
        lens=text("",20,Color.rgb(145,255,151));lens.setTypeface(android.graphics.Typeface.MONOSPACE);lens.setGravity(Gravity.CENTER_VERTICAL);lens.setPadding(dp(24),dp(14),dp(24),dp(14));frame.addView(lens,new FrameLayout.LayoutParams(-1,-1));
        axisHint=text("",15,Color.rgb(35,78,43));root.addView(axisHint);
        utterance=new EditText(this);utterance.setHint("What the microphone heard");utterance.setMinLines(2);utterance.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);utterance.setText("I changed careers because I wanted something meaningful");root.addView(utterance);
        LinearLayout turns=row();Button other=button("Hear them");Button me=button("I said it");turns.addView(other,weight());turns.addView(me,weight());root.addView(turns);
        LinearLayout ring=row();Button up=button("Scroll ↑");Button press=button("R1 press");Button down=button("Scroll ↓");ring.addView(up,weight());ring.addView(press,weight());ring.addView(down,weight());root.addView(ring);
        microphone=button("Start microphone");root.addView(microphone);
        Button stop=button("Stop / resume");root.addView(stop);
        other.setOnClickListener(v->ingest(false));me.setOnClickListener(v->ingest(true));up.setOnClickListener(v->scrollTone(-1));down.setOnClickListener(v->scrollTone(1));press.setOnClickListener(v->{axis=(axis+1)%axes.length;render();});stop.setOnClickListener(v->{listening=!listening;render();});microphone.setOnClickListener(v->toggleMicrophone());
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
            public void onReadyForSpeech(Bundle b){axisHint.setText("Listening · speak naturally");}
            public void onBeginningOfSpeech(){} public void onRmsChanged(float rms){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
            public void onError(int error){if(microphoneActive)handler.postDelayed(()->beginRecognition(),500);}
            public void onResults(Bundle b){String value=best(b);if(value!=null){utterance.setText(value);ingest(false);}if(microphoneActive)handler.postDelayed(()->beginRecognition(),250);}
            public void onPartialResults(Bundle b){String value=best(b);if(value!=null){utterance.setText(value);String detected=detect(value);context=englishContext(value,detected,false);render();}}
            public void onEvent(int type,Bundle b){}
        });}
        beginRecognition();
    }
    private void beginRecognition(){
        if(!microphoneActive||recognizer==null)return;Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);recognizer.startListening(intent);
    }
    private String best(Bundle bundle){ArrayList<String> values=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return values==null||values.isEmpty()?null:values.get(0);}
    private void stopMicrophone(){microphoneActive=false;handler.removeCallbacksAndMessages(null);if(recognizer!=null)recognizer.cancel();microphone.setText("Start microphone");render();}

    private void ingest(boolean wearer){
        if(!listening)return;String heard=utterance.getText().toString().trim();if(heard.isEmpty())return;
        if(!wearer)language=detect(heard);context=englishContext(heard,language,wearer);
        if(!"EN".equals(language))branches=Arrays.asList("mock: tell me more","mock: that sounds wonderful");
        else if(inquisitive)branches=playful?Arrays.asList("What’s the fun version?","And then what happened?"):Arrays.asList("What matters most to you?","What would help you feel understood?");
        else branches=playful?Arrays.asList("Okay, that has a story.",risk>=6?"Here’s the bold take.":"I’m with you."):Arrays.asList("That sounds important to you.","I see where you’re coming from.");render();
    }
    private void scrollTone(int direction){
        if(axis==0)playful=!playful;else if(axis==1)inquisitive=!inquisitive;else risk=Math.max(1,Math.min(7,risk+direction));render();
    }
    private void render(){
        String p=playful?"P":"S",s=inquisitive?"I":"D",r=String.valueOf(risk);if(axis==0)p="["+p+"]";if(axis==1)s="["+s+"]";if(axis==2)r="["+r+"]";
        String status=language+" "+p+" "+s+" "+r;StringBuilder out=new StringBuilder(context);for(int i=0;i<branches.size();i++)out.append("\n").append(i==0?status+" ":"").append("• ").append(branches.get(i));if(!listening)out.append("\nPAUSED");lens.setText(out);axisHint.setText("Active: "+axes[axis]+" · press cycles P/S → I/D → risk 1–7");
    }
    private String detect(String value){if(value.matches(".*[А-Яа-я].*"))return"RU";if(value.matches(".*[\u4E00-\u9FFF].*"))return"ZH";if(value.matches(".*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*"))return"PL";return"EN";}
    private String englishContext(String value,String lang,boolean wearer){String[] words=value.split("\\s+");StringBuilder b=new StringBuilder();for(int i=Math.max(0,words.length-20);i<words.length;i++){if(b.length()>0)b.append(' ');b.append(words[i]);}if("EN".equals(lang))return b.toString();return wearer?"YOU SAID: "+b:"THEY SAID: [mock English translation]";}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private Button button(String value){Button b=new Button(this);b.setText(value);return b;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){stopMicrophone();if(recognizer!=null){recognizer.destroy();recognizer=null;}super.onDestroy();}
}
