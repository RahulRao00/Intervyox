package com.intervyox.repository;

import com.intervyox.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserEmailOrderByCreatedAtDesc(String email);
}
