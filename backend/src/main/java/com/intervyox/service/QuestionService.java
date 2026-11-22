package com.intervyox.service;

import com.intervyox.dto.QuestionDtos.CreateQuestionRequest;
import com.intervyox.dto.QuestionDtos.QuestionResponse;
import com.intervyox.dto.QuestionDtos.UpdateQuestionRequest;
import com.intervyox.model.*;
import com.intervyox.repository.QuestionRepository;
import com.intervyox.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;

    public QuestionService(QuestionRepository questionRepository, UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Question> selectQuestionsForInterview(
            String targetRole, InterviewType interviewType, Difficulty difficulty, int count) {
        // 1. Try exact match (role, type, difficulty)
        List<Question> candidates = new ArrayList<>(
                questionRepository.findByTargetRoleIgnoreCaseAndInterviewTypeAndDifficultyAndActiveTrue(
                        targetRole, interviewType, difficulty));

        // 2. Fall back to same type and difficulty
        if (candidates.size() < count) {
            List<Question> typeDiffMatches = questionRepository.findByInterviewTypeAndDifficultyAndActiveTrue(
                    interviewType, difficulty);
            for (Question q : typeDiffMatches) {
                if (!candidates.contains(q)) {
                    candidates.add(q);
                }
            }
        }

        // 3. Fall back to same role
        if (candidates.size() < count) {
            List<Question> roleMatches = questionRepository.findByTargetRoleIgnoreCaseAndActiveTrue(targetRole);
            for (Question q : roleMatches) {
                if (!candidates.contains(q)) {
                    candidates.add(q);
                }
            }
        }

        // 4. Fall back to any active questions
        if (candidates.size() < count) {
            List<Question> allActive = questionRepository.findByActiveTrue();
            for (Question q : allActive) {
                if (!candidates.contains(q)) {
                    candidates.add(q);
                }
            }
        }

        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> listQuestions(
            String role, InterviewType type, Difficulty diff, Boolean active, Pageable pageable) {
        return questionRepository.searchQuestions(role, type, diff, active, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long id) {
        return questionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id " + id));
    }

    @Transactional
    public QuestionResponse createQuestion(CreateQuestionRequest req, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        Question question = new Question(
                req.targetRole(),
                req.interviewType(),
                req.difficulty(),
                req.questionText(),
                req.idealAnswerOutline(),
                QuestionSource.ADMIN,
                admin,
                req.active() != null ? req.active() : true
        );
        Question saved = questionRepository.save(question);
        return toResponse(saved);
    }

    @Transactional
    public QuestionResponse updateQuestion(Long id, UpdateQuestionRequest req) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id " + id));

        if (req.targetRole() != null && !req.targetRole().isBlank()) {
            question.setTargetRole(req.targetRole());
        }
        if (req.interviewType() != null) {
            question.setInterviewType(req.interviewType());
        }
        if (req.difficulty() != null) {
            question.setDifficulty(req.difficulty());
        }
        if (req.questionText() != null && !req.questionText().isBlank()) {
            question.setQuestionText(req.questionText());
        }
        if (req.idealAnswerOutline() != null) {
            question.setIdealAnswerOutline(req.idealAnswerOutline());
        }
        if (req.active() != null) {
            question.setActive(req.active());
        }

        return toResponse(question);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id " + id);
        }
        questionRepository.deleteById(id);
    }

    public QuestionResponse toResponse(Question q) {
        return new QuestionResponse(
                q.getId(),
                q.getTargetRole(),
                q.getInterviewType(),
                q.getDifficulty(),
                q.getQuestionText(),
                q.getIdealAnswerOutline(),
                q.getSource(),
                q.isActive(),
                q.getCreatedAt()
        );
    }
}
