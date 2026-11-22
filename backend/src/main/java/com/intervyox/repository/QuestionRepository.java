package com.intervyox.repository;

import com.intervyox.model.Difficulty;
import com.intervyox.model.InterviewType;
import com.intervyox.model.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTargetRoleIgnoreCaseAndInterviewTypeAndDifficultyAndActiveTrue(
            String targetRole, InterviewType interviewType, Difficulty difficulty);

    List<Question> findByInterviewTypeAndDifficultyAndActiveTrue(
            InterviewType interviewType, Difficulty difficulty);

    List<Question> findByTargetRoleIgnoreCaseAndActiveTrue(String targetRole);

    List<Question> findByActiveTrue();

    @Query("SELECT q FROM Question q WHERE " +
           "(:role IS NULL OR LOWER(q.targetRole) LIKE LOWER(CONCAT('%', :role, '%'))) AND " +
           "(:type IS NULL OR q.interviewType = :type) AND " +
           "(:diff IS NULL OR q.difficulty = :diff) AND " +
           "(:active IS NULL OR q.active = :active)")
    Page<Question> searchQuestions(
            @Param("role") String role,
            @Param("type") InterviewType type,
            @Param("diff") Difficulty diff,
            @Param("active") Boolean active,
            Pageable pageable);
}
