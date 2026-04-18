# SwiftPay

SwiftPay is a Spring Boot payment-ledger service built for the hackathon challenge. It accepts peer-to-peer payment requests, stores them as `PENDING`, publishes a Kafka event, processes the transfer atomically in the ledger layer, and records double-entry ledger rows for auditability.

## Stack

- Java 21
- Spring Boot 3
- PostgreSQL
- Redis
- Apache Kafka
- Swagger / OpenAPI
- Docker / Docker Compose
- GitHub Actions

## Architecture Flow

1. `POST /v1/payments` validates the request and checks sender balance.
2. Redis reserves the `transactionId` for 24-hour idempotency.
3. PostgreSQL stores the transaction with `PENDING` status.
4. Kafka publishes `payment.initiated`.
5. Ledger consumer locks both wallet rows and performs atomic debit/credit.
6. Two ledger entries are written: one `DEBIT`, one `CREDIT`.
7. Transaction becomes `SUCCESS` or `FAILED`.
8. Kafka publishes the result event.

## Demo Users

These users are auto-seeded on startup when `swiftpay.demo.seed-users=true`:

- `sender-1` / balance `500.00 USD`
- `receiver-1` / balance `100.00 USD`
- `sender-2` / balance `50.00 USD`
- default password for all seeded users: `demo123`

## Run Locally

Start infrastructure:

```bash
docker compose up -d postgres redis zookeeper kafka
```

If you change Kafka listener settings in `docker-compose.yml`, recreate infrastructure so the broker picks them up:

```bash
docker compose down
docker compose up -d postgres redis zookeeper kafka
```

Run the app:

```bash
mvn spring-boot:run
```

Or run the full stack:

```bash
docker compose up --build
```

## Swagger And Health

- Demo UI: `http://localhost:8085/`
- Swagger UI: `http://localhost:8085/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8085/api-docs`
- Health: `http://localhost:8085/actuator/health`
- Docker PostgreSQL: `localhost:5433`
- Host Kafka bootstrap server: `localhost:9092`
- JDBC timezone override: `UTC`

## API Demo

Create a user:

```http
POST /v1/users
Content-Type: application/json

{
  "id": "demo-1",
  "fullName": "Demo User",
  "email": "demo@swiftpay.com",
  "mobileNumber": "9000000010",
  "balance": 250.00,
  "currency": "USD"
}
```

Create a payment:

```http
POST /v1/payments
Content-Type: application/json
Authorization: Bearer <jwt>

{
  "transactionId": "txn-1001",
  "senderId": "sender-1",
  "receiverId": "receiver-1",
  "amount": 25.00,
  "currency": "USD"
}
```

Fetch transaction:

```http
GET /v1/payments/txn-1001
```

Fetch user transaction history:

```http
GET /v1/payments/history/sender-1
```

Fetch user ledger history:

```http
GET /v1/payments/ledger/sender-1
```

## Testing

Run tests:

```bash
mvn test
```

Current test coverage includes:

- pending transaction creation
- successful ledger transfer
- insufficient funds validation
- duplicate idempotency handling
- transaction history retrieval
- user creation and lookup

## Notes For Presentation

- Show `POST /v1/auth/login` first, then use the token in Swagger or the UI.
- Highlight idempotency with Redis and `transactionId` reuse.
- Highlight row-level locking on users for safer concurrent balance mutation.
- Highlight double-entry ledger rows for audit trail.
- Highlight async decoupling between API intake and ledger processing through Kafka.
