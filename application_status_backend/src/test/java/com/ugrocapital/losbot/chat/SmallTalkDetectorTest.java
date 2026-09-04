package com.ugrocapital.losbot.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmallTalkDetectorTest {
    @Test
    void detectsOnlyStandaloneSmallTalk() {
        assertTrue(SmallTalkDetector.isPureSmallTalk("  Thanks! "));
        assertTrue(SmallTalkDetector.isPureSmallTalk("good morning"));
        assertFalse(SmallTalkDetector.isPureSmallTalk("Show my approved applications"));
        assertFalse(SmallTalkDetector.isPureSmallTalk(null));
    }
}