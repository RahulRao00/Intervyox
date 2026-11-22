package com.intervyox.dto;

import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewStatus;
import com.intervyox.model.InterviewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class InterviewDtos {

    public record StartInterviewRequest(
            @NotBlank(message = "Target role is required")
            String targetRole,

            @NotNull(message = "Interview type is required")
            InterviewType interviewType,

            @NotNull(message = "Difficulty is required")
            Difficulty difficulty,

            @Min(value = 1, message = "Question count must be at least 1")
            @Max(value = 15, message = "Question count cannot exceed 15")
            Integer questionCount,

            Boolean useAiGeneration) {}

    public record SubmitAnswerRequest(
            Integer questionOrder,
            Long answerId,

            @NotBlank(message = "Answer cannot be empty")
            String userAnswer) {}

    public record AnswerDetailResponse(
            Long id,
            int questionOrder,
            String questionText,
            String userAnswer,
            BigDecimal aiScore,
            BigDecimal correctnessScore,
            BigDecimal clarityScore,
            BigDecimal completenessScore,
            BigDecimal relevanceScore,
            String feedback,
            List<String> strengths,
            List<String> weaknesses,
            String improvementSuggestions,
            String idealAnswer,
            boolean evaluationFallback,
            LocalDateTime answeredAt) {}

    public record InterviewSummaryResponse(
            Long id,
            String targetRole,
            InterviewType interviewType,
            Difficulty difficulty,
            int totalQuestions,
            int answeredQuestions,
            BigDecimal overallScore,
            InterviewStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            List<AnswerDetailResponse> answers) {}

    public record InterviewListResponse(
            Long id,
            String targetRole,
            InterviewType interviewType,
            Difficulty difficulty,
            int totalQuestions,
            int answeredQuestions,
            BigDecimal overallScore,
            InterviewStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {}
}
