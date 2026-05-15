import { useState } from 'react'
import { useAgentLatency, useAgentStats, useAgentHourly } from '@/hooks/useMetrics'
import { MetricCard } from '@/components/MetricCard'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import { format } from 'date-fns'

function LatencyChart({ agentId }: { agentId: string }) {
  const { data, isLoading } = useAgentHourly(agentId, 24)

  if (isLoading) return <div className="h-48 flex items-center justify-center text-gray-600 text-sm">Loading chart...</div>
  if (!data || data.length === 0) return <div className="h-48 flex items-center justify-center text-gray-600 text-sm">No hourly data yet.</div>

  const formatted = data.map((d) => ({
    ...d,
    hour: format(new Date(d.bucket), 'HH:mm'),
  }))

  return (
    <ResponsiveContainer width="100%" height={200}>
      <LineChart data={formatted} margin={{ top: 4, right: 4, left: -16, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
        <XAxis dataKey="hour" tick={{ fill: '#6b7280', fontSize: 11 }} />
        <YAxis tick={{ fill: '#6b7280', fontSize: 11 }} unit="ms" />
        <Tooltip
          contentStyle={{ backgroundColor: '#111827', border: '1px solid #374151', fontSize: 12 }}
          labelStyle={{ color: '#9ca3af' }}
        />
        <Legend iconSize={10} wrapperStyle={{ fontSize: 12 }} />
        <Line type="monotone" dataKey="p95Ms" name="p95" stroke="#6366f1" dot={false} strokeWidth={2} />
        <Line type="monotone" dataKey="p99Ms" name="p99" stroke="#f59e0b" dot={false} strokeWidth={2} />
      </LineChart>
    </ResponsiveContainer>
  )
}

function AgentDetail({ agentId }: { agentId: string }) {
  const { data: latency, isLoading: latencyLoading } = useAgentLatency(agentId)
  const { data: stats, isLoading: statsLoading } = useAgentStats(agentId)

  const errorStatus =
    (stats?.errorRatePct ?? 0) > 10 ? 'error'
      : (stats?.errorRatePct ?? 0) > 5 ? 'warning'
      : 'healthy'

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <MetricCard
          label="p50 Latency"
          value={latencyLoading ? '—' : `${(latency?.p50Ms ?? 0).toFixed(0)}ms`}
        />
        <MetricCard
          label="p95 Latency"
          value={latencyLoading ? '—' : `${(latency?.p95Ms ?? 0).toFixed(0)}ms`}
          status={(latency?.p95Ms ?? 0) > 2000 ? 'warning' : 'neutral'}
        />
        <MetricCard
          label="p99 Latency"
          value={latencyLoading ? '—' : `${(latency?.p99Ms ?? 0).toFixed(0)}ms`}
          status={(latency?.p99Ms ?? 0) > 5000 ? 'error' : 'neutral'}
        />
        <MetricCard
          label="Samples"
          value={latencyLoading ? '—' : (latency?.sampleCount ?? 0).toLocaleString()}
          sub="in latency window"
        />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <MetricCard
          label="Total Traces"
          value={statsLoading ? '—' : (stats?.traceCount ?? 0).toLocaleString()}
        />
        <MetricCard
          label="Error Rate"
          value={statsLoading ? '—' : `${(stats?.errorRatePct ?? 0).toFixed(1)}%`}
          status={errorStatus}
        />
        <MetricCard
          label="Tokens Used"
          value={statsLoading ? '—' : (stats?.tokenCount ?? 0).toLocaleString()}
        />
        <MetricCard
          label="Cost (USD)"
          value={statsLoading ? '—' : `$${(stats?.costUsdTotal ?? 0).toFixed(4)}`}
        />
      </div>

      <div className="card p-5">
        <h3 className="text-sm font-medium text-gray-400 mb-4">Latency — last 24h (hourly p95/p99)</h3>
        <LatencyChart agentId={agentId} />
      </div>
    </div>
  )
}

export function AgentsPage() {
  const [agentId, setAgentId] = useState('')
  const [activeAgent, setActiveAgent] = useState<string | null>(null)

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    if (agentId.trim()) setActiveAgent(agentId.trim())
  }

  return (
    <div>
      <h1 className="text-xl font-semibold text-white mb-6">Agents</h1>

      <form onSubmit={handleSearch} className="flex gap-3 mb-8 max-w-md">
        <input
          value={agentId}
          onChange={(e) => setAgentId(e.target.value)}
          placeholder="Enter agent ID..."
          className="flex-1 bg-gray-800 border border-gray-700 rounded-md px-3 py-2 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-gray-500"
        />
        <button type="submit" className="btn-primary text-sm">View</button>
      </form>

      {activeAgent && (
        <div>
          <p className="text-sm text-gray-500 mb-4">
            Showing metrics for <span className="text-gray-300 font-mono">{activeAgent}</span>
          </p>
          <AgentDetail agentId={activeAgent} />
        </div>
      )}

      {!activeAgent && (
        <div className="card p-6 max-w-md text-sm text-gray-500">
          Enter an agent ID above to view its real-time latency, error rate, token usage, and cost metrics.
        </div>
      )}
    </div>
  )
}
