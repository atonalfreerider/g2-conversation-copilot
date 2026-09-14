package com.g2copilot.settings;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

final class ConversationMemory {
    private final SharedPreferences prefs;private final List<JSONObject> turns=new ArrayList<>();private final Map<String,Integer> speakers=new LinkedHashMap<>();private String primary="Me",other="Other";private boolean awaitingName=false;
    ConversationMemory(Context context,String biography){prefs=context.getSharedPreferences("conversation_memory",Context.MODE_PRIVATE);primary=nameFromBio(biography);load();speakers.putIfAbsent(primary,0);speakers.putIfAbsent(other,0);}
    void append(boolean wearer,String text){String speaker=wearer?primary:other;if(wearer&&text.toLowerCase().matches(".*what(?:'s| is) your name.*"))awaitingName=true;else if(!wearer&&awaitingName){String found=responseName(text);if(found!=null){speakers.remove(other);other=found;speaker=found;}awaitingName=false;}speakers.put(speaker,speakers.getOrDefault(speaker,0)+1);try{turns.add(new JSONObject().put("speaker",speaker).put("text",text).put("time",System.currentTimeMillis()));}catch(Exception ignored){}save();}
    void breakpoint(){try{turns.add(new JSONObject().put("breakpoint",true).put("time",System.currentTimeMillis()));}catch(Exception ignored){}awaitingName=false;save();}
    String speakerLine(){StringBuilder b=new StringBuilder("Speakers: ");for(Map.Entry<String,Integer> e:speakers.entrySet()){if(b.length()>10)b.append(" · ");b.append(e.getKey()).append(" (").append(e.getValue()).append(")");}return b.toString();}
    String transcript(){StringBuilder b=new StringBuilder(speakerLine());int start=Math.max(0,turns.size()-30);for(int i=start;i<turns.size();i++){JSONObject t=turns.get(i);if(t.optBoolean("breakpoint")){b.append("\n── BREAKPOINT ──");continue;}b.append("\n").append(t.optString("speaker","?")).append(": ").append(t.optString("text",""));}return b.toString();}
    String compressed(){StringBuilder b=new StringBuilder(speakerLine()).append(". Recent turns: ");int start=Math.max(0,turns.size()-8);for(int i=start;i<turns.size();i++){JSONObject t=turns.get(i);if(!t.optBoolean("breakpoint"))b.append(t.optString("speaker")).append(": ").append(t.optString("text")).append(" | ");}return b.toString();}
    private void load(){try{JSONArray a=new JSONArray(prefs.getString("turns","[]"));for(int i=0;i<a.length();i++)turns.add(a.getJSONObject(i));JSONObject s=new JSONObject(prefs.getString("speakers","{}"));java.util.Iterator<String> keys=s.keys();while(keys.hasNext()){String k=keys.next();speakers.put(k,s.optInt(k));}other=prefs.getString("other",other);}catch(Exception ignored){}}
    private void save(){JSONArray a=new JSONArray();for(JSONObject t:turns)a.put(t);JSONObject s=new JSONObject();for(Map.Entry<String,Integer> e:speakers.entrySet())try{s.put(e.getKey(),e.getValue());}catch(Exception ignored){}prefs.edit().putString("turns",a.toString()).putString("speakers",s.toString()).putString("other",other).apply();}
    private String nameFromBio(String bio){Matcher m=Pattern.compile("(?i)(?:my name is|i am|i'm)\\s+([A-Z][a-z]{1,20})").matcher(bio==null?"":bio);return m.find()?m.group(1):"Me";}
    private String responseName(String text){Matcher m=Pattern.compile("(?i)^(?:i am|i'm|my name is)?\\s*([A-Z][a-z]{1,20})(?:[.! ]|$)").matcher(text.trim());return m.find()?m.group(1):null;}
}
