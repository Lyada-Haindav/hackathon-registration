# Hackathon Registration and Management System

Production-style full stack Java web application built with **Spring Boot + MongoDB + JWT + Thymeleaf**.

## Tech Stack
- Java 17
- Spring Boot 3.3.5
- Spring Security (JWT + RBAC)
- Spring Data MongoDB
- Thymeleaf + Vanilla JavaScript dashboards
- Razorpay Java SDK

## Core Capabilities
- User + Faculty login with JWT authentication
- Participant email verification + forgot/reset password using email links
- Faculty can create events, dynamic registration forms, fees, criteria, and problem statements
- User can register teams, submit dynamic form responses, pay via Razorpay, and track status
- Faculty evaluation module with editable criterion-based scoring
- Auto recalculated team total score and dynamic leaderboard (Top 3 + full ranking)

## Architecture
Layered architecture:
- `controller` - API endpoints and web route controllers
- `service` - business logic and transaction orchestration
- `repository` - MongoDB data access
- `model` - MongoDB document classes
- `dto` - request/response contracts
- `security` - JWT generation, filter, Spring Security setup
- `exception` - centralized exception handling
- `util` - helpers (team name generator, signature utility)

## Project Structure
```
src/main/java/com/example/hackathon
├── HackathonRegistrationManagementApplication.java
├── config
├── controller
├── dto
├── exception
├── model
├── repository
├── security
├── service
└── util

src/main/resources
├── application.yml
├── static
│   ├── css/styles.css
│   └── js/*.js
└── templates
    ├── index.html
    ├── login.html
    ├── register.html
    ├── user-dashboard.html
    ├── faculty-dashboard.html
    └── leaderboard.html
```

## MongoDB Schema Design

### `users`
- `_id`
- `name`
- `email` (unique)
- `password` (BCrypt hash)
- `role` (`USER` / `FACULTY`)
- `active`
- `createdAt`

### `events`
- `_id`
- `title`
- `description`
- `startDate`, `endDate`
- `registrationOpenDate`, `registrationCloseDate`
- `registrationFee`
- `active`
- `createdBy`, `createdAt`

### `forms`
- `_id`
- `eventId` (unique)
- `fields[]` (`key`, `label`, `type`, `required`, `options[]`)
- `createdBy`, `createdAt`, `updatedAt`

### `teams`
- `_id`
- `eventId`, `userId` (compound unique)
- `teamName` (auto-generated)
- `teamSize`
- `paymentStatus`
- `razorpayOrderId`, `razorpayPaymentId`, `paymentRecordId`
- `formResponses` (dynamic key-value map)
- `totalScore`
- `createdAt`

### `teamMembers`
- `_id`
- `teamId`
- `name`, `email`, `phone`, `college`
- `leader`
- `createdAt`

### `payments`
- `_id`
- `teamId`, `eventId`
- `amount`, `currency`
- `razorpayOrderId`, `razorpayPaymentId`, `razorpaySignature`
- `status` (`ORDER_CREATED`, `CAPTURED`, `FAILED`, `SIGNATURE_MISMATCH`)
- `createdAt`, `verifiedAt`

### `problemStatements`
- `_id`
- `eventId`
- `title`, `description`
- `released`, `releasedAt`
- `createdBy`, `createdAt`

### `criteria`
- `_id`
- `eventId`
- `name`
- `maxMarks`
- `createdBy`, `createdAt`

### `evaluations`
- `_id` (evaluationId)
- `eventId`
- `teamId`
- `criterionId`
- `criterionName`
- `marksGiven`
- `maxMarks`
- `evaluatedBy`
- `evaluatedAt`
- `totalScore`

### `authTokens`
- `_id`
- `token` (unique)
- `userId`
- `email`
- `type` (`EMAIL_VERIFICATION` / `PASSWORD_RESET`)
- `expiresAt` (TTL index)
- `createdAt`

## Leaderboard Logic
1. Faculty submits criterion-wise scores for a team.
2. System upserts evaluation records (`eventId + teamId + criterionId`).
3. Team total score is recalculated as sum of all `marksGiven`.
4. Team document gets updated `totalScore`.
5. Leaderboard endpoint sorts teams by `totalScore DESC` and returns ranks.
6. `Top 3` endpoint returns first 3 teams.
7. Frontend uses polling + SSE stream endpoint for live updates.

