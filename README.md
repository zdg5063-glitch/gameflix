# GameFlix: Login & Registration Service

Spring Boot 4.0.8, Java 21, Maven, Spring Security, Spring Data JPA, MySQL.

## How to run

### 1. MySQL setup
The app expects a database named `gameflix` and a dedicated user:

```sql
CREATE DATABASE gameflix;
CREATE USER 'gameflix_user'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON gameflix.* TO 'gameflix_user'@'localhost';
```

### 2. Configuration
Set these environment variables (or put the full property keys in an untracked
`application-local.properties`; see `application.properties.example`):

| Variable | Purpose |
|---|---|
| `MYSQL_USER` | database user (`gameflix_user`) |
| `MYSQL_PASSWORD` | database password |
| `JWT_SECRET` | HS256 signing secret, at least 32 random bytes (the app refuses to start with less) |
| `CORS_ORIGIN` | front-end origin (default `http://localhost:3000`) |

One way to generate a secret: `openssl rand -base64 48`

### 3. Start
```
mvn spring-boot:run
```
The service listens on port 8080. Hibernate creates the `users` table on first run.

### 4. Run the tests
The tests run against the same MySQL database, so they need the same three variables
(`MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET`) set in the environment or the test run
configuration:
```
mvn test
```

## Endpoints

| Endpoint | Success | Failure |
|---|---|---|
| `POST /api/auth/register` | 201 | 400 invalid input, 409 email already registered |
| `POST /api/auth/login` | 200 with token | 401 on any credential failure |
| `GET /api/users/me` | 200 with profile | 401 missing, invalid, or expired token |

## Schema
Output of `SHOW CREATE TABLE users;` from MySQL 9.0.1:

```sql
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(50) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
```

- `password` is `VARCHAR(255)` so the full hash fits (a `{bcrypt}` hash is 68 characters).
- Email uniqueness is enforced by the `uk_users_email` unique constraint, not only in application code.

## Password hashing
Passwords are hashed with bcrypt before they are stored in the database. The default bcrypt work
factor is 10, but I changed it to 12 so that a brute-force attack takes longer to succeed.

I measured the time per hash on my machine: 66 ms at a work factor of 10, 239 ms at 12 and 950 ms
at 14. I stopped at 12 because it is still significantly stronger than 10 and does not take about a
second for every login attempt like 14 does, which could clog the login endpoint if many users
logged in at the same time.

## ddl-auto
`spring.jpa.hibernate.ddl-auto=update`. Hibernate creates the table on first run and keeps existing data between restarts, so the users I register stay in the database and my test evidence is easy to reproduce. `create-drop` would wipe the table every run.

## Token lifetime
Tokens expire 30 minutes after they are issued. The value is `app.jwt.expiration-minutes=30` in
`application.properties`, so I can change it later without touching the code.

If a token lasted many hours or even days, someone with a leaked token would have a long time to
access the system and potentially cause damage. A 30-minute lifetime limits what a stolen token can
do, but is still long enough that users don't have to log in several times during one session. I
could design a refresh-token flow, but that is out of scope for this project.

## Other security decisions
- **Login failure message:** An unknown email or an incorrect password returns a 401 with the
  message "Invalid email or password". It is the same message in both cases, so an attacker gets no
  clue about which emails have an account in the system.
- **CORS:** The allowed origin is `http://localhost:3000` by default, and it can be changed with the
  `CORS_ORIGIN` environment variable. Only GET, POST and OPTIONS are allowed, since that is what my
  endpoints use. I did not use `*` because I don't want any website to be able to make requests from
  a user's browser.
- **Secrets:** The database password and the JWT secret are read from environment variables. They
  are not in any committed file and must never be. Files that could hold them locally
  (`application-local.properties`, `.env`) are listed in `.gitignore`, and
  `application.properties.example` shows the settings with placeholder values only.
- **Token payload:** The token contains only `sub` (the user id), `iat` (issued at) and `exp`
  (expires). Emails, passwords, hashes and display names are intentionally left out.

## Module 3 design-change note
My Module 3 ERD received feedback that it was inconsistent with a microservices architecture and
lacked detail. In a microservices design each service owns its own data, so this service owns only
the user and login data, in its own `users` table. Library, Subscription and Game from the ERD are
not part of this assignment and are not implemented here. How the `User` entity changed:

| Module 3 ERD (User) | This service (`users`) | Change and reason |
|---|---|---|
| `user_id` (uuid) | `id` (bigint, auto-increment) | Changed type and name. I followed the demo project's numeric auto-increment id; the token's `sub` claim carries this id. |
| `first_name`, `last_name` | `display_name` (50 characters) | Merged into one field, since registration only needs one name to display. |
| `username` | none | Removed. Login uses email and password, so a second identifier was not needed. |
| `email` | `email` (255 characters, unique) | Kept, now with a database unique constraint (`uk_users_email`). |
| `password_hash` | `password` (255 characters) | Renamed, because the assignment's evidence query reads the `password` column. It stores only the bcrypt hash. |
| `role` | none | Removed. Nothing in this assignment needs different permission levels. |
| `created_at` | `created_at` | Kept. |

## Test evidence
I used automated tests (Spring Boot with MockMvc, in `AuthEndpointsTests`) instead of Postman,
because I have already used Postman in another class and wanted to learn a different way to test.
The tests send requests to the real MySQL database, not a fake one. Each test registers users with a
random email, and after each test the users it created are deleted. There are 7 tests and all pass.

| # | Required case | Test |
|---|---|---|
| 1 | Successful registration | returns 201; the body has no password or hash; the stored password starts with `{bcrypt}` |
| 2 | Duplicate-email rejection | registering the same email twice returns 409 the second time |
| 3 | Successful login returning a token | returns 200 with a three-part token |
| 4 | Login with a wrong password | returns 401; the body is identical to the unknown-email response |
| 5 | Protected call with a valid token | `/api/users/me` returns 200 with the user's profile |
| 6a | Protected call with a missing token | `/api/users/me` returns 401 |
| 6b | Protected call with a tampered token | a token with an altered signature returns 401 |
| extra | Expired token | a token with `exp` two hours in the past returns 401 |

Screenshots are in the Word document submitted in Canvas:

## AI-use statement
I used Claude (an AI assistant) to generate the project shell to match my professor's demo project,
to help write the register, login and token code and the MockMvc tests, and to troubleshoot setup
problems (MySQL on my Mac, the IntelliJ Java version, environment variables in run configurations).

I checked the results myself: I ran the tests, looked at the stored hash in the database, and
measured the bcrypt timings on my own machine.

One thing I changed: the generated code used bcrypt's default work factor of 10. After measuring 10,
12 and 14, I chose 12 (see Password hashing).
