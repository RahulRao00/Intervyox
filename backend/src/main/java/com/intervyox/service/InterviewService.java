package com.intervyox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intervyox.dto.InterviewDtos.*;
import com.intervyox.model.*;
import com.intervyox.repository.InterviewAnswerRepository;
import com.intervyox.repository.InterviewRepository;
import com.intervyox.repository.QuestionRepository;
import com.intervyox.repository.UserRepository;
import com.intervyox.service.ai.AiProvider;
import com.intervyox.service.ai.AnswerEvaluationRequest;
import com.intervyox.service.ai.AnswerEvaluationResult;
import com.intervyox.service.ai.GeneratedQuestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewRepository interviewRepository;
    private final InterviewAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final QuestionService questionService;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;

    public InterviewService(
            InterviewRepository interviewRepository,
            InterviewAnswerRepository answerRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository,
            QuestionService questionService,
            AiProvider aiProvider,
            ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.questionService = questionService;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSummaryResponse startInterview(Long userId, StartInterviewRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        int questionCount = (req.questionCount() != null && req.questionCount() > 0) ? req.questionCount() : 5;

        List<Question> selectedQuestions = new ArrayList<>();

        if (Boolean.TRUE.equals(req.useAiGeneration())) {
            try {
                List<GeneratedQuestionResult> generated = aiProvider.generateQuestions(
                        req.targetRole(), req.interviewType(), req.difficulty(), questionCount);

                for (GeneratedQuestionResult g : generated) {
                    Question q = new Question(
                            req.targetRole(),
                            g.interviewType() != null ? g.interviewType() : req.interviewType(),
                            g.difficulty() != null ? g.difficulty() : req.difficulty(),
                            g.questionText(),
                            g.idealAnswerOutline(),
                            QuestionSource.AI_GENERATED,
                            user,
                            true
                    );
                    selectedQuestions.add(questionRepository.save(q));
                }
            } catch (Exception e) {
                log.warn("AI generation encountered issue: {}. Falling back to seeded questions.", e.getMessage());
            }
        }

        // If AI generation not requested or returned fewer than needed, fill from database
        if (selectedQuestions.size() < questionCount) {
            int needed = questionCount - selectedQuestions.size();
            List<Question> dbQuestions = questionService.selectQuestionsForInterview(
                    req.targetRole(), req.interviewType(), req.difficulty(), needed);
            for (Question q : dbQuestions) {
                if (!selectedQuestions.contains(q)) {
                    selectedQuestions.add(q);
                }
            }
        }

        // If DB had fewer questions, generate fallback questions
        if (selectedQuestions.size() < questionCount) {
            int needed = questionCount - selectedQuestions.size();
            for (int i = 1; i <= needed; i++) {
                Question fallbackQ = new Question(
                        req.targetRole(),
                        req.interviewType(),
                        req.difficulty(),
                        "Discuss your experience with key principles and architectures relevant to " + req.targetRole() + " (" + req.difficulty() + " level).",
                        "1. Core concept explanation. 2. Real world implementation experience. 3. Handling trade-offs and edge cases.",
                        QuestionSource.SEEDED,
                        null,
                        true
                );
                selectedQuestions.add(questionRepository.save(fallbackQ));
            }
        }

        Interview interview = new Interview(
                user,
                req.targetRole(),
                req.interviewType(),
                req.difficulty(),
                questionCount
        );

        for (int i = 0; i < questionCount && i < selectedQuestions.size(); i++) {
            Question q = selectedQuestions.get(i);
            InterviewAnswer answer = new InterviewAnswer(
                    interview,
                    q,
                    i + 1,
                    q.getQuestionText()
            );
            interview.addAnswer(answer);
        }

        Interview saved = interviewRepository.save(interview);
        return toSummaryResponse(saved);
    }

    @Transactional(readOnly = true)
    public InterviewSummaryResponse getInterview(Long userId, Long interviewId) {
        Interview interview = findAndValidateOwnership(userId, interviewId);
        return toSummaryResponse(interview);
    }

    @Transactional
    public AnswerDetailResponse submitAnswer(Long userId, Long interviewId, SubmitAnswerRequest req) {
        Interview interview = findAndValidateOwnership(userId, interviewId);

        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview is already completed");
        }

        InterviewAnswer targetAnswer;
        if (req.answerId() != null) {
            targetAnswer = answerRepository.findById(req.answerId())
                    .filter(a -> a.getInterview().getId().equals(interviewId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer item not found"));
        } else if (req.questionOrder() != null) {
            targetAnswer = answerRepository.findByInterviewIdAndQuestionOrder(interviewId, req.questionOrder())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question order " + req.questionOrder() + " not found"));
        } else {
            // Find first unanswered
            targetAnswer = interview.getAnswers().stream()
                    .filter(a -> a.getUserAnswer() == null || a.getUserAnswer().isBlank())
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "All questions have already been answered"));
        }

        String idealOutline = targetAnswer.getQuestion() != null ? targetAnswer.getQuestion().getIdealAnswerOutline() : null;

        AnswerEvaluationRequest evalReq = new AnswerEvaluationRequest(
                targetAnswer.getQuestionTextSnapshot(),
                idealOutline,
                req.userAnswer(),
                interview.getTargetRole(),
                interview.getInterviewType(),
                interview.getDifficulty()
        );

        AnswerEvaluationResult evalResult = aiProvider.evaluateAnswer(evalReq);

        targetAnswer.setUserAnswer(req.userAnswer());
        targetAnswer.setAiScore(evalResult.overallScore());
        targetAnswer.setCorrectnessScore(evalResult.correctnessScore());
        targetAnswer.setClarityScore(evalResult.clarityScore());
        targetAnswer.setCompletenessScore(evalResult.completenessScore());
        targetAnswer.setRelevanceScore(evalResult.relevanceScore());
        targetAnswer.setFeedback(evalResult.feedback());
        targetAnswer.setImprovementSuggestions(evalResult.improvementSuggestions());
        targetAnswer.setIdealAnswer(evalResult.idealAnswer());
        targetAnswer.setEvaluationFallback(evalResult.fallbackUsed());
        targetAnswer.setAnsweredAt(LocalDateTime.now());

        try {
            targetAnswer.setStrengthsJson(objectMapper.writeValueAsString(evalResult.strengths()));
            targetAnswer.setWeaknessesJson(objectMapper.writeValueAsString(evalResult.weaknesses()));
        } catch (Exception e) {
            log.warn("Failed to serialize strengths/weaknesses JSON: {}", e.getMessage());
            targetAnswer.setStrengthsJson("[]");
            targetAnswer.setWeaknessesJson("[]");
        }

        InterviewAnswer savedAnswer = answerRepository.save(targetAnswer);

        // Update answered questions count on interview
        long answeredCount = interview.getAnswers().stream()
                .filter(a -> a.getUserAnswer() != null && !a.getUserAnswer().isBlank())
                .count();
        interview.setAnsweredQuestions((int) answeredCount);

        interviewRepository.save(interview);
        return toAnswerDetailResponse(savedAnswer);
    }

    @Transactional
    public InterviewSummaryResponse completeInterview(Long userId, Long interviewId) {
        Interview interview = findAndValidateOwnership(userId, interviewId);

        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            return toSummaryResponse(interview);
        }

        List<InterviewAnswer> answeredList = interview.getAnswers().stream()
                .filter(a -> a.getAiScore() != null)
                .toList();

        if (!answeredList.isEmpty()) {
            BigDecimal sum = answeredList.stream()
                    .map(InterviewAnswer::getAiScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(answeredList.size()), 2, RoundingMode.HALF_UP);
            interview.setOverallScore(avg);
        } else {
            interview.setOverallScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        interview.setAnsweredQuestions(answeredList.size());
        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setCompletedAt(LocalDateTime.now());

        Interview saved = interviewRepository.save(interview);
        return toSummaryResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<InterviewListResponse> getUserInterviews(Long userId, Pageable pageable) {
        return interviewRepository.findByUserIdOrderByStartedAtDesc(userId, pageable)
                .map(this::toListResponse);
    }

    private Interview findAndValidateOwnership(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdWithAnswers(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found with id " + interviewId));

        if (!interview.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: you do not own this interview");
        }

        return interview;
    }

    public InterviewSummaryResponse toSummaryResponse(Interview i) {
        List<AnswerDetailResponse> answers = i.getAnswers().stream()
                .map(this::toAnswerDetailResponse)
                .toList();

        return new InterviewSummaryResponse(
                i.getId(),
                i.getTargetRole(),
                i.getInterviewType(),
                i.getDifficulty(),
                i.getTotalQuestions(),
                i.getAnsweredQuestions(),
                i.getOverallScore(),
                i.getStatus(),
                i.getStartedAt(),
                i.getCompletedAt(),
                answers
        );
    }

    public InterviewListResponse toListResponse(Interview i) {
        return new InterviewListResponse(
                i.getId(),
                i.getTargetRole(),
                i.getInterviewType(),
                i.getDifficulty(),
                i.getTotalQuestions(),
                i.getAnsweredQuestions(),
                i.getOverallScore(),
                i.getStatus(),
                i.getStartedAt(),
                i.getCompletedAt()
        );
    }

    public AnswerDetailResponse toAnswerDetailResponse(InterviewAnswer a) {
        List<String> strengths = parseJsonList(a.getStrengthsJson());
        List<String> weaknesses = parseJsonList(a.getWeaknessesJson());

        return new AnswerDetailResponse(
                a.getId(),
                a.getQuestionOrder(),
                a.getQuestionTextSnapshot(),
                a.getUserAnswer(),
                a.getAiScore(),
                a.getCorrectnessScore(),
                a.getClarityScore(),
                a.getCompletenessScore(),
                a.getRelevanceScore(),
                a.getFeedback(),
                strengths,
                weaknesses,
                a.getImprovementSuggestions(),
                a.getIdealAnswer(),
                a.isEvaluationFallback(),
                a.getAnsweredAt()
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