## Security
- Stateless JWT authentication
- Password hashing with BCrypt
- Role-based API access:
  - `/api/faculty/**` -> `FACULTY`
  - `/api/user/**` -> `USER` or `FACULTY`
  - `/api/leaderboard/**` -> public read
- Auth token expected in header:
  - `Authorization: Bearer <JWT_TOKEN>`

## API Endpoints

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/verify-email?token=...`
- `POST /api/auth/resend-verification`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`

### User APIs
- `GET /api/user/events`
- `GET /api/user/forms/{eventId}`
- `POST /api/user/teams/register`
- `GET /api/user/teams/{eventId}`
- `GET /api/user/teams`
- `POST /api/user/payments/{teamId}/order`
- `POST /api/user/payments/{teamId}/verify`
- `GET /api/user/problem-statements/{eventId}`

### Faculty APIs
- `POST /api/faculty/events`
- `PUT /api/faculty/events/{eventId}`
- `GET /api/faculty/events`
- `POST /api/faculty/forms`
- `GET /api/faculty/forms/{eventId}`
- `GET /api/faculty/events/{eventId}/teams`
- `POST /api/faculty/problem-statements`
- `PUT /api/faculty/problem-statements/{problemId}/release`
- `GET /api/faculty/problem-statements/{eventId}`
- `POST /api/faculty/criteria`
- `GET /api/faculty/criteria/{eventId}`
- `POST /api/faculty/evaluations`
- `GET /api/faculty/evaluations/{eventId}/{teamId}`
- `GET /api/faculty/evaluations/{eventId}`
- `GET /api/faculty/exports/{eventId}/{dataset}?format=csv|xlsx`
  - `dataset`: `teams` | `payments` | `leaderboard` | `evaluations`

### Leaderboard APIs
- `GET /api/leaderboard/{eventId}`
- `GET /api/leaderboard/{eventId}/top3`
- `GET /api/leaderboard/{eventId}/stream` (SSE)

### Deployment Readiness API (Faculty)
- `GET /api/faculty/deployment/readiness`
  - Verifies MongoDB connectivity, JWT secret strength, mock payment mode, Razorpay key configuration, and Razorpay API authentication.

## Deployment Notes
- Use `deployment.env.example` as the base environment config for production.
- Keep `PAYMENT_MOCK_ENABLED=false` in production.
- Configure real `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` before enabling live checkout.

## Deploy on Render
1. Push this repo to GitHub (already done).
2. In Render, click **New +** -> **Blueprint** and select this repository.
3. Render will auto-detect `render.yaml` and create the `hackathon-registration` web service.
4. Set required secrets in Render:
   - `MONGODB_URI`
   - `JWT_SECRET`
   - `ADMIN_PASSWORD`
   - `RAZORPAY_KEY_ID`
   - `RAZORPAY_KEY_SECRET`
   - `EMAIL_ENABLED`
   - `EMAIL_FROM`
   - `APP_BASE_URL`
   - `SMTP_HOST`
   - `SMTP_PORT`
   - `SMTP_USERNAME`
   - `SMTP_PASSWORD`
5. Deploy and open your Render URL:
   - `https://<your-service-name>.onrender.com`
6. Verify readiness from faculty portal:
   - `/faculty/deployment`

