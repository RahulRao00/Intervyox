package com.intervyox.service.ai;

import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;

public record AnswerEvaluationRequest(
        String questionText,
        String idealAnswerOutline,
        String userAnswer,
        String targetRole,
        InterviewType interviewType,
        Difficulty difficulty) {}
