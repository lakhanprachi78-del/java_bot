package com.ugro.skaleup.rag;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

/**
 * Local multilingual E5-small embeddings, matching the Python RAG pipeline.
 *
 * Model: intfloat/multilingual-e5-small
 * Device: CPU
 * Document prefix: passage:
 * Query prefix: query:
 *
 * The tokenizer is configured with the same 512-token maximum length
 * used by the Python pipeline.
 */
@Service
public class EmbeddingService {

    private static final String MODEL_NAME = "intfloat/multilingual-e5-small";
    private static final String MODEL_DIR = "models/multilingual-e5-small";

    private static final String MODEL_URL =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/model.onnx?download=true";

    private static final String TOKENIZER_URL =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/tokenizer.json?download=true";

    private static final int MAX_LENGTH = 512;
    private static final int DIMENSION = 384;

    private final Path modelDir;
    private final Object lock = new Object();

    private volatile OrtEnvironment environment;
    private volatile OrtSession session;
    private volatile HuggingFaceTokenizer tokenizer;

    public EmbeddingService() {
        this.modelDir = Path.of(MODEL_DIR);
    }

    /**
     * Embeds documents using the E5 document prefix.
     */
    public List<float[]> embed(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<String> prefixed = texts.stream()
                .map(t -> "passage: " + Objects.requireNonNullElse(t, ""))
                .toList();

        return infer(prefixed);
    }

    /**
     * Embeds a query using the E5 query prefix.
     */
    public float[] embedOne(String text) throws Exception {
        String value = "query: " + Objects.requireNonNullElse(text, "");
        return infer(List.of(value)).get(0);
    }

    private List<float[]> infer(List<String> texts) throws Exception {
        ensureLoaded();

        Encoding[] encodings =
                tokenizer.batchEncode(texts.toArray(String[]::new));

        int batch = encodings.length;

        if (batch == 0) {
            return List.of();
        }

        int seq = Math.min(MAX_LENGTH, encodings[0].getIds().length);

        long[][] inputIds = new long[batch][seq];
        long[][] attention = new long[batch][seq];
        long[][] tokenTypes = new long[batch][seq];

        for (int i = 0; i < batch; i++) {
            long[] ids = encodings[i].getIds();
            long[] mask = encodings[i].getAttentionMask();

            int n = Math.min(seq, ids.length);

            System.arraycopy(ids, 0, inputIds[i], 0, n);
            System.arraycopy(
                    mask,
                    0,
                    attention[i],
                    0,
                    Math.min(seq, mask.length));
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();

        OnnxTensor inputIdsTensor =
                OnnxTensor.createTensor(environment, inputIds);

        OnnxTensor attentionTensor =
                OnnxTensor.createTensor(environment, attention);

        inputs.put("input_ids", inputIdsTensor);
        inputs.put("attention_mask", attentionTensor);

        OnnxTensor tokenTypeTensor = null;

        // Some ONNX exports expose token_type_ids.
        // multilingual-e5-small normally does not require them.
        if (session.getInputNames().contains("token_type_ids")) {
            tokenTypeTensor =
                    OnnxTensor.createTensor(environment, tokenTypes);
            inputs.put("token_type_ids", tokenTypeTensor);
        }

        try (OnnxTensor idsResource = inputIdsTensor;
             OnnxTensor attentionResource = attentionTensor;
             OnnxTensor tokenTypesResource = tokenTypeTensor;
             OrtSession.Result result = session.run(inputs)) {

            Object value = result.get(0).getValue();

            float[][][] hidden = (float[][][]) value;

            List<float[]> output = new ArrayList<>(batch);

            for (int b = 0; b < batch; b++) {

                float[] vector = new float[DIMENSION];
                double count = 0.0;

                for (int t = 0; t < seq; t++) {

                    if (attention[b][t] == 0) {
                        continue;
                    }

                    count += 1.0;

                    for (int d = 0; d < DIMENSION; d++) {
                        vector[d] += hidden[b][t][d];
                    }
                }

                if (count > 0) {
                    for (int d = 0; d < DIMENSION; d++) {
                        vector[d] /= (float) count;
                    }
                }

                normalize(vector);
                output.add(vector);
            }

            return output;
        }
    }

    private void ensureLoaded() throws Exception {

        if (session != null && tokenizer != null) {
            return;
        }

        synchronized (lock) {

            if (session != null && tokenizer != null) {
                return;
            }

            Files.createDirectories(modelDir);

            Path model = modelDir.resolve("model.onnx");
            Path tokenizerFile = modelDir.resolve("tokenizer.json");

            downloadIfMissing(MODEL_URL, model);
            downloadIfMissing(TOKENIZER_URL, tokenizerFile);

            /*
             * DJL 0.36.0 does not expose setMaxLength().
             * Load the tokenizer normally. The inference code below
             * explicitly limits the ONNX input sequence to MAX_LENGTH
             * (512), matching the Python pipeline's maximum length.
             */
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile);

            environment = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions options =
                    new OrtSession.SessionOptions();

            options.setIntraOpNumThreads(
                    Math.max(
                            1,
                            Runtime.getRuntime()
                                    .availableProcessors() / 2));

            session = environment.createSession(
                    model.toString(),
                    options);
        }
    }

    private void downloadIfMissing(
            String url,
            Path target) throws IOException, InterruptedException {

        if (Files.exists(target) && Files.size(target) > 0) {
            return;
        }

        Path temp =
                target.resolveSibling(
                        target.getFileName() + ".download");

        HttpClient client =
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NORMAL)
                        .build();

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .header(
                                "User-Agent",
                                "SkaleUP-SKAI-RAG/1.0")
                        .GET()
                        .build();

        HttpResponse<InputStream> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() / 100 != 2) {
            throw new IOException(
                    "Failed to download "
                            + MODEL_NAME
                            + " asset: HTTP "
                            + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(
                    in,
                    temp,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(
                temp,
                target,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void normalize(float[] v) {

        double n = 0;

        for (float x : v) {
            n += x * x;
        }

        n = Math.sqrt(n);

        if (n > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) n;
            }
        }
    }
}
