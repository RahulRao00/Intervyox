package com.intervyox.service.ai;

import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;

import java.util.List;

public interface AiProvider {

    AnswerEvaluationResult evaluateAnswer(AnswerEvaluationRequest request);

    List<GeneratedQuestionResult> generateQuestions(
            String targetRole, InterviewType interviewType, Difficulty difficulty, int count);
}
