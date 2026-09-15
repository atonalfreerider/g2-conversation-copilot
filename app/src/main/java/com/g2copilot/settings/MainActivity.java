package com.g2copilot.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private SecureSettings settings; private Spinner provider, persona; private EditText apiKey, model, endpoint, words, biography;
    private TextView status; private Button test; private Switch nativeCharacters;
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); settings=new SecureSettings(this); setContentView(buildUi());Provider active=settings.activeProvider();provider.setSelection(active.ordinal());load(active);
    }
    private View buildUi() {
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(28),dp(24),dp(36)); root.setBackgroundColor(Color.rgb(243,246,242)); scroll.addView(root);
        TextView title=text("G2 Conversation Copilot",28); root.addView(title); root.addView(text("Connections and glasses defaults",16));
        provider=new Spinner(this); provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,Provider.values())); root.addView(label("Backend")); root.addView(provider);
        apiKey=input("API key"); apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(apiKey);
        model=input("Model"); endpoint=input("API endpoint"); root.addView(model); root.addView(endpoint);
        LinearLayout actions=new LinearLayout(this); test=button("Test connection"); Button save=button("Save securely"); actions.addView(test); actions.addView(save); root.addView(actions);
        root.addView(label("Glasses defaults")); words=input("Context words"); words.setInputType(InputType.TYPE_CLASS_NUMBER); words.setText("20"); root.addView(words);
        root.addView(label("Speaker biography"));biography=input("Describe yourself, your voice, interests, and point of view");biography.setSingleLine(false);biography.setMinLines(3);biography.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);biography.setText(getSharedPreferences("profile",MODE_PRIVATE).getString("biography",""));root.addView(biography);
        biography.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){getSharedPreferences("profile",MODE_PRIVATE).edit().putString("biography",s.toString()).apply();}public void afterTextChanged(Editable e){}});
        root.addView(label("Conversation style")); persona=choice("Playful","Strategic / trust-building"); root.addView(persona);
        nativeCharacters=new Switch(this);nativeCharacters.setText("Show native characters instead of phonetic English");nativeCharacters.setChecked(getSharedPreferences("profile",MODE_PRIVATE).getBoolean("native_characters",false));root.addView(nativeCharacters);
        Button saveDefaults=button("Save defaults"); root.addView(saveDefaults); status=text("Keys stay encrypted on this phone.",14); status.setPadding(0,dp(18),0,0); root.addView(status);
        Button simulator=button("Open glasses simulator"); root.addView(simulator);
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onItemSelected(AdapterView<?> p,View v,int pos,long id){Provider chosen=Provider.values()[pos];settings.setActiveProvider(chosen);load(chosen);boolean cloud=chosen!=Provider.GEMINI_NANO;apiKey.setEnabled(cloud);model.setEnabled(cloud);endpoint.setEnabled(cloud);test.setText(cloud?"Test connection":"Check Gemini Nano");} public void onNothingSelected(AdapterView<?> p){} });
        nativeCharacters.setOnCheckedChangeListener((v,checked)->getSharedPreferences("profile",MODE_PRIVATE).edit().putBoolean("native_characters",checked).apply());
        save.setOnClickListener(v->saveConnection()); test.setOnClickListener(v->testConnection()); saveDefaults.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().putInt("words",number(words,20)).putString("persona",persona.getSelectedItemPosition()==0?"P":"S").apply();getSharedPreferences("profile",MODE_PRIVATE).edit().putString("biography",biography.getText().toString().trim()).putBoolean("native_characters",nativeCharacters.isChecked()).apply();show("Defaults and biography saved on phone");});
        simulator.setOnClickListener(v->startActivity(new Intent(this,SimulatorActivity.class)));
        return scroll;
    }
    private void load(Provider p) { try { apiKey.setText(settings.key(p)); model.setText(settings.model(p)); endpoint.setText(settings.url(p)); } catch(Exception e){ show("Could not unlock saved key"); } }
    private void saveConnection() { try { settings.put(selected(),apiKey.getText().toString().trim(),model.getText().toString().trim(),endpoint.getText().toString().trim()); show(selected().label+" saved securely"); } catch(Exception e){ show("Save failed"); } }
    private void testConnection() { saveConnection(); test.setEnabled(false); show("Testing " + selected().label + "…"); network.execute(()->{ try { String result;if(selected()==Provider.GEMINI_NANO){OnDeviceGeminiClient.suggest("CONTROL VARIABLES: language=EN. Return only JSON with language EN, context Gemini Nano ready, and eight branch objects containing english plus empty native and phonetic fields.",null);result="Gemini Nano ready · no API key";}else result=ProviderClient.test(selected(),endpoint.getText().toString().trim(),model.getText().toString().trim(),apiKey.getText().toString().trim());runOnUiThread(()->{show(result);test.setEnabled(true);}); } catch(Exception e){runOnUiThread(()->{show(e.getMessage());test.setEnabled(true);});} }); }
    private Provider selected(){return (Provider)provider.getSelectedItem();}
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(24,38,27));v.setPadding(0,dp(5),0,dp(8));return v;}
    private TextView label(String s){TextView v=text(s,15);v.setPadding(0,dp(18),0,dp(5));return v;}
    private EditText input(String hint){EditText v=new EditText(this);v.setHint(hint);v.setSingleLine(true);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private Spinner choice(String first,String second){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{first,second}));return s;}
    private int number(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString());}catch(Exception ignored){return fallback;}}
    private void show(String s){status.setText(s==null?"Connection failed":s);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){network.shutdownNow();super.onDestroy();}
}
