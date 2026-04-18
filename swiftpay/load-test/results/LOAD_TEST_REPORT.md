# SwiftPay Load Test Report

## Test Configuration

| Parameter | Value |
|---|---|
| Tool | K6 v0.55.0 |
| Target | POST /v1/payments (end-to-end payment flow) |
| Host | http://localhost:8085 (Docker Compose stack) |
| Virtual Users | 50 VUs (looping) |
| Duration | 30 seconds (sustained) |
| Stack | Spring Boot 3.5 + PostgreSQL 15 + Kafka 7.4 + Redis 7 |

---

## Results Summary

### Throughput

| Metric | Value |
|---|---|
| **Total Transactions** | 13,209 payments |
| **HTTP Requests** | 13,359 (includes token refresh calls) |
| **Achieved TPS** | **444 TPS** ✅ (target: 250 TPS) |
| **Payment Success Rate** | **100%** (13,209 / 13,209) |
| **Error Rate** | **0.00%** ✅ (threshold: < 1%) |

### Latency (ms)

| Percentile | Value |
|---|---|
| Min | 8.9 ms |
| Median (p50) | 92.6 ms |
| p90 | 188.3 ms |
| **p95** | **234.6 ms** ✅ (threshold: < 2000 ms) |
| Max | 1819.2 ms |
| Average | 111.8 ms |

### Checks

| Check | Passed | Failed |
|---|---|---|
| HTTP status is 202 | 13,209 | 0 |
| Response has transactionId | 13,209 | 0 |

---

## Threshold Evaluation

| Threshold | Result |
|---|---|
| `p(95) < 2000ms` | ✅ PASSED (234.6 ms) |
| `http_req_failed rate < 1%` | ✅ PASSED (0.00%) |
| `payment_error_rate < 1%` | ✅ PASSED (0.00%) |

---

## Extrapolation to 1,000,000 Transactions

At the sustained rate of **444 TPS**:

| Metric | Value |
|---|---|
| Time to 1M transactions | ~2,252 seconds (~37.5 minutes) |
| Projected error count | ~0 (0% error rate) |
| Projected p95 latency | ~235 ms |

At the required **250 TPS**:

| Metric | Value |
|---|---|
| Time to 1M transactions | ~4,000 seconds (~66.7 minutes) |
| Required VUs | ~28 VUs (system has headroom) |

> The system comfortably exceeds the 250 TPS requirement by **78%**, achieving 444 TPS
> with 50 virtual users at zero error rate.

---

## Infrastructure

```
docker-compose up --build

Services:
  swiftpay-app       → :8085   (Spring Boot 3.5 / Java 21)
  swiftpay-postgres  → :5433   (PostgreSQL 15)
  swiftpay-kafka     → :9092   (Confluent Kafka 7.4.0)
  swiftpay-redis     → :6379   (Redis 7)
  swiftpay-zookeeper → :2181
```

---

## Raw Metrics

See [summary.json](summary.json) for the full K6 machine-readable output.
