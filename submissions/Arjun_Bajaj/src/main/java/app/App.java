package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class App {
    private static final String HF_TOKEN_ENV = "HF_TOKEN";
    private static final String SUMMARIZATION_MODEL =
            "https://api-inference.huggingface.co/models/sshleifer/distilbart-cnn-12-6";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient CLIENT = new OkHttpClient();

    public static void main(String[] args) throws Exception {
        String inputPath = args.length > 0 ? args[0] : "sample_input.txt";
        String text = Files.readString(Path.of(inputPath));

        String summary = summarize(text, 50, 140);
        List<String> actions = extractActions(text);

        System.out.println("\n=== SUMMARY ===\n");
        System.out.println(summary == null || summary.isBlank() ? "(no summary)" : summary);

        System.out.println("\n=== ACTION ITEMS ===\n");
        if (actions.isEmpty()) {
            System.out.println("(none detected)");
        } else {
            for (int i = 0; i < actions.size(); i++) {
                System.out.println((i + 1) + ". " + actions.get(i));
            }
        }
    }

    private static String summarize(String text, int minLen, int maxLen) throws IOException {
        String token = System.getenv(HF_TOKEN_ENV);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "Missing HF token. Set env var HF_TOKEN to a Hugging Face access token.");
        }
        List<String> chunks = chunk(text, 900);
        List<String> partials = new ArrayList<>();
        for (String c : chunks) {
            String part = callHuggingFace(token, c, minLen, maxLen);
            if (part != null && !part.isBlank()) partials.add(part);
        }
        if (partials.isEmpty()) return "";
        if (partials.size() == 1) return partials.get(0);
        String joined = String.join(" ", partials);
        return callHuggingFace(token, joined, minLen, maxLen);
    }

    private static String callHuggingFace(String token, String text, int minLen, int maxLen) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("inputs", text);
        Map<String, Object> params = new HashMap<>();
        params.put("min_length", minLen);
        params.put("max_length", maxLen);
        params.put("do_sample", false);
        payload.put("parameters", params);

        Request request = new Request.Builder()
                .url(SUMMARIZATION_MODEL)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(
                        MAPPER.writeValueAsBytes(payload),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new IOException("HF API error: " + response.code() + " - " + body);
            }
            String body = response.body().string();
            JsonNode node = MAPPER.readTree(body);
            if (node.isArray() && node.size() > 0 && node.get(0).has("summary_text")) {
                return node.get(0).get("summary_text").asText();
            }
            if (node.has("error")) return node.get("error").asText();
            return "";
        }
    }

    private static List<String> chunk(String text, int approxChars) {
        List<String> words = Arrays.asList(text.split("\\s+"));
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (cur.length() + w.length() + 1 > approxChars) {
                if (cur.length() > 0) {
                    chunks.add(cur.toString());
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }

    private static final List<String> ACTION_PREFIXES = Arrays.asList(
            "todo", "to-do", "next:", "action:", "follow up", "follow-up",
            "we should", "let's", "let us", "assign", "schedule", "prepare",
            "implement", "fix", "investigate", "review", "email", "draft",
            "submit", "finish", "complete", "update", "create", "deploy",
            "test", "document", "refactor", "meet", "ping", "reach out"
    );

    private static List<String> extractActions(String text) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(s -> s.replaceAll("^[\\s\\-•\\t]+", "").trim())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        List<String> actions = new ArrayList<>();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean prefix = ACTION_PREFIXES.stream().anyMatch(p ->
                    lower.startsWith(p) || lower.contains(" " + p));
            boolean checkbox = lower.startsWith("[ ]") || lower.startsWith("[x]") ||
                               lower.startsWith("- [ ]") || lower.startsWith("- [x]");
            if (prefix || checkbox) actions.add(line);
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(actions);
        return new ArrayList<>(set);
    }
}
