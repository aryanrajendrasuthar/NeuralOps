import { apiClient } from './client'

export interface AnomalyStatus {
  agent_id: string
  current_anomaly_score: number
  is_currently_anomalous: boolean
  last_checked_at: string | null
  recent_anomaly_count: number
}

export interface AnomalyInsight extends AnomalyStatus {
  insight: string | null
}

export const anomaliesApi = {
  getStatus: (agentId: string) =>
    apiClient.get<AnomalyStatus>(`/anomalies/${agentId}/status`).then((r) => r.data),
  getInsight: (agentId: string) =>
    apiClient.get<AnomalyInsight>(`/anomalies/${agentId}/insight`).then((r) => r.data),
}
