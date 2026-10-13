# IntervyoX: AI-Powered Mock Interview and Personalized Feedback Platform

## 1. Overview

IntervyoX is a web platform where students practice mock interviews and get structured feedback on their answers. A user picks a role and interview type, answers questions, and receives an AI-generated evaluation with strengths, weaknesses and suggestions. Past sessions are stored so progress can be tracked over time.

## 2. Problem and Objectives

Interview preparation is scattered across question banks, coding platforms, peer practice and paid coaching. Candidates rarely get one place that offers realistic practice together with immediate, structured feedback.

**Primary research question:** How can an AI-assisted web application provide structured mock interviews and personalized feedback while keeping the architecture secure, the evaluation criteria consistent and the system usable?

**Objectives**

- Build a modular platform for technical, HR and role-based mock interviews
- Provide secure registration and login using JWT
- Support configurable interview sessions with predefined and AI-generated questions
- Integrate an LLM for question generation and rubric-based answer evaluation
- Generate structured feedback (strengths, weaknesses, suggestions)
- Store interview history and show progress on a dashboard

## 3. System Architecture

The system has four parts: a React frontend, a Spring Boot backend, a MySQL database and an external LLM API. The backend sits in the middle and controls everything. It checks every request, enforces login and permissions, stores data, and is the only component that talks to the AI. The AI is used only to understand answers and write feedback.

```mermaid
flowchart LR
    U[User Browser] --> F[React Frontend<br/>UI and Dashboard]
    F -->|REST + JWT| B[Spring Boot Backend<br/>Controllers, Services, Security]
    B --> D[(MySQL<br/>JPA / Hibernate)]
    B -->|controlled integration layer| A[LLM API<br/>Gemini / OpenAI]
    B --> L[Logs / Monitoring]
```

## 4. Database Design

A user can take many interviews. Each interview contains many questions. Each question can have one response, and each response can have one evaluation. Foreign keys keep this history consistent and make it easy to fetch past performance.

```mermaid
erDiagram
    USER ||--o{ INTERVIEW : takes
    INTERVIEW ||--|{ QUESTION : contains
    QUESTION ||--o| RESPONSE : answered_by
    RESPONSE ||--o| EVALUATION : scored_by
    USER {
        long id PK
        string email
        string password_hash
        string target_role
    }
    INTERVIEW {
        long id PK
        long user_id FK
        string category
        string difficulty
        string status
    }
    QUESTION {
        long id PK
        long interview_id FK
        string text
        string source
    }
    RESPONSE {
        long id PK
        long question_id FK
        text answer
    }
    EVALUATION {
        long id PK
        long response_id FK
        int relevance
        int clarity
        int correctness
        text strengths
        text weaknesses
        text suggestions
    }
```

## 5. Interview and Evaluation Flow

This shows what happens from login to feedback. The user logs in and receives a JWT, which is sent with every later request. They create an interview, the backend generates or selects questions, and the user submits answers. When an answer is evaluated, the backend sends a rubric-based prompt to the LLM, validates the structured JSON that comes back, saves it, and returns a feedback report.

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant BE as Spring Boot API
    participant DB as MySQL
    participant AI as LLM API

    User->>FE: Register / Login
    FE->>BE: POST /api/auth/login
    BE->>DB: Verify credentials
    BE-->>FE: JWT
    User->>FE: Choose role, category, difficulty
    FE->>BE: POST /api/interviews (JWT)
    BE->>DB: Create session + questions
    BE-->>FE: Interview with questions
    User->>FE: Submit answer
    FE->>BE: POST /api/interviews/{id}/responses
    BE->>DB: Store response
    FE->>BE: POST /api/interviews/{id}/evaluate
    BE->>AI: Rubric prompt (question, answer, criteria)
    AI-->>BE: Structured JSON evaluation
    BE->>BE: Validate schema
    BE->>DB: Store evaluation
    BE-->>FE: Feedback report
    FE-->>User: Strengths, weaknesses, suggestions
```

## 6. Use Cases

The student is the main user. They can register and log in, choose a role and interview category, take the interview, submit answers, view AI feedback and review their history. Question generation and answer evaluation both rely on the LLM service.

```mermaid
flowchart LR
    S((Student)) --> UC1[Register / Login]
    S --> UC2[Select role and category]
    S --> UC3[Take mock interview]
    S --> UC4[Submit answers]
    S --> UC5[View AI feedback]
    S --> UC6[View history and dashboard]
    UC4 -.includes.-> UC7[AI evaluation]
    UC3 -.includes.-> UC8[Question generation]
    UC7 --- AI((LLM Service))
    UC8 --- AI
```

## 7. AI Evaluation Pipeline

Each answer is evaluated against a fixed rubric so scoring stays consistent. The backend builds a prompt from the question, the answer, the role and the criteria, then asks the LLM for JSON output. If the JSON is valid, it is normalized and stored. If not, the backend retries once and then shows a fallback message.

```mermaid
flowchart TD
    A[Question + Candidate Answer + Role/Category] --> B[Build constrained prompt with rubric]
    B --> C[Call LLM API]
    C --> D{Valid structured JSON?}
    D -- yes --> E[Normalize scores and feedback]
    D -- no --> F[Retry once, else show fallback message]
    E --> G[(Store Evaluation)]
    G --> H[Feedback report + dashboard]
```

Rubric dimensions: relevance, technical correctness (where applicable), clarity, completeness, reasoning and communication. AI feedback is advisory and is not a hiring decision.

## 8. Planned REST API

| Method | Endpoint                       | Purpose                  |
| ------ | ------------------------------ | ------------------------ |
| POST   | /api/auth/register             | Create account           |
| POST   | /api/auth/login                | Login, returns JWT       |
| GET    | /api/users/me                  | Current user profile     |
| POST   | /api/interviews                | Create interview session |
| GET    | /api/interviews/{id}           | Get interview            |
| POST   | /api/interviews/{id}/responses | Submit answer            |
| POST   | /api/interviews/{id}/evaluate  | Trigger AI evaluation    |
| GET    | /api/interviews/history        | Past interviews          |
| GET    | /api/dashboard/summary         | Progress summary         |

## 9. Tech Stack and Progress

**Stack:** React, Tailwind CSS | Java, Spring Boot, Spring Security, JWT | MySQL, JPA/Hibernate | Gemini / OpenAI API | JUnit, Postman | Git, GitHub

**Implementation progress** (ticked items are done)

- [x] Requirements, literature review, architecture and DB design
- [x] Spring Boot project setup
- [ ] MySQL schema and entities
- [ ] Registration and login with JWT
- [ ] Protected endpoint (/api/users/me)
- [ ] Interview creation API
- [ ] Response submission API
- [ ] LLM integration with rubric prompt
- [ ] React frontend
- [ ] Dashboard
- [ ] Testing and security hardening
- [ ] Deployment
