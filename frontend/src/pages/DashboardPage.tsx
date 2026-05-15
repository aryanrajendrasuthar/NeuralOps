import { useOverview } from '@/hooks/useMetrics'
import { MetricCard } from '@/components/MetricCard'

export function DashboardPage() {
  const { data: overview, isLoading, isError } = useOverview()

  return (
    <div>
      <h1 className="text-xl font-semibold text-white mb-6">Overview</h1>

      {isError && (
        <div className="bg-red-950/40 border border-red-800 rounded-md px-4 py-3 text-sm text-red-400 mb-6">
          Failed to load overview metrics. The metrics service may be starting up.
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 mb-8 max-w-lg">
        <MetricCard
          label="Active Agents"
          value={isLoading ? '—' : (overview?.activeAgents ?? 0).toLocaleString()}
          sub="seen in last 24h"
        />
        <MetricCard
          label="Traces Today"
          value={isLoading ? '—' : (overview?.totalTracesToday ?? 0).toLocaleString()}
          sub="total ingested"
        />
      </div>

      <div className="card p-6 max-w-2xl">
        <h2 className="text-sm font-medium text-gray-400 mb-3">Getting started</h2>
        <div className="space-y-3 text-sm text-gray-400">
          <p>
            Select an agent from the <span className="text-white font-medium">Agents</span> page
            to view real-time latency percentiles, error rates, cost breakdown, and anomaly status.
          </p>
          <p>
            Configure threshold alerts from the{' '}
            <span className="text-white font-medium">Alerts</span> page. When a rule fires, NeuralOps
            delivers an HTTP POST to your configured webhook URL within seconds.
          </p>
        </div>
      </div>
    </div>
  )
}
