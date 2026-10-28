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

## Running with Docker
The service can also run in Docker. GameFlix and MySQL 8.0 run in two separate containers that
Docker Compose starts together. Compose puts them on a private network, keeps the database in a
named volume, waits until MySQL is healthy, and only then starts the Spring Boot app.

### Prerequisites
Docker Desktop (running), Java 21 and Maven (I used the one built into IntelliJ).

### Files added for Docker
| File | Purpose |
|---|---|
| `Dockerfile` | Builds the app image: a Java 21 runtime image, the Maven-built JAR copied in as `app.jar`, started with `java -jar`. |
| `compose.yaml` | Defines the `mysql` and `app` services, the `app-net` network and the `mysql_data` volume. |
| `.dockerignore` | Keeps `.git`, IDE files and `.env` out of the Docker build. `target/` is not ignored because the Dockerfile copies the JAR from it. |
| `.env.example` | Lists every variable Compose needs, with placeholder values only. |

### Setup
1. Copy `.env.example` to `.env`.
2. Replace the placeholder passwords with my own classroom-only values.
3. Generate the JWT secret with `openssl rand -base64 48` and put it in `JWT_SECRET`.

`.env` is listed in `.gitignore` and must never be committed.

### Build and run
```
mvn clean package -DskipTests
docker compose up --build -d
docker compose ps
```
The first command builds the JAR. Docker copies whatever JAR is in `target/`, so it has to be
rebuilt after every code or configuration change. The second builds the image and starts both
containers in the background. `docker compose ps` should show `mysql` as healthy and `app` as running.
If `app` is missing from the list it exited, and `docker compose logs app --tail 40` shows why.

### Testing the containers
I registered a user through the containerized app:
```
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"docker@example.com","password":"DockerTest123!","displayName":"Docker Test"}'
```
The app returned `201` with the new user's id, email and display name and no password. Then I
checked the MySQL container's database from the command line:
```
docker compose exec mysql mysql -u<MYSQL_USER> -p gameflix
SHOW TABLES;
SELECT id, email, display_name, created_at, LEFT(password, 12) AS hash_start FROM users;
```
The `users` table existed (Hibernate created it) and held the new account. The password column
started with `{bcrypt}$2a$`, so only a hash was stored.

### Data survives restarts
`docker compose down` removes the containers and the network but keeps the `mysql_data` volume. After
`docker compose down` and `docker compose up -d`, the same user was still in the database.
`docker compose down -v` also deletes the volume and permanently erases the database, so I only use
it when I want a fresh one.

### Design choices
- **Service name instead of `localhost`:** inside a container, `localhost` means the container
  itself. The app reaches the database at `mysql:3306`, using the Compose service name.
- **Environment variables override the defaults:** `application.properties` uses
  `${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/gameflix?...}`. Running from IntelliJ uses the
  `localhost` default, and Compose sets `SPRING_DATASOURCE_URL` to the `mysql` address in Docker.
- **Secrets stay out of the repo and the image:** the database passwords, `JWT_SECRET` and
  `CORS_ORIGIN` come from `.env` through `compose.yaml`. `.env` is git-ignored and in `.dockerignore`.
- **Health check:** `depends_on` with `condition: service_healthy` keeps the app from starting until
  `mysqladmin ping` succeeds, because MySQL needs time to initialize.
- **Ports:** MySQL is published on host port 3307 so it does not clash with the MySQL already
  running on my Mac at 3306. The app uses 8080.
- **First-run settings:** `MYSQL_DATABASE`, `MYSQL_USER` and the passwords only apply when the volume
  is empty, so changing them later requires `docker compose down -v` to take effect.

### Problem I hit
The first start failed with `Access denied for user 'root'`, even though the app should log in as
`gameflix_user`. The log showed the connection itself worked (so the `mysql` hostname and network
were fine) but the login was wrong. Looking at the file inside the JAR with
`unzip -p target/gameflix-0.0.1-SNAPSHOT.jar BOOT-INF/classes/application.properties` showed that
when I edited the datasource URL, `spring.datasource.username=${MYSQL_USER}` had been joined onto
the end of the URL line, so there was no username setting. I put it back on its own line, rebuilt
the JAR, and ran `docker compose up --build -d` again.

Screenshots of the Docker steps are in the Word document submitted in Canvas.

## AI-use statement
I used Claude (an AI assistant) to generate the project shell to match my professor's demo project,
to help write the register, login and token code and the MockMvc tests, and to troubleshoot setup
problems (MySQL on my Mac, the IntelliJ Java version, environment variables in run configurations).

I checked the results myself: I ran the tests, looked at the stored hash in the database, and
measured the bcrypt timings on my own machine.

One thing I changed: the generated code used bcrypt's default work factor of 10. After measuring 10,
12 and 14, I chose 12 (see Password hashing).

For the Docker assignment I used Claude to explain the handout and to help check the Dockerfile and
`compose.yaml`. I ran every command myself, found the cause of the `Access denied` error in the
packaged JAR, and verified the result with `docker compose ps`, the register request and a query
against the database inside the container.
