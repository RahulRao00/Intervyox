package com.intervyox.service.ai;

import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;

public record GeneratedQuestionResult(
        String questionText,
        String idealAnswerOutline,
        Difficulty difficulty,
        InterviewType interviewType) {}
