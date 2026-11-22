package com.intervyox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intervyox.model.*;
import com.intervyox.repository.QuestionRepository;
import com.intervyox.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class QuestionDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestionDataSeeder.class);

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${intervyox.seed.admin.enabled:true}")
    private boolean seedAdminEnabled;

    @Value("${intervyox.seed.admin.email:admin@intervyox.com}")
    private String adminEmail;

    @Value("${intervyox.seed.admin.password:admin12345}")
    private String adminPassword;

    @Value("${intervyox.seed.admin.name:System Administrator}")
    private String adminName;

    public QuestionDataSeeder(
            QuestionRepository questionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedQuestionsIfEmpty();
        seedAdminIfConfigured();
    }

    private void seedQuestionsIfEmpty() {
        if (questionRepository.count() > 0) {
            log.info("Questions table already contains data. Skipping question seeding.");
            return;
        }

        try {
            ClassPathResource resource = new ClassPathResource("seed/questions.json");
            if (!resource.exists()) {
                log.warn("seed/questions.json not found on classpath.");
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                List<Map<String, String>> rawQuestions = objectMapper.readValue(
                        inputStream, new TypeReference<List<Map<String, String>>>() {});

                for (Map<String, String> item : rawQuestions) {
                    String role = item.get("targetRole");
                    InterviewType type = InterviewType.valueOf(item.get("interviewType").toUpperCase());
                    Difficulty difficulty = Difficulty.valueOf(item.get("difficulty").toUpperCase());
                    String text = item.get("questionText");
                    String outline = item.get("idealAnswerOutline");

                    Question question = new Question(
                            role, type, difficulty, text, outline, QuestionSource.SEEDED, null, true
                    );
                    questionRepository.save(question);
                }

                log.info("Successfully seeded {} questions into the database.", rawQuestions.size());
            }
        } catch (Exception e) {
            log.error("Failed to seed questions from seed/questions.json: {}", e.getMessage(), e);
        }
    }

    private void seedAdminIfConfigured() {
        if (!seedAdminEnabled) return;

        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = new User(
                    adminName,
                    adminEmail,
                    passwordEncoder.encode(adminPassword),
                    Role.ADMIN,
                    "Lead Technical Architect"
            );
            userRepository.save(admin);
            log.info("Seeded demo admin account: {}", adminEmail);
        }
    }
}
