package com.ugro.skaleup.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins="*")
public class RagController {
    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService rag;
    private final QueryCacheService cache;
    private final Path data;
    private final String password;
    private final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());

    public RagController(
            RagService r,
            QueryCacheService c,
            @Value("${RAG_DATA_DIR:./data}") String d,
            @Value("${ADMIN_PASSWORD:ugro-admin}") String p) {
        rag = r;
        cache = c;
        data = Path.of(d);
        password = p;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "rag-knowledge-api");
    }

    @PostMapping("/chat")
    public ApiModels.RagChatResponse chat(
            @RequestBody ApiModels.RagChatRequest r) throws Exception {
        return rag.chat(r.message().trim(), r.session_id(), r.is_handoff());
    }

    @PostMapping("/feedback")
    public ApiModels.FeedbackResponse feedback(
            @RequestBody ApiModels.FeedbackRequest r) {
        boolean ok = cache.recordFeedback(r.query_id(), r.positive());
        return new ApiModels.FeedbackResponse(ok);
    }

    @PostMapping("/feedback/detail")
    public ApiModels.FeedbackDetailResponse feedbackDetail(
            @RequestBody ApiModels.FeedbackDetailRequest r) {
        if (r.query_id() != null) {
            cache.recordFeedback(r.query_id(), r.positive());
        }
        log.info("Feedback detail — query_id={}, positive={}, tags={}, comment=\"{}\"",
                r.query_id(), r.positive(), r.tags(), r.comment());
        return new ApiModels.FeedbackDetailResponse(true, r.query_id());
    }

    @PostMapping("/admin/login")
    public ApiModels.AdminLoginResponse login(
            @RequestBody ApiModels.AdminLoginRequest r) {
        if (!Objects.equals(r.password(), password)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Incorrect admin password.");
        }

        String t = UUID.randomUUID().toString();
        tokens.add(t);
        return new ApiModels.AdminLoginResponse(t);
    }

    @PostMapping("/admin/logout")
    public Map<String, Boolean> logout(
            @RequestHeader(value = "Authorization", required = false) String a) {
        if (a != null) {
            tokens.remove(a.replaceFirst("^Bearer ", ""));
        }
        return Map.of("ok", true);
    }

    @GetMapping("/admin/docs")
    public ApiModels.DocsResponse docs(
            @RequestHeader("Authorization") String a) throws IOException {
        auth(a);
        Files.createDirectories(data);

        try (var s = Files.list(data)) {
            return new ApiModels.DocsResponse(
                    s.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                     .map(p -> p.getFileName().toString())
                     .sorted()
                     .toList());
        }
    }

    @PostMapping("/admin/upload")
    public ApiModels.UploadResponse upload(
            @RequestHeader("Authorization") String a,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        auth(a);
        Files.createDirectories(data);

        int n = 0;
        for (MultipartFile f : files) {
            String name = Path.of(
                    Objects.requireNonNullElse(f.getOriginalFilename(), ""))
                    .getFileName().toString();

            if (!name.toLowerCase().endsWith(".pdf")) {
                continue;
            }

            Files.copy(
                    f.getInputStream(),
                    data.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
            n++;
        }

        return new ApiModels.UploadResponse(n, "PDF files uploaded.");
    }

    @DeleteMapping("/admin/docs/{filename}")
    public Map<String, Boolean> delete(
            @RequestHeader("Authorization") String a,
            @PathVariable String filename) throws IOException {
        auth(a);

        return Map.of(
                "deleted",
                Files.deleteIfExists(
                        data.resolve(Path.of(filename).getFileName())));
    }

    @PostMapping("/admin/retrain")
    public ApiModels.RetrainResponse retrain(
            @RequestHeader("Authorization") String a) throws Exception {
        auth(a);

        int n = rag.retrain();
        return new ApiModels.RetrainResponse(
                n,
                "Java vector index rebuilt from PDF documents.");
    }

    private void auth(String a) {
        if (a == null ||
            !tokens.contains(a.replaceFirst("^Bearer ", ""))) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin authorization required.");
        }
    }
}