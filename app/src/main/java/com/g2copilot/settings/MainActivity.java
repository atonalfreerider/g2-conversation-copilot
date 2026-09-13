package com.g2copilot.settings;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private SecureSettings settings; private Spinner provider; private EditText apiKey, model, endpoint, words;
    private SeekBar stance, risk; private TextView status, stanceValue, riskValue; private Button test;
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); settings=new SecureSettings(this); setContentView(buildUi()); load(Provider.OPENAI);
    }
    private View buildUi() {
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(28),dp(24),dp(36)); root.setBackgroundColor(Color.rgb(243,246,242)); scroll.addView(root);
        TextView title=text("G2 Conversation Copilot",28); root.addView(title); root.addView(text("Connections and glasses defaults",16));
        provider=new Spinner(this); provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,Provider.values())); root.addView(label("Backend")); root.addView(provider);
        apiKey=input("API key"); apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(apiKey);
        model=input("Model"); endpoint=input("API endpoint"); root.addView(model); root.addView(endpoint);
        LinearLayout actions=new LinearLayout(this); test=button("Test connection"); Button save=button("Save securely"); actions.addView(test); actions.addView(save); root.addView(actions);
        root.addView(label("Glasses defaults")); words=input("Context words"); words.setInputType(InputType.TYPE_CLASS_NUMBER); words.setText("20"); root.addView(words);
        stanceValue=label(""); root.addView(stanceValue); stance=slider(); root.addView(stance);
        riskValue=label(""); root.addView(riskValue); risk=slider(); root.addView(risk);
        Button saveDefaults=button("Save defaults"); root.addView(saveDefaults); status=text("Keys stay encrypted on this phone.",14); status.setPadding(0,dp(18),0,0); root.addView(status);
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onItemSelected(AdapterView<?> p,View v,int pos,long id){load(Provider.values()[pos]);} public void onNothingSelected(AdapterView<?> p){} });
        stance.setOnSeekBarChangeListener(listener(()->updateTone())); risk.setOnSeekBarChangeListener(listener(()->updateTone())); updateTone();
        save.setOnClickListener(v->saveConnection()); test.setOnClickListener(v->testConnection()); saveDefaults.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().putInt("words",number(words,20)).putInt("stance",stance.getProgress()).putInt("risk",risk.getProgress()).apply(); show("Defaults saved");});
        return scroll;
    }
    private void load(Provider p) { try { apiKey.setText(settings.key(p)); model.setText(settings.model(p)); endpoint.setText(settings.url(p)); } catch(Exception e){ show("Could not unlock saved key"); } }
    private void saveConnection() { try { settings.put(selected(),apiKey.getText().toString().trim(),model.getText().toString().trim(),endpoint.getText().toString().trim()); show(selected().label+" saved securely"); } catch(Exception e){ show("Save failed"); } }
    private void testConnection() { saveConnection(); test.setEnabled(false); show("Testing " + selected().label + "…"); network.execute(()->{ try { String result=ProviderClient.test(selected(),endpoint.getText().toString().trim(),model.getText().toString().trim(),apiKey.getText().toString().trim()); runOnUiThread(()->{show(result);test.setEnabled(true);}); } catch(Exception e){runOnUiThread(()->{show(e.getMessage());test.setEnabled(true);});} }); }
    private Provider selected(){return (Provider)provider.getSelectedItem();}
    private void updateTone(){stanceValue.setText("Inquisitive  " + stance.getProgress()+"/7  Declarative");riskValue.setText("Safe  "+risk.getProgress()+"/7  Risky");}
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(24,38,27));v.setPadding(0,dp(5),0,dp(8));return v;}
    private TextView label(String s){TextView v=text(s,15);v.setPadding(0,dp(18),0,dp(5));return v;}
    private EditText input(String hint){EditText v=new EditText(this);v.setHint(hint);v.setSingleLine(true);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private SeekBar slider(){SeekBar b=new SeekBar(this);b.setMax(7);b.setProgress(3);return b;}
    private SeekBar.OnSeekBarChangeListener listener(Runnable r){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){r.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}};}
    private int number(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString());}catch(Exception ignored){return fallback;}}
    private void show(String s){status.setText(s==null?"Connection failed":s);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){network.shutdownNow();super.onDestroy();}
}
