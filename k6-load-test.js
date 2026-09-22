import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const createPaymentTrend = new Trend('create_payment_duration');
const getPaymentTrend = new Trend('get_payment_duration');

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp-up to 10 users
    { duration: '1m30s', target: 50 }, // Ramp-up to 50 users
    { duration: '1m', target: 100 },   // Ramp-up to 100 users
    { duration: '2m', target: 100 },   // Stay at 100 users
    { duration: '30s', target: 0 },    // Ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<1000', 'p(99)<2000'], // 95th percentile < 1s, 99th < 2s
    'http_req_failed': ['rate<0.1'],                   // Error rate < 10%
    'errors': ['rate<0.1'],                            // Custom error rate < 10%
  },
};

// Base URL for API
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || 'Bearer valid-test-token';

export default function () {
  group('Payment Creation', () => {
    const idempotencyKey = `idempotency-${Date.now()}-${Math.random()}`;
    
    const payload = JSON.stringify({
      merchantId: 'merchant-' + Math.floor(Math.random() * 100),
      idempotencyKey: idempotencyKey,
      amount: 100 + Math.floor(Math.random() * 9900), // 100-10000
      currency: 'INR',
      paymentMethod: 'CARD',
      description: 'Test payment',
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': JWT_TOKEN,
      },
    };

    const createResponse = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
    createPaymentTrend.add(createResponse.timings.duration);

    const success = check(createResponse, {
      'Payment created with status 200': (r) => r.status === 200,
      'Payment has ID': (r) => r.json('id') !== null,
      'Payment status is CREATED': (r) => r.json('status') === 'CREATED',
    });

    if (!success) {
      errorRate.add(1);
    }

    // Extract payment ID for subsequent test
    const paymentId = createResponse.json('id');

    if (paymentId) {
      sleep(1);

      group('Payment Retrieval', () => {
        const getResponse = http.get(
          `${BASE_URL}/api/v1/payments/${paymentId}`,
          { headers: { 'Authorization': JWT_TOKEN } }
        );

        getPaymentTrend.add(getResponse.timings.duration);

        check(getResponse, {
          'Payment retrieved with status 200': (r) => r.status === 200,
          'Payment ID matches': (r) => r.json('id') === paymentId,
        });
      });
    }
  });

  group('Idempotency Test', () => {
    const idempotencyKey = `idempotency-test-${Date.now()}`;
    
    const payload = JSON.stringify({
      merchantId: 'merchant-test',
      idempotencyKey: idempotencyKey,
      amount: 5000,
      currency: 'INR',
      paymentMethod: 'CARD',
      description: 'Idempotency test',
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': JWT_TOKEN,
      },
    };

    // First request
    const response1 = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
    const paymentId1 = response1.json('id');

    sleep(1);

    // Duplicate request with same idempotency key
    const response2 = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
    const paymentId2 = response2.json('id');

    check(response1, {
      'First request successful': (r) => r.status === 200,
    });

    check(response2, {
      'Duplicate request successful': (r) => r.status === 200,
      'Same payment ID returned (idempotency works)': () => paymentId1 === paymentId2,
    });
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

// Simple text summary function
function textSummary(data, options) {
  const indent = options?.indent || '';
  const summary = [];

  summary.push('\n=== Load Test Summary ===');
  
  if (data.metrics) {
    for (const [name, metric] of Object.entries(data.metrics)) {
      if (metric.values) {
        summary.push(`${indent}${name}:`);
        for (const [statName, value] of Object.entries(metric.values)) {
          summary.push(`${indent}  ${statName}: ${value}`);
        }
      }
    }
  }

  return summary.join('\n');
}
