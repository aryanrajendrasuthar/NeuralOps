import { useQuery } from '@tanstack/react-query'
import { metricsApi } from '@/api/metrics'

export function useOverview() {
  return useQuery({
    queryKey: ['metrics', 'overview'],
    queryFn: metricsApi.getOverview,
    refetchInterval: 15_000,
  })
}

export function useAgentLatency(agentId: string) {
  return useQuery({
    queryKey: ['metrics', 'latency', agentId],
    queryFn: () => metricsApi.getLatency(agentId),
    refetchInterval: 10_000,
    enabled: !!agentId,
  })
}

export function useAgentStats(agentId: string) {
  return useQuery({
    queryKey: ['metrics', 'stats', agentId],
    queryFn: () => metricsApi.getStats(agentId),
    refetchInterval: 10_000,
    enabled: !!agentId,
  })
}

export function useAgentHourly(agentId: string, hours = 24) {
  return useQuery({
    queryKey: ['metrics', 'hourly', agentId, hours],
    queryFn: () => metricsApi.getHourly(agentId, hours),
    refetchInterval: 60_000,
    enabled: !!agentId,
  })
}
