package com.intervyox.controller;

import com.intervyox.dto.InterviewDtos.*;
import com.intervyox.model.Interview;
import com.intervyox.model.User;
import com.intervyox.repository.InterviewRepository;
import com.intervyox.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewRepository interviews;
    private final UserRepository users;

    public InterviewController(InterviewRepository interviews, UserRepository users) {
        this.interviews = interviews;
        this.users = users;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse create(@Valid @RequestBody CreateInterviewRequest req, Authentication auth) {
        User user = users.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        Interview saved = interviews.save(new Interview(user, req.category(), req.difficulty()));
        return toResponse(saved);
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public List<InterviewResponse> history(Authentication auth) {
        return interviews.findByUserEmailOrderByCreatedAtDesc(auth.getName())
                .stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public InterviewResponse get(@PathVariable Long id, Authentication auth) {
        Interview interview = interviews.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        // Ownership check: a user may only read their own interviews
        if (!interview.getUser().getEmail().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your interview");
        }
        return toResponse(interview);
    }

    private InterviewResponse toResponse(Interview i) {
        return new InterviewResponse(i.getId(), i.getCategory(), i.getDifficulty(), i.getStatus(), i.getCreatedAt());
    }
}
