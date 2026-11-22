package com.intervyox.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiAiProvider(
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${ai.timeout-seconds:20}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "gemini-2.5-flash";
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AnswerEvaluationResult evaluateAnswer(AnswerEvaluationRequest request) {
        if (apiKey.isBlank()) {
            log.info("Gemini API key is not configured. Using deterministic fallback evaluation.");
            return buildFallbackEvaluation(request);
        }

        try {
            String prompt = buildEvaluationPrompt(request);
            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "responseMimeType", "application/json"
                    )
            );

            String responseBody = restClient.post()
                    .uri(GEMINI_BASE_URL + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            return parseEvaluationResponse(responseBody, request);
        } catch (Exception e) {
            log.warn("Gemini AI evaluation failed or timed out ({}). Using fallback evaluation.", e.getMessage());
            return buildFallbackEvaluation(request);
        }
    }

    @Override
    public List<GeneratedQuestionResult> generateQuestions(
            String targetRole, InterviewType interviewType, Difficulty difficulty, int count) {
        if (apiKey.isBlank()) {
            log.info("Gemini API key is not configured. Generating fallback questions.");
            return buildFallbackQuestions(targetRole, interviewType, difficulty, count);
        }

        try {
            String prompt = buildQuestionGenerationPrompt(targetRole, interviewType, difficulty, count);
            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.3,
                            "responseMimeType", "application/json"
                    )
            );

            String responseBody = restClient.post()
                    .uri(GEMINI_BASE_URL + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            return parseQuestionGenerationResponse(responseBody, targetRole, interviewType, difficulty, count);
        } catch (Exception e) {
            log.warn("Gemini question generation failed ({}). Using fallback questions.", e.getMessage());
            return buildFallbackQuestions(targetRole, interviewType, difficulty, count);
        }
    }

    private String buildEvaluationPrompt(AnswerEvaluationRequest req) {
        return """
                You are a senior engineering and HR interviewer evaluating a candidate's answer.
                Role: %s
                Interview Type: %s
                Difficulty: %s
                Question: %s
                Ideal Answer Outline: %s
                Candidate's Answer: %s

                Evaluate the candidate's answer rigorously on a scale of 0.0 to 10.0 for:
                - correctness: technical or conceptual accuracy
                - clarity: communication, structure, conciseness
                - completeness: depth, coverage of essential points
                - relevance: directly addressing the question asked
                - overall: weighted average reflecting readiness for the role

                Return ONLY a valid raw JSON object matching this exact schema:
                {
                  "overallScore": 8.0,
                  "correctnessScore": 8.5,
                  "clarityScore": 8.0,
                  "completenessScore": 7.5,
                  "relevanceScore": 8.0,
                  "feedback": "Comprehensive summary of performance...",
                  "strengths": ["Clear explanation of...", "Good practical example of..."],
                  "weaknesses": ["Missed discussing...", "Could be more concise regarding..."],
                  "improvementSuggestions": "Concrete recommendations to improve...",
                  "idealAnswer": "A well-structured model answer demonstrating excellence..."
                }
                """.formatted(
                req.targetRole(),
                req.interviewType(),
                req.difficulty(),
                req.questionText(),
                req.idealAnswerOutline() != null ? req.idealAnswerOutline() : "Standard best practices",
                req.userAnswer()
        );
    }

    private AnswerEvaluationResult parseEvaluationResponse(String responseJson, AnswerEvaluationRequest request) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            String rawText = textNode.asText("");

            String cleanedJson = stripCodeFences(rawText);
            JsonNode evalNode = objectMapper.readTree(cleanedJson);

            BigDecimal correctness = clampScore(evalNode.path("correctnessScore").asDouble(7.0));
            BigDecimal clarity = clampScore(evalNode.path("clarityScore").asDouble(7.0));
            BigDecimal completeness = clampScore(evalNode.path("completenessScore").asDouble(7.0));
            BigDecimal relevance = clampScore(evalNode.path("relevanceScore").asDouble(7.0));

            BigDecimal overall;
            if (evalNode.has("overallScore") && !evalNode.get("overallScore").isNull()) {
                overall = clampScore(evalNode.get("overallScore").asDouble());
            } else {
                overall = correctness.add(clarity).add(completeness).add(relevance)
                        .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
            }

            String feedback = evalNode.path("feedback").asText("Evaluation completed.");
            List<String> strengths = extractStringList(evalNode.path("strengths"));
            List<String> weaknesses = extractStringList(evalNode.path("weaknesses"));
            String improvementSuggestions = evalNode.path("improvementSuggestions")
                    .asText("Practice structuring responses using the STAR method or clear point-by-point outlines.");
            String idealAnswer = evalNode.path("idealAnswer")
                    .asText(request.idealAnswerOutline() != null ? request.idealAnswerOutline() : "Clear and structured answer.");

            return new AnswerEvaluationResult(
                    overall, correctness, clarity, completeness, relevance,
                    feedback, strengths, weaknesses, improvementSuggestions, idealAnswer, false
            );
        } catch (Exception e) {
            log.warn("Failed to parse Gemini evaluation JSON response: {}. Falling back.", e.getMessage());
            return buildFallbackEvaluation(request);
        }
    }

    private String buildQuestionGenerationPrompt(String role, InterviewType type, Difficulty difficulty, int count) {
        return """
                You are a senior hiring manager. Generate exactly %d unique, realistic interview questions for:
                Role: %s
                Interview Type: %s
                Difficulty: %s

                Return ONLY a valid JSON array of objects with keys:
                - "questionText": The question
                - "idealAnswerOutline": Key points that a good answer should cover

                Example schema:
                [
                  {
                    "questionText": "...",
                    "idealAnswerOutline": "1. ... 2. ... 3. ..."
                  }
                ]
                """.formatted(count, role, type, difficulty);
    }

    private List<GeneratedQuestionResult> parseQuestionGenerationResponse(
            String responseJson, String role, InterviewType type, Difficulty diff, int count) {
        List<GeneratedQuestionResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            String cleanedJson = stripCodeFences(textNode.asText(""));
            JsonNode arrayNode = objectMapper.readTree(cleanedJson);

            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    String qText = item.path("questionText").asText("");
                    String outline = item.path("idealAnswerOutline").asText("");
                    if (!qText.isBlank()) {
                        results.add(new GeneratedQuestionResult(qText, outline, diff, type));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse generated questions: {}. Using fallback questions.", e.getMessage());
        }

        if (results.size() < count) {
            List<GeneratedQuestionResult> fallbacks = buildFallbackQuestions(role, type, diff, count - results.size());
            results.addAll(fallbacks);
        }
        return results;
    }

    public AnswerEvaluationResult buildFallbackEvaluation(AnswerEvaluationRequest req) {
        String answer = req.userAnswer() != null ? req.userAnswer().trim() : "";
        int wordCount = answer.isEmpty() ? 0 : answer.split("\\s+").length;

        double baseScore;
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();

        if (wordCount < 15) {
            baseScore = 4.0;
            weaknesses.add("Answer is very brief; lacks depth and necessary technical detail.");
            weaknesses.add("Does not sufficiently explain key concepts.");
            strengths.add("Direct and concise initial statement.");
        } else if (wordCount < 50) {
            baseScore = 6.5;
            strengths.add("Addresses the primary question with relevant concepts.");
            strengths.add("Clear and intelligible phrasing.");
            weaknesses.add("Could elaborate further on real-world edge cases and trade-offs.");
        } else {
            baseScore = 8.0;
            strengths.add("Detailed and structured explanation covering multiple facets of the topic.");
            strengths.add("Good depth and relevant terminology.");
            weaknesses.add("Consider summarizing key takeaways up front to improve impact.");
        }

        BigDecimal correctness = clampScore(baseScore + 0.2);
        BigDecimal clarity = clampScore(baseScore);
        BigDecimal completeness = clampScore(baseScore - 0.2);
        BigDecimal relevance = clampScore(baseScore + 0.1);
        BigDecimal overall = correctness.add(clarity).add(completeness).add(relevance)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

        String feedback = "Evaluation generated using system rubrics (Word count: " + wordCount + "). " +
                (baseScore >= 7.0 ? "Solid attempt demonstrating working knowledge." : "Needs more technical depth and structured rationale.");

        String improvement = "Structure your answer using a clear framework: state the core concept, provide a practical example or trade-off, and conclude with the business impact.";
        String ideal = req.idealAnswerOutline() != null && !req.idealAnswerOutline().isBlank()
                ? req.idealAnswerOutline()
                : "A high-scoring answer explains the core principle, compares trade-offs, and provides a real-world scenario.";

        return new AnswerEvaluationResult(
                overall, correctness, clarity, completeness, relevance,
                feedback, strengths, weaknesses, improvement, ideal, true
        );
    }

    public List<GeneratedQuestionResult> buildFallbackQuestions(
            String targetRole, InterviewType interviewType, Difficulty difficulty, int count) {
        List<GeneratedQuestionResult> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String qText = String.format("In the context of %s (%s, %s), how do you approach system reliability, maintainability, and quality assurance in your day-to-day work?",
                    targetRole, interviewType, difficulty);
            String outline = "1. Design for maintainability and automated testing. 2. Observability, metrics, and logging. 3. Code review, CI/CD, and regression mitigation.";
            list.add(new GeneratedQuestionResult(qText, outline, difficulty, interviewType));
        }
        return list;
    }

    public static String stripCodeFences(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public static BigDecimal clampScore(double score) {
        double clamped = Math.max(0.0, Math.min(10.0, score));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> extractStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    list.add(item.asText().trim());
                }
            }
        }
        if (list.isEmpty()) {
            list.add("Good contextual understanding.");
        }
        return list;
    }
}
