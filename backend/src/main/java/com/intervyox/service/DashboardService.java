package com.intervyox.service;

import com.intervyox.dto.DashboardDtos.*;
import com.intervyox.dto.InterviewDtos.InterviewListResponse;
import com.intervyox.model.Interview;
import com.intervyox.model.InterviewStatus;
import com.intervyox.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final InterviewRepository interviewRepository;
    private final InterviewService interviewService;

    public DashboardService(InterviewRepository interviewRepository, InterviewService interviewService) {
        this.interviewRepository = interviewRepository;
        this.interviewService = interviewService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(Long userId) {
        List<Interview> allInterviews = interviewRepository.findByUserIdOrderByStartedAtDesc(userId);

        long totalInterviews = allInterviews.size();

        List<Interview> completed = allInterviews.stream()
                .filter(i -> i.getStatus() == InterviewStatus.COMPLETED && i.getOverallScore() != null)
                .toList();

        BigDecimal averageScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal bestScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (!completed.isEmpty()) {
            BigDecimal sum = completed.stream()
                    .map(Interview::getOverallScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageScore = sum.divide(BigDecimal.valueOf(completed.size()), 2, RoundingMode.HALF_UP);

            bestScore = completed.stream()
                    .map(Interview::getOverallScore)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Score trend (in ascending chronological order for charts)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        List<ScoreTrendPoint> scoreTrend = completed.stream()
                .sorted(Comparator.comparing(Interview::getStartedAt))
                .map(i -> new ScoreTrendPoint(
                        i.getStartedAt().format(formatter),
                        i.getOverallScore(),
                        i.getTargetRole()
                ))
                .toList();

        // Performance by Role
        Map<String, List<Interview>> byRole = completed.stream()
                .collect(Collectors.groupingBy(Interview::getTargetRole));

        List<RolePerformance> performanceByRole = byRole.entrySet().stream()
                .map(e -> {
                    BigDecimal roleAvg = e.getValue().stream()
                            .map(Interview::getOverallScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP);
                    return new RolePerformance(e.getKey(), roleAvg, e.getValue().size());
                })
                .sorted(Comparator.comparing(RolePerformance::averageScore).reversed())
                .toList();

        // Performance by Category (TECHNICAL vs HR)
        Map<String, List<Interview>> byCategory = completed.stream()
                .collect(Collectors.groupingBy(i -> i.getInterviewType().name()));

        List<CategoryPerformance> performanceByCategory = byCategory.entrySet().stream()
                .map(e -> {
                    BigDecimal catAvg = e.getValue().stream()
                            .map(Interview::getOverallScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP);
                    return new CategoryPerformance(e.getKey(), catAvg, e.getValue().size());
                })
                .sorted(Comparator.comparing(CategoryPerformance::averageScore).reversed())
                .toList();

        String strongestArea = "No data yet";
        String weakestArea = "No data yet";

        if (!performanceByRole.isEmpty()) {
            strongestArea = performanceByRole.get(0).role() + " (" + performanceByRole.get(0).averageScore() + "/10)";
            weakestArea = performanceByRole.get(performanceByRole.size() - 1).role() + " (" + performanceByRole.get(performanceByRole.size() - 1).averageScore() + "/10)";
        }

        // Recent 5 interviews
        List<InterviewListResponse> recentInterviews = allInterviews.stream()
                .limit(5)
                .map(interviewService::toListResponse)
                .toList();

        return new DashboardSummaryResponse(
                totalInterviews,
                averageScore,
                bestScore,
                scoreTrend,
                performanceByRole,
                performanceByCategory,
                strongestArea,
                weakestArea,
                recentInterviews
        );
    }
}
