package com.ugrocapital.appstatus.dto.web;

public class WhoAmIResponse {
    public String username;
    public String role;
    public String displayName;

    public WhoAmIResponse() {
    }

    public WhoAmIResponse(String username, String role, String displayName) {
        this.username = username;
        this.role = role;
        this.displayName = displayName;
    }
}
