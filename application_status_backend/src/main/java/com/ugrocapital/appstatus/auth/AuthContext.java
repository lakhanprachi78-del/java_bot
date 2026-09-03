package com.ugrocapital.appstatus.auth;

/**
 * Resolved identity for the current request. "role" is "user" or "admin" —
 * "user" can only see applications they created OR currently own
 * (createdby == username OR lstupdatedby == username); "admin" sees
 * everything. Mirrors auth.py's AuthContext dataclass exactly.
 */
public record AuthContext(String username, String role) {
}
