# SwiftPay Load Test — 250 TPS / 1 Million Transactions

## Prerequisites

1. **K6** installed — https://k6.io/docs/get-started/installation/
2. **Wireshark / tshark** for PCAP capture
3. App stack running: `docker-compose up --build`

---

## Step 1 — Seed enough wallet balance

The demo users start with a fixed balance. Before running 1M transactions
make sure their balance is high enough, or set a very small amount per txn.
The script uses $1–$10 per transfer — adjust `DemoDataInitializer` initial
balance to e.g. 999_999_999 before the test run.

---

## Step 2 — Start PCAP capture (in a separate terminal)

### Windows (tshark):
```bash
tshark -i Loopback -f "tcp port 8085" -w load-test/results/swiftpay.pcap
```

### Linux/Mac:
```bash
sudo tcpdump -i lo -f "tcp port 8085" -w load-test/results/swiftpay.pcap &
```

---

## Step 3 — Run the load test

```bash
# Create results directory
mkdir -p load-test/results

# Run at 250 TPS for ~1 million transactions (~67 minutes)
k6 run load-test/swiftpay-load-test.js

# Or point to a remote host:
k6 run -e BASE_URL=http://<your-host>:8085 load-test/swiftpay-load-test.js
```

---

## Step 4 — Stop PCAP capture

Stop `tshark` / `tcpdump` after the test finishes.

---

## Step 5 — Collect results

After the run, two files will be in `load-test/results/`:
- `summary.json` — full metrics (throughput, latency, error rate)
- `swiftpay.pcap` — network capture

---

## Expected output (passing thresholds)

| Metric | Target |
|---|---|
| Throughput | ≥ 250 TPS sustained |
| p95 latency | < 2000 ms |
| Error rate | < 1% |
| Total transactions | ~1 000 000 |

---

## Quick smoke test (1 minute, low load)

```bash
k6 run --duration 1m --vus 10 load-test/swiftpay-load-test.js
```
