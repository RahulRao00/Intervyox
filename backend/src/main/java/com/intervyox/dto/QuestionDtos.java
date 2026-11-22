package com.intervyox.dto;

import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;
import com.intervyox.model.QuestionSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class QuestionDtos {

    public record QuestionResponse(
            Long id,
            String targetRole,
            InterviewType interviewType,
            Difficulty difficulty,
            String questionText,
            String idealAnswerOutline,
            QuestionSource source,
            boolean active,
            LocalDateTime createdAt) {}

    public record CreateQuestionRequest(
            @NotBlank(message = "Target role is required")
            String targetRole,

            @NotNull(message = "Interview type is required")
            InterviewType interviewType,

            @NotNull(message = "Difficulty is required")
            Difficulty difficulty,

            @NotBlank(message = "Question text is required")
            String questionText,

            String idealAnswerOutline,
            Boolean active) {}

    public record UpdateQuestionRequest(
            String targetRole,
            InterviewType interviewType,
            Difficulty difficulty,
            String questionText,
            String idealAnswerOutline,
            Boolean active) {}
}
