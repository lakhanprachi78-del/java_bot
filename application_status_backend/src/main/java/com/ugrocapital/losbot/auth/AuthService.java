package com.ugrocapital.losbot.auth;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private record SessionEntry(String username, String role, String databaseOwner) {
    }

    private static final Map<String, SessionEntry> SESSIONS = Map.of(
            "session-chandan", new SessionEntry("chandan", "user", "chandan.vishwasrao@ugrocapital.com"),
            "session-admin", new SessionEntry("admin", "admin", "admin"));

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "chandan", "Chandan",
            "admin", "Admin");

    public AuthContext getAuthContext(String sessionId) {
        SessionEntry entry = SESSIONS.get(sessionId);
        if (entry == null) {
            throw new UnknownSessionException(sessionId);
        }
        return new AuthContext(entry.username(), entry.role(), entry.databaseOwner());
    }

    public String displayName(AuthContext auth) {
        return DISPLAY_NAMES.getOrDefault(auth.username(), auth.username());
    }
}