### Free Email Setup (Brevo)
1. Create a Brevo account and generate an SMTP key.
2. Set `EMAIL_ENABLED=true`.
3. Set SMTP vars (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`).
4. Set `EMAIL_FROM` to a verified sender/domain in Brevo.
5. Set `APP_BASE_URL` to your deployed URL so verification/reset links open correctly.

## Deploy on Vercel (as public entry/proxy)
Vercel cannot run this Spring Boot server directly as a long-running process.  
Use Render for backend runtime and Vercel as the public edge entry.

1. Open `vercel.json` and set destination to your actual Render URL if it differs:
   - `https://hackathon-registration.onrender.com`
2. Import this repository into Vercel and deploy.
3. Vercel will forward all routes (`/`, `/api/*`, dashboards) to your Render app.
4. Attach your custom domain in Vercel if needed.

## Example Requests / Responses

### Register User
`POST /api/auth/register`
```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "password": "Alice@123"
}
```

Response:
```json
{
  "token": "<JWT>",
  "userId": "65f...",
  "name": "Alice",
  "email": "alice@example.com",
  "role": "USER"
}
```

### Create Event (Faculty)
`POST /api/faculty/events`
```json
{
  "title": "National AI Hackathon",
  "description": "Build AI products",
  "startDate": "2026-03-10",
  "endDate": "2026-03-12",
  "registrationOpenDate": "2026-02-20",
  "registrationCloseDate": "2026-03-05",
  "registrationFee": 499.0,
  "active": true
}
```

### Team Registration
`POST /api/user/teams/register`
```json
{
  "eventId": "EVENT123",
  "members": [
    {
      "name": "Alice",
      "email": "alice@example.com",
      "phone": "9999999999",
      "college": "ABC College",
      "leader": true
    },
    {
      "name": "Bob",
      "email": "bob@example.com",
      "phone": "8888888888",
      "college": "ABC College",
      "leader": false
    }
  ],
  "formResponses": {
    "teamIdea": "Smart healthcare assistant",
    "contactPhone": "9999999999"
  }
}
```

### Razorpay Order Creation
`POST /api/user/payments/{teamId}/order`

Response:
```json
{
  "teamId": "TEAM123",
  "orderId": "order_N123456",
  "keyId": "rzp_test_xxxxx",
  "amount": 499.0,
  "currency": "INR"
}
```

### Razorpay Verification
`POST /api/user/payments/{teamId}/verify`
```json
{
  "razorpayOrderId": "order_N123456",
  "razorpayPaymentId": "pay_N123456",
  "razorpaySignature": "<signature>"
}
```

### Create Criteria
`POST /api/faculty/criteria`
```json
{
  "eventId": "EVENT123",
  "name": "Innovation",
  "maxMarks": 25
}
```

### Evaluate Team
`POST /api/faculty/evaluations`
```json
{
  "eventId": "EVENT123",
  "teamId": "TEAM123",
  "scores": [
    {
      "criterionId": "CRITERION_1",
      "marksGiven": 21
    },
    {
      "criterionId": "CRITERION_2",
      "marksGiven": 18
    }
  ]
}
```

### Leaderboard
`GET /api/leaderboard/EVENT123`
```json
[
  {
    "rank": 1,
    "teamId": "TEAM1",
    "teamName": "QuantumCoders48291",
    "totalScore": 89.5
  }
]
```

## Setup Instructions

### 1. Prerequisites
- Java 17+
- Maven 3.9+
- MongoDB running locally or remote cluster

### 2. Configure Environment Variables
```bash
export MONGODB_URI="mongodb://localhost:27017/hackathon_db"
export JWT_SECRET="replace-with-long-random-secret"
export JWT_EXPIRATION_MS="86400000"
export RAZORPAY_KEY_ID="rzp_test_..."
export RAZORPAY_KEY_SECRET="..."
export ADMIN_EMAIL="faculty@hackathon.com"
export ADMIN_PASSWORD="Admin@123"
```

### 3. Build and Run
```bash
mvn clean install
mvn spring-boot:run
```

### 4. Open UI
- Home: `http://localhost:8080/`
- Login: `http://localhost:8080/login`
- Register: `http://localhost:8080/register`
- User Dashboard: `http://localhost:8080/user`
- Faculty Dashboard: `http://localhost:8080/faculty`
- Leaderboard: `http://localhost:8080/leaderboard`

## Razorpay Integration Notes
- Order creation happens server-side in `PaymentService`.
- Verification uses HMAC SHA-256 (`orderId|paymentId`) with Razorpay secret.
- Signature mismatch marks payment as failed.
- For zero-fee events, payment is auto-marked as successful (`FREE_REGISTRATION`).

## Scalability / Production Considerations
- Add Redis caching for leaderboard and event reads
- Add pagination for teams/evaluations endpoints
- Add audit logging and activity tracing
- Add WebSocket for push-based live leaderboard at scale
- Add idempotency keys for payment and evaluation writes
- Add CI tests for service and security layers
