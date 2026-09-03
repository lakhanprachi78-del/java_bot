package com.ugrocapital.appstatus.auth;

import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Map;

/**
 * Resolves a session_id to a user identity (username + role).
 *
 * THIS IS THE ONLY CLASS THAT WILL CHANGE when you wire up real session
 * management. Right now SESSIONS is a hardcoded map standing in for
 * whatever your real system uses (JWT, server-side session store, SSO
 * token, etc). Every other class in this project only ever deals with an
 * AuthContext object — none of them know or care that the lookup is
 * currently hardcoded. Direct port of auth.py.
 */
@Service
public class AuthService {

    private record SessionEntry(String username, String role) {
    }

    // session_id -> (username, role). Usernames here match the
    // createdby/lstupdatedby values in the real dmcredit data, so you can
    // log in "as" someone who actually owns some real applications.
    private static final Map<String, SessionEntry> SESSIONS = Map.ofEntries(
            Map.entry("test-user", new SessionEntry("prachi.lakhan@ugrocapital.com", "user")),
            Map.entry("session-prachi", new SessionEntry("prachi.lakhan@ugrocapital.com", "user")),
            Map.entry("session-chandan", new SessionEntry("chandan.vishwasrao@ugrocapital.com", "user")),
            Map.entry("session-mrinmoyee", new SessionEntry("mrinmoyee.das@ugrocapital.com", "user")),
            Map.entry("session-arya", new SessionEntry("arya.tiwari@ugrocapital.com", "user")),
            Map.entry("session-sristi", new SessionEntry("sristi.kaushal@ugrocapital.com", "user")),
            Map.entry("session-shubhi", new SessionEntry("shubhi.barman1@ugrocapital.com", "user")),
            Map.entry("session-varun", new SessionEntry("varun.dudani@ugrocapital.com", "user")),
            Map.entry("session-samarth", new SessionEntry("samarth.chavan@ugrocapital.com", "user")),
            Map.entry("session-malhar", new SessionEntry("malhar.nikam@ugrocapital.com", "user")),
            Map.entry("session-admin", new SessionEntry("admin.super", "admin"))
    );

    // Friendly display names for greetings — falls back to deriving one
    // from the username/email if it's not listed here.
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("prachi.lakhan@ugrocapital.com", "Prachi"),
            Map.entry("chandan.vishwasrao@ugrocapital.com", "Chandan"),
            Map.entry("mrinmoyee.das@ugrocapital.com", "Mrinmoyee"),
            Map.entry("arya.tiwari@ugrocapital.com", "Arya"),
            Map.entry("sristi.kaushal@ugrocapital.com", "Sristi"),
            Map.entry("shubhi.barman1@ugrocapital.com", "Shubhi"),
            Map.entry("varun.dudani@ugrocapital.com", "Varun"),
            Map.entry("samarth.chavan@ugrocapital.com", "Samarth"),
            Map.entry("malhar.nikam@ugrocapital.com", "Malhar"),
            Map.entry("admin.super", "Admin")
    );

    /**
     * Resolves a session_id to an AuthContext. Throws UnknownSessionException
     * for anything not recognized.
     */
    public AuthContext getAuthContext(String sessionId) {
        SessionEntry entry = SESSIONS.get(sessionId);
        if (entry == null) {
            throw new UnknownSessionException("Unknown or expired session_id: " + sessionId);
        }
        return new AuthContext(entry.username(), entry.role());
    }

    public String getDisplayName(String sessionId) {
        SessionEntry entry = SESSIONS.get(sessionId);
        if (entry == null) {
            return "there";
        }
        String username = entry.username();
        if (DISPLAY_NAMES.containsKey(username)) {
            return DISPLAY_NAMES.get(username);
        }
        return capitalize(firstLocalPart(username));
    }

    public String getTimeBasedGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    public String buildGreeting(String sessionId) {
        return "Hey " + getDisplayName(sessionId) + ", "
                + getTimeBasedGreeting().toLowerCase() + "! \uD83D\uDC4B";
    }

    /** 'chandan.vishwasrao@ugrocapital.com' -> 'Chandan'. */
    public String displayNameFromUsername(String username) {
        return capitalize(firstLocalPart(username));
    }

    private String firstLocalPart(String username) {
        String localPart = username.split("@", 2)[0];
        String first = localPart.split("\\.", 2)[0];
        return first.isEmpty() ? username : first;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
