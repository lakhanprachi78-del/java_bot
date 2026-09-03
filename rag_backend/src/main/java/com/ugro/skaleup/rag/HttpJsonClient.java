package com.ugro.skaleup.rag;
import com.fasterxml.jackson.databind.*; import org.springframework.stereotype.Component; import java.net.URI; import java.net.http.*; import java.time.Duration; import java.util.*;
@Component public class HttpJsonClient { private final ObjectMapper mapper=new ObjectMapper();
 public JsonNode post(String url,String key,Object body) throws Exception {HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/json");if(key!=null&&!key.isBlank())b.header("Authorization","Bearer "+key);HttpResponse<String> r=HttpClient.newHttpClient().send(b.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()/100!=2)throw new IllegalStateException("HTTP "+r.statusCode()+": "+r.body());return mapper.readTree(r.body());}
 public ObjectMapper mapper(){return mapper;}
}
