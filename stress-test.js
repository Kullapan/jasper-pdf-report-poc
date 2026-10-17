import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    // Stage 1: Warmup & Low Load (5 VUs) - 10s
    { duration: '10s', target: 5 },
    // Stage 2: Normal / Moderate Load (15 VUs) - 20s
    { duration: '20s', target: 15 },
    // Stage 3: High Load (30 VUs) - 20s
    { duration: '20s', target: 30 },
    // Stage 4: Stress / Peak Load (50 VUs) - 20s
    { duration: '20s', target: 50 },
    // Stage 5: Breaking Point Test (70 VUs) - 20s
    { duration: '20s', target: 70 },
    // Stage 6: Cooldown & Recovery (0 VUs) - 10s
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'], // failure rate should be less than 5%
  },
};

const REPORT_ID = '684d781e-b21a-43a7-a7ef-3d11c902acbd';
const URL = `http://localhost:8080/api/reports/${REPORT_ID}/content?version=1`;

export default function () {
  const res = http.get(URL, { timeout: '30s' });
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has 40k+ bytes pdf': (r) => r.body && r.body.length > 40000,
  });

  // Short realistic pause between user requests (100ms - 300ms)
  sleep(0.2);
}
