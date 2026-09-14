package com.g2copilot.settings;

final class Branch {
    final String english, phonetic;
    Branch(String english,String phonetic){this.english=english==null?"":english.trim();this.phonetic=phonetic==null?"":phonetic.trim();}
    String display(boolean foreign){return foreign?english+"\n"+phonetic:english;}
    String speech(boolean foreign){return foreign?phonetic:english;}
    @Override public String toString(){return english+(phonetic.isEmpty()?"":" / "+phonetic);}
}
