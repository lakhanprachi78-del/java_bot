package com.ugrocapital.losbot.auth;

import java.time.LocalTime;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    private final AuthService authService;

    public GreetingService(AuthService authService) {
        this.authService = authService;
    }

    public String getTimeBasedGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    public String buildGreeting(String sessionId) {
        AuthContext auth = authService.getAuthContext(sessionId);
        return "Hey " + authService.displayName(auth) + ", " + getTimeBasedGreeting() + "!";
    }
}