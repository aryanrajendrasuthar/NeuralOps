import { apiClient } from './client'

export type AlertMetric = 'LATENCY_P99' | 'LATENCY_P95' | 'ERROR_RATE' | 'ANOMALY_SCORE'
export type AlertOperator = 'GT' | 'LT'
export type WebhookStatus = 'PENDING' | 'DELIVERED' | 'FAILED'

export interface AlertRule {
  id: number
  agentId: string | null
  metric: AlertMetric
  operator: AlertOperator
  threshold: number
  webhookUrl: string
  isEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface AlertEvent {
  id: number
  ruleId: number
  agentId: string
  metric: AlertMetric
  metricValue: number
  threshold: number
  triggeredAt: string
  webhookStatus: WebhookStatus
  webhookAttempts: number
}

export interface AlertRuleRequest {
  agentId?: string
  metric: AlertMetric
  operator: AlertOperator
  threshold: number
  webhookUrl: string
}

export const alertsApi = {
  listRules: () => apiClient.get<AlertRule[]>('/alerts/rules').then((r) => r.data),
  createRule: (body: AlertRuleRequest) =>
    apiClient.post<AlertRule>('/alerts/rules', body).then((r) => r.data),
  deleteRule: (id: number) => apiClient.delete(`/alerts/rules/${id}`),
  setEnabled: (id: number, enabled: boolean) =>
    apiClient.patch<AlertRule>(`/alerts/rules/${id}/enabled`, { enabled }).then((r) => r.data),
  getHistory: (agentId: string, page = 0, size = 20) =>
    apiClient
      .get<{ content: AlertEvent[]; totalElements: number }>(
        `/alerts/agents/${agentId}/history?page=${page}&size=${size}`
      )
      .then((r) => r.data),
}
