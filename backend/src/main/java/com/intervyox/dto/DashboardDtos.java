package com.intervyox.dto;

import com.intervyox.dto.InterviewDtos.InterviewListResponse;

import java.math.BigDecimal;
import java.util.List;

public class DashboardDtos {

    public record ScoreTrendPoint(
            String date,
            BigDecimal score,
            String role) {}

    public record RolePerformance(
            String role,
            BigDecimal averageScore,
            int interviewCount) {}

    public record CategoryPerformance(
            String category,
            BigDecimal averageScore,
            int interviewCount) {}

    public record DashboardSummaryResponse(
            long totalInterviews,
            BigDecimal averageScore,
            BigDecimal bestScore,
            List<ScoreTrendPoint> scoreTrend,
            List<RolePerformance> performanceByRole,
            List<CategoryPerformance> performanceByCategory,
            String strongestArea,
            String weakestArea,
            List<InterviewListResponse> recentInterviews) {}
}
