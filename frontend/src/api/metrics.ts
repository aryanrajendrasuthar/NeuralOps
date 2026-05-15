import { apiClient } from './client'

export interface AgentLatency {
  agentId: string
  p50Ms: number
  p95Ms: number
  p99Ms: number
  sampleCount: number
}

export interface AgentStats {
  agentId: string
  traceCount: number
  tokenCount: number
  errorCount: number
  errorRatePct: number
  costUsdTotal: number
}

export interface HourlyPoint {
  bucket: string
  p95Ms: number
  p99Ms: number
  errorCount: number
  costUsd: number
}

export interface Overview {
  activeAgents: number
  totalTracesToday: number
}

export const metricsApi = {
  getOverview: () => apiClient.get<Overview>('/metrics/overview').then((r) => r.data),
  getLatency: (agentId: string) =>
    apiClient.get<AgentLatency>(`/metrics/agents/${agentId}/latency`).then((r) => r.data),
  getStats: (agentId: string) =>
    apiClient.get<AgentStats>(`/metrics/agents/${agentId}/stats`).then((r) => r.data),
  getHourly: (agentId: string, hours = 24) =>
    apiClient.get<HourlyPoint[]>(`/metrics/agents/${agentId}/hourly?hours=${hours}`).then((r) => r.data),
}
