package com.ugrocapital.losbot.auth;

public record AuthContext(String username, String role, String databaseOwner) {
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}