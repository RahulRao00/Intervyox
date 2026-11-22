package com.intervyox.repository;

import com.intervyox.model.Interview;
import com.intervyox.model.InterviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    @Query("SELECT i FROM Interview i LEFT JOIN FETCH i.answers WHERE i.id = :id")
    Optional<Interview> findByIdWithAnswers(@Param("id") Long id);

    Page<Interview> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    List<Interview> findByUserIdOrderByStartedAtDesc(Long userId);

    List<Interview> findByUserIdAndStatusOrderByStartedAtDesc(Long userId, InterviewStatus status);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, InterviewStatus status);

    @Query("SELECT AVG(i.overallScore) FROM Interview i WHERE i.user.id = :userId AND i.status = 'COMPLETED' AND i.overallScore IS NOT NULL")
    BigDecimal findAverageScoreByUserId(@Param("userId") Long userId);

    @Query("SELECT MAX(i.overallScore) FROM Interview i WHERE i.user.id = :userId AND i.status = 'COMPLETED' AND i.overallScore IS NOT NULL")
    BigDecimal findBestScoreByUserId(@Param("userId") Long userId);
}
