package com.ugrocapital.appstatus.dto.web;

public class ChatResponse {
    public String reply;
    public String sessionId;
    public Boolean hasMore;   // null when pagination doesn't apply (e.g. single lookup)
    public Long queryId;      // the query_log.db row this turn landed on; null if logging failed

    public ChatResponse() {
    }

    public ChatResponse(String reply, String sessionId, Boolean hasMore, Long queryId) {
        this.reply = reply;
        this.sessionId = sessionId;
        this.hasMore = hasMore;
        this.queryId = queryId;
    }
}
