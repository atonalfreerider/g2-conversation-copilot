package com.g2copilot.settings;

final class Branch {
    final String english, phonetic, nativeText;
    Branch(String english,String phonetic){this(english,phonetic,"");}
    Branch(String english,String phonetic,String nativeText){this.english=english==null?"":english.trim();this.phonetic=phonetic==null?"":phonetic.trim();this.nativeText=nativeText==null?"":nativeText.trim();}
    String display(boolean foreign,boolean showNative){return foreign?english+"\n"+(showNative?nativeText:phonetic):english;}
    String speech(boolean foreign){return foreign&&!nativeText.isEmpty()?nativeText:english;}
    @Override public String toString(){return english+(phonetic.isEmpty()?"":" / "+phonetic)+(nativeText.isEmpty()?"":" / "+nativeText);}
}
