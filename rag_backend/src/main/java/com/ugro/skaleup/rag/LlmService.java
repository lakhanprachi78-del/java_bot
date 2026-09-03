package com.ugro.skaleup.rag;
import com.fasterxml.jackson.databind.JsonNode; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import java.util.*;
@Service public class LlmService {
 private static final Logger log = LoggerFactory.getLogger(LlmService.class);
 private final HttpJsonClient http;private final String key,base,model;public LlmService(HttpJsonClient h,@Value("${LLM_API_KEY:}")String k,@Value("${LLM_BASE_URL}")String b,@Value("${LLM_MODEL}")String m){http=h;key=k;base=b;model=m;}
 public String answer(String system,String user)throws Exception{if(key==null||key.isBlank())return "LLM API key is not configured. Please set LLM_API_KEY (or GEMINI_API_KEY/DEEPSEEK_API_KEY).";Map<String,Object>body=new LinkedHashMap<>();body.put("model",model);body.put("temperature",0.1);body.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",user)));JsonNode r=http.post(base.replaceAll("/$","")+"/chat/completions",key,body);logUsage(r);return r.path("choices").path(0).path("message").path("content").asText("").trim();}
 private void logUsage(JsonNode r){JsonNode u=r.path("usage");if(u.isMissingNode())return;log.info("LLM token usage — prompt: {}, completion: {}, total: {}",u.path("prompt_tokens").asInt(-1),u.path("completion_tokens").asInt(-1),u.path("total_tokens").asInt(-1));}
}
