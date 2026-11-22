package com.intervyox.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(
            @Value("${ai.openai.api-key:}") String apiKey,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.timeout-seconds:20}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "gpt-4o-mini";
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AnswerEvaluationResult evaluateAnswer(AnswerEvaluationRequest request) {
        if (apiKey.isBlank()) {
            log.info("OpenAI API key is blank. Falling back to deterministic evaluation.");
            return buildFallbackEvaluation(request);
        }

        try {
            String systemPrompt = "You are an expert technical and HR interviewer. Evaluate the candidate's answer and respond strictly in valid JSON format.";
            String userPrompt = """
                    Role: %s
                    Interview Type: %s
                    Difficulty: %s
                    Question: %s
                    Ideal Outline: %s
                    Candidate Answer: %s

                    Evaluate rigorously (0.0 to 10.0) for: correctness, clarity, completeness, relevance, overall.
                    Return ONLY a JSON object:
                    {
                      "overallScore": 8.0,
                      "correctnessScore": 8.0,
                      "clarityScore": 8.0,
                      "completenessScore": 8.0,
                      "relevanceScore": 8.0,
                      "feedback": "...",
                      "strengths": ["..."],
                      "weaknesses": ["..."],
                      "improvementSuggestions": "...",
                      "idealAnswer": "..."
                    }
                    """.formatted(
                    request.targetRole(),
                    request.interviewType(),
                    request.difficulty(),
                    request.questionText(),
                    request.idealAnswerOutline(),
                    request.userAnswer()
            );

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            String response = restClient.post()
                    .uri(OPENAI_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode evalNode = objectMapper.readTree(GeminiAiProvider.stripCodeFences(content));

            BigDecimal correctness = GeminiAiProvider.clampScore(evalNode.path("correctnessScore").asDouble(7.0));
            BigDecimal clarity = GeminiAiProvider.clampScore(evalNode.path("clarityScore").asDouble(7.0));
            BigDecimal completeness = GeminiAiProvider.clampScore(evalNode.path("completenessScore").asDouble(7.0));
            BigDecimal relevance = GeminiAiProvider.clampScore(evalNode.path("relevanceScore").asDouble(7.0));
            BigDecimal overall = evalNode.has("overallScore")
                    ? GeminiAiProvider.clampScore(evalNode.get("overallScore").asDouble())
                    : correctness.add(clarity).add(completeness).add(relevance).divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

            List<String> strengths = extractList(evalNode.path("strengths"));
            List<String> weaknesses = extractList(evalNode.path("weaknesses"));
            String feedback = evalNode.path("feedback").asText("Evaluation completed.");
            String improvement = evalNode.path("improvementSuggestions").asText("Focus on concrete technical examples.");
            String ideal = evalNode.path("idealAnswer").asText(request.idealAnswerOutline() != null ? request.idealAnswerOutline() : "");

            return new AnswerEvaluationResult(
                    overall, correctness, clarity, completeness, relevance,
                    feedback, strengths, weaknesses, improvement, ideal, false
            );
        } catch (Exception e) {
            log.warn("OpenAI evaluation failed ({}). Using fallback.", e.getMessage());
            return buildFallbackEvaluation(request);
        }
    }

    @Override
    public List<GeneratedQuestionResult> generateQuestions(
            String targetRole, InterviewType interviewType, Difficulty difficulty, int count) {
        if (apiKey.isBlank()) {
            return buildFallbackQuestions(targetRole, interviewType, difficulty, count);
        }

        try {
            String prompt = String.format("Generate %d interview questions for %s (%s, %s). Return ONLY a JSON array of objects with keys 'questionText' and 'idealAnswerOutline'.",
                    count, targetRole, interviewType, difficulty);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.3,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are an interviewer. Return JSON only."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String response = restClient.post()
                    .uri(OPENAI_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode arrayNode = objectMapper.readTree(GeminiAiProvider.stripCodeFences(content));

            List<GeneratedQuestionResult> results = new ArrayList<>();
            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    String q = item.path("questionText").asText("");
                    String outline = item.path("idealAnswerOutline").asText("");
                    if (!q.isBlank()) {
                        results.add(new GeneratedQuestionResult(q, outline, difficulty, interviewType));
                    }
                }
            }
            if (results.size() < count) {
                results.addAll(buildFallbackQuestions(targetRole, interviewType, difficulty, count - results.size()));
            }
            return results;
        } catch (Exception e) {
            log.warn("OpenAI question generation failed ({}). Using fallback questions.", e.getMessage());
            return buildFallbackQuestions(targetRole, interviewType, difficulty, count);
        }
    }

    private AnswerEvaluationResult buildFallbackEvaluation(AnswerEvaluationRequest req) {
        GeminiAiProvider gemini = new GeminiAiProvider("", "gemini-2.5-flash", 5, objectMapper);
        return gemini.buildFallbackEvaluation(req);
    }

    private List<GeneratedQuestionResult> buildFallbackQuestions(
            String role, InterviewType type, Difficulty diff, int count) {
        GeminiAiProvider gemini = new GeminiAiProvider("", "gemini-2.5-flash", 5, objectMapper);
        return gemini.buildFallbackQuestions(role, type, diff, count);
    }

    private List<String> extractList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    list.add(item.asText().trim());
                }
            }
        }
        if (list.isEmpty()) {
            list.add("Strong technical foundation.");
        }
        return list;
    }
}
