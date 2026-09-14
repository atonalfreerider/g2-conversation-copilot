package com.g2copilot.settings;

import static org.junit.Assert.*;
import org.junit.Test;

public final class PromptBuilderTest {
    @Test public void riskSevenIsActuallyBold(){String p=PromptBuilder.build("EN",true,true,7,"Direct photographer","hello");assertTrue(p.contains("MAXIMUM SOCIAL BOLDNESS"));assertTrue(p.contains("Do not retreat to generic empathy"));}
    @Test public void biographyShapesPointOfView(){assertTrue(PromptBuilder.build("EN",true,true,4,"Chicago photographer","hello").contains("SPEAKER BIOGRAPHY: Chicago photographer"));}
    @Test public void translationLabelsAreRemoved(){assertEquals("Good morning",OutputSanitizer.cleanTranslation("User said: Good morning — a Polish friendly greeting"));}
    @Test public void polishGreetingIsStupidlyReadable(){assertEquals("TEEN DOE Bray",OutputSanitizer.cleanPhonetic("Dzień dobry"));assertEquals("TEEN DOE Bray",OutputSanitizer.cleanPhonetic("Dzien Dobry"));}
    @Test public void foreignBranchContractHasEnglishAndPhonetic(){String p=PromptBuilder.build("PL",true,true,4,"Traveler","Dzień dobry");assertTrue(p.contains("{english, phonetic}"));assertTrue(p.contains("english is the clean English meaning"));assertTrue(p.contains("exactly SIX"));}
    @Test public void instantTranslationIsPinned(){String p=PromptBuilder.build("PL",true,true,4,"Traveler","hello","Where is the station?");assertTrue(p.contains("Branch 1 MUST"));assertTrue(p.contains("Where is the station?"));}
}
