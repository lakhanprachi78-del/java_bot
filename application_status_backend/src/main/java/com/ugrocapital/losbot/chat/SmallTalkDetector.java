package com.ugrocapital.losbot.chat;

import java.util.regex.Pattern;

public final class SmallTalkDetector {
    private static final Pattern PURE_SMALLTALK = Pattern.compile(
            "^(hi|hello|hey|thanks|thank you|thx|ok|okay|great|cool|bye|goodbye|good morning|good afternoon|good evening)[!.?, ]*$",
            Pattern.CASE_INSENSITIVE);

    private SmallTalkDetector() {
    }

    public static boolean isPureSmallTalk(String message) {
        return message != null && PURE_SMALLTALK.matcher(message.trim()).matches();
    }
}