package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Called when the LOS agent decides a message isn't about application
 * data (it called the not_in_scope tool). Silently forwards the SAME
 * (cleaned) message to the SkaleUp RAG backend and returns its answer
 * as-is — the user never sees that a handoff happened, just the final
 * reply. Direct port of main.py's _handoff_to_skaleup.
 */
@Service
public class HandoffService {

    private static final Logger log = LoggerFactory.getLogger(HandoffService.class);

    public static final String NO_SCOPE_ANYWHERE_MESSAGE =
            "That doesn't look like an application-status question or a general " +
                    "SkaleUp question, so I'm not able to help with it here. Please check " +
                    "your internal documentation or team lead for that.";

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public HandoffService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * is_handoff=true tells the RAG backend not to route this back to us
     * if IT also can't answer, which would otherwise ping-pong forever.
     */
    @SuppressWarnings("unchecked")
    public String handoffToSkaleUp(String sessionId, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", message);
            body.put("session_id", sessionId);
            body.put("is_handoff", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<String, Object> data = restTemplate.postForObject(appProperties.getRagChatUrl(), request, Map.class);

            if (data != null) {
                Object answer = data.get("answer");
                if (answer != null && !answer.toString().isBlank()) {
                    return answer.toString();
                }
            }
        } catch (Exception e) {
            log.warn("HANDOFF ERROR (LOS -> SkaleUp): {}", e.getMessage());
        }
        return NO_SCOPE_ANYWHERE_MESSAGE;
    }
}
