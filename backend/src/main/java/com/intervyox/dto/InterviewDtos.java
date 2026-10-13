package com.intervyox.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class InterviewDtos {

    public record CreateInterviewRequest(
            @NotBlank String category,
            @NotBlank String difficulty) {}

    public record InterviewResponse(
            Long id,
            String category,
            String difficulty,
            String status,
            LocalDateTime createdAt) {}
}
