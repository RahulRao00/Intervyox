package com.intervyox.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "questions",
    indexes = {
        @Index(name = "idx_questions_role_type_diff_active", columnList = "target_role, interview_type, difficulty, active")
    }
)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_role", nullable = false, length = 100)
    private String targetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 20)
    private InterviewType interviewType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "ideal_answer_outline", columnDefinition = "TEXT")
    private String idealAnswerOutline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionSource source = QuestionSource.SEEDED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Question() {}

    public Question(String targetRole, InterviewType interviewType, Difficulty difficulty,
                    String questionText, String idealAnswerOutline, QuestionSource source,
                    User createdByUser, boolean active) {
        this.targetRole = targetRole;
        this.interviewType = interviewType;
        this.difficulty = difficulty;
        this.questionText = questionText;
        this.idealAnswerOutline = idealAnswerOutline;
        this.source = source != null ? source : QuestionSource.SEEDED;
        this.createdByUser = createdByUser;
        this.active = active;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (source == null) source = QuestionSource.SEEDED;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public InterviewType getInterviewType() { return interviewType; }
    public void setInterviewType(InterviewType interviewType) { this.interviewType = interviewType; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getIdealAnswerOutline() { return idealAnswerOutline; }
    public void setIdealAnswerOutline(String idealAnswerOutline) { this.idealAnswerOutline = idealAnswerOutline; }

    public QuestionSource getSource() { return source; }
    public void setSource(QuestionSource source) { this.source = source; }

    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
