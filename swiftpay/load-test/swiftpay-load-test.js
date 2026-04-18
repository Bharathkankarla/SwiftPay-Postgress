import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const paymentSuccess   = new Counter("payment_success_total");
const paymentFailed    = new Counter("payment_failed_total");
const errorRate        = new Rate("payment_error_rate");
const paymentDuration  = new Trend("payment_duration_ms", true);

// ---------------------------------------------------------------------------
// Test configuration — 250 TPS for ~1 million transactions
// 250 VUs * 1 iter/s ≈ 250 TPS  |  4000 s ≈ 67 min → ~1 000 000 iterations
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    sustained_load: {
      executor:          "constant-arrival-rate",
      rate:              250,       // 250 iterations per second = 250 TPS
      timeUnit:          "1s",
      duration:          "67m",     // enough for ~1 000 000 transactions
      preAllocatedVUs:   300,
      maxVUs:            500,
    },
  },
  thresholds: {
    http_req_duration:    ["p(95)<2000"],   // 95% of requests under 2 s
    payment_error_rate:   ["rate<0.01"],    // less than 1% errors
    http_req_failed:      ["rate<0.01"],
  },
};

// ---------------------------------------------------------------------------
// Pre-seeded demo users (created by DemoDataInitializer on app start)
// Add more pairs here to distribute load across wallets
// ---------------------------------------------------------------------------
const USER_PAIRS = [
  { sender: "sender-1",  receiver: "receiver-1", password: "demo123" },
  { sender: "receiver-1", receiver: "sender-2",  password: "demo123" },
  { sender: "sender-2",  receiver: "sender-1",   password: "demo123" },
];

const BASE_URL = __ENV.BASE_URL || "http://localhost:8085";

// ---------------------------------------------------------------------------
// Token cache — one JWT per sender, refreshed lazily
// ---------------------------------------------------------------------------
const tokenCache = {};

function getToken(userId, password) {
  if (tokenCache[userId]) return tokenCache[userId];

  const res = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({ userId, password }),
    { headers: { "Content-Type": "application/json" } }
  );

  if (res.status === 200) {
    tokenCache[userId] = JSON.parse(res.body).accessToken;
  }
  return tokenCache[userId];
}

// ---------------------------------------------------------------------------
// Default function — called once per virtual iteration
// ---------------------------------------------------------------------------
export default function () {
  const pair  = USER_PAIRS[Math.floor(Math.random() * USER_PAIRS.length)];
  const token = getToken(pair.sender, pair.password);
  if (!token) return;

  const txnId  = `txn-${__VU}-${__ITER}-${Date.now()}`;
  const amount = (Math.random() * 9 + 1).toFixed(2);  // $1.00 – $10.00

  const payload = JSON.stringify({
    transactionId: txnId,
    senderId:      pair.sender,
    receiverId:    pair.receiver,
    amount:        parseFloat(amount),
    currency:      "USD",
  });

  const start = Date.now();
  const res   = http.post(`${BASE_URL}/v1/payments`, payload, {
    headers: {
      "Content-Type":  "application/json",
      "Authorization": `Bearer ${token}`,
    },
  });
  paymentDuration.add(Date.now() - start);

  const ok = check(res, {
    "status is 202": (r) => r.status === 202,
    "has transactionId": (r) => {
      try { return JSON.parse(r.body).transactionId !== undefined; }
      catch { return false; }
    },
  });

  if (ok) {
    paymentSuccess.add(1);
    errorRate.add(false);
  } else {
    paymentFailed.add(1);
    errorRate.add(true);
  }
}

// ---------------------------------------------------------------------------
// Summary printed at the end of the test
// ---------------------------------------------------------------------------
export function handleSummary(data) {
  const out = {
    "load-test/results/summary.json": JSON.stringify(data, null, 2),
  };
  // Also print to stdout
  return out;
}
