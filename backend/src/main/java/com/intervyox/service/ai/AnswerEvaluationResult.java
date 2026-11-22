package com.intervyox.service.ai;

import java.math.BigDecimal;
import java.util.List;

public record AnswerEvaluationResult(
        BigDecimal overallScore,
        BigDecimal correctnessScore,
        BigDecimal clarityScore,
        BigDecimal completenessScore,
        BigDecimal relevanceScore,
        String feedback,
        List<String> strengths,
        List<String> weaknesses,
        String improvementSuggestions,
        String idealAnswer,
        boolean fallbackUsed) {}
