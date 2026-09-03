package com.ugro.skaleup.rag;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size; import java.util.*;
public final class ApiModels { private ApiModels(){}
 public record ChatRequest(@NotBlank String session_id,@NotBlank String message,boolean is_handoff){}
 public record ChatResponse(String reply,String session_id,Boolean has_more,Integer query_id){}
 public record RagChatRequest(@NotBlank String message,String session_id,boolean is_handoff){}
 public record ChatSource(String name,Object page,String snippet){}
 public record RagChatResponse(String answer,List<ChatSource> sources,Integer query_id){}
 public record DirectChatRequest(@NotBlank String session_id,@NotBlank String field,@NotBlank String value,int offset){}
 public record WhoAmIResponse(String username,String role,String display_name){}
 public record FeedbackRequest(@NotNull Integer query_id,@NotNull Boolean positive){}
 public record FeedbackDetailRequest(Integer query_id,String query_text,String answer_text,@NotNull Boolean positive,List<String> tags,@Size(max=2000) String comment){}
 public record FeedbackResponse(boolean success){}
 public record FeedbackDetailResponse(boolean success,Integer id){}
 public record AdminLoginRequest(@NotBlank String password){}
 public record AdminLoginResponse(String token){}
 public record DocsResponse(List<String> files){}
 public record RetrainResponse(int chunks,String message){}
 public record UploadResponse(int saved,String message){}
}
