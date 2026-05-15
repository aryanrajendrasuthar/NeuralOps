import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend, Counter } from 'k6/metrics'

const errorRate = new Rate('neuralops_error_rate')
const latencyTrend = new Trend('neuralops_ingestion_latency_ms', true)
const successCounter = new Counter('neuralops_successful_traces')

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m',  target: 200 },
    { duration: '3m',  target: 500 },
    { duration: '2m',  target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<2000'],
    neuralops_error_rate: ['rate<0.01'],
    http_req_failed: ['rate<0.01'],
  },
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const AGENT_IDS = ['load-agent-001', 'load-agent-002', 'load-agent-003', 'load-agent-004', 'load-agent-005']
const TRACE_TYPES = ['LLM_CALL', 'TOOL_INVOCATION', 'DECISION_POINT']

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

function randomElement(arr) {
  return arr[Math.floor(Math.random() * arr.length)]
}

function buildTraceEvent() {
  const isError = Math.random() < 0.02
  return {
    agentId: randomElement(AGENT_IDS),
    sessionId: `load-session-${randomInt(1, 20)}`,
    traceType: isError ? 'ERROR' : randomElement(TRACE_TYPES),
    payload: { model: 'llama3.1:8b', prompt_tokens: randomInt(50, 2000) },
    latencyMs: randomInt(50, 3000),
    tokenCount: randomInt(100, 4000),
    estimatedCostUsd: (randomInt(1, 100) / 10000).toFixed(6),
    timestamp: new Date().toISOString(),
    metadata: { env: 'load-test', run: __ENV.RUN_ID || 'local' },
  }
}

export default function () {
  const payload = JSON.stringify(buildTraceEvent())
  const params = {
    headers: { 'Content-Type': 'application/json' },
    timeout: '5s',
  }

  const res = http.post(`${BASE_URL}/api/v1/traces`, payload, params)

  const ok = check(res, {
    'status is 202': (r) => r.status === 202,
    'response has traceId': (r) => {
      try {
        return JSON.parse(r.body).traceId !== undefined
      } catch {
        return false
      }
    },
  })

  errorRate.add(!ok)
  latencyTrend.add(res.timings.duration)
  if (ok) successCounter.add(1)

  sleep(randomInt(1, 3) / 10)
}

export function handleSummary(data) {
  return {
    stdout: `
NeuralOps Load Test Summary
============================
Total requests:     ${data.metrics.http_reqs.values.count}
Successful (202):   ${data.metrics.neuralops_successful_traces.values.count}
Error rate:         ${(data.metrics.neuralops_error_rate.values.rate * 100).toFixed(2)}%
p50 latency:        ${data.metrics.neuralops_ingestion_latency_ms.values['p(50)'].toFixed(0)}ms
p95 latency:        ${data.metrics.neuralops_ingestion_latency_ms.values['p(95)'].toFixed(0)}ms
p99 latency:        ${data.metrics.neuralops_ingestion_latency_ms.values['p(99)'].toFixed(0)}ms
Throughput:         ${data.metrics.http_reqs.values.rate.toFixed(1)} req/s peak
`,
  }
}
