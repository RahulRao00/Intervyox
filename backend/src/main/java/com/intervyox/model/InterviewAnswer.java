package com.intervyox.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "interview_answers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_interview_question_order", columnNames = {"interview_id", "question_order"})
    }
)
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question_text_snapshot", nullable = false, columnDefinition = "TEXT")
    private String questionTextSnapshot;

    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    @Column(name = "ai_score", precision = 4, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "correctness_score", precision = 4, scale = 2)
    private BigDecimal correctnessScore;

    @Column(name = "clarity_score", precision = 4, scale = 2)
    private BigDecimal clarityScore;

    @Column(name = "completeness_score", precision = 4, scale = 2)
    private BigDecimal completenessScore;

    @Column(name = "relevance_score", precision = 4, scale = 2)
    private BigDecimal relevanceScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "weaknesses_json", columnDefinition = "TEXT")
    private String weaknessesJson;

    @Column(name = "improvement_suggestions", columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(name = "ideal_answer", columnDefinition = "TEXT")
    private String idealAnswer;

    @Column(name = "evaluation_fallback", nullable = false)
    private boolean evaluationFallback = false;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    public InterviewAnswer() {}

    public InterviewAnswer(Interview interview, Question question, int questionOrder, String questionTextSnapshot) {
        this.interview = interview;
        this.question = question;
        this.questionOrder = questionOrder;
        this.questionTextSnapshot = questionTextSnapshot;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Interview getInterview() { return interview; }
    public void setInterview(Interview interview) { this.interview = interview; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public int getQuestionOrder() { return questionOrder; }
    public void setQuestionOrder(int questionOrder) { this.questionOrder = questionOrder; }

    public String getQuestionTextSnapshot() { return questionTextSnapshot; }
    public void setQuestionTextSnapshot(String questionTextSnapshot) { this.questionTextSnapshot = questionTextSnapshot; }

    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }

    public BigDecimal getAiScore() { return aiScore; }
    public void setAiScore(BigDecimal aiScore) { this.aiScore = aiScore; }

    public BigDecimal getCorrectnessScore() { return correctnessScore; }
    public void setCorrectnessScore(BigDecimal correctnessScore) { this.correctnessScore = correctnessScore; }

    public BigDecimal getClarityScore() { return clarityScore; }
    public void setClarityScore(BigDecimal clarityScore) { this.clarityScore = clarityScore; }

    public BigDecimal getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(BigDecimal completenessScore) { this.completenessScore = completenessScore; }

    public BigDecimal getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(BigDecimal relevanceScore) { this.relevanceScore = relevanceScore; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public String getStrengthsJson() { return strengthsJson; }
    public void setStrengthsJson(String strengthsJson) { this.strengthsJson = strengthsJson; }

    public String getWeaknessesJson() { return weaknessesJson; }
    public void setWeaknessesJson(String weaknessesJson) { this.weaknessesJson = weaknessesJson; }

    public String getImprovementSuggestions() { return improvementSuggestions; }
    public void setImprovementSuggestions(String improvementSuggestions) { this.improvementSuggestions = improvementSuggestions; }

    public String getIdealAnswer() { return idealAnswer; }
    public void setIdealAnswer(String idealAnswer) { this.idealAnswer = idealAnswer; }

    public boolean isEvaluationFallback() { return evaluationFallback; }
    public void setEvaluationFallback(boolean evaluationFallback) { this.evaluationFallback = evaluationFallback; }

    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }
}
