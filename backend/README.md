# IntervyoX Backend

Spring Boot 3.3 / Java 17 / MySQL / JWT.

## Run

1. Start MySQL locally (the `intervyox` database is created automatically).
2. Set environment variables (never commit these):

   ```bash
   export DB_USERNAME=root
   export DB_PASSWORD=your_mysql_password
   export JWT_SECRET=replace-with-a-random-string-of-at-least-32-characters
   ```

3. Run:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

The API starts on http://localhost:8080.

## Endpoints implemented

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | /api/auth/register | No | Create account (201, 409 if email exists) |
| POST | /api/auth/login | No | Returns JWT (401 on wrong credentials) |
| GET | /api/users/me | JWT | Current user profile |
| POST | /api/interviews | JWT | Create interview session |
| GET | /api/interviews/{id} | JWT | Get own interview (403 if not owner) |
| GET | /api/interviews/history | JWT | List own interviews |

## Postman tests

1. `POST /api/auth/register` body `{"email":"a@test.com","password":"password123","targetRole":"Java Developer"}` -> 201
2. `POST /api/auth/login` with the same email and password -> 200 with token; wrong password -> 401
3. `GET /api/users/me` with header `Authorization: Bearer <token>` -> 200; without a token -> 401
4. `POST /api/interviews` body `{"category":"TECHNICAL","difficulty":"MEDIUM"}` -> 201
5. Register a second user and `GET /api/interviews/{id}` of the first user's interview -> 403
