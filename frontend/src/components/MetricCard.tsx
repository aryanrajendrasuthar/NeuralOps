interface MetricCardProps {
  label: string
  value: string | number
  sub?: string
  status?: 'healthy' | 'warning' | 'error' | 'neutral'
}

export function MetricCard({ label, value, sub, status = 'neutral' }: MetricCardProps) {
  const statusClass = {
    healthy: 'text-emerald-400',
    warning: 'text-amber-400',
    error: 'text-red-400',
    neutral: 'text-white',
  }[status]

  return (
    <div className="metric-card">
      <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">{label}</p>
      <p className={`text-2xl font-bold tabular-nums ${statusClass}`}>{value}</p>
      {sub && <p className="text-xs text-gray-500 mt-1">{sub}</p>}
    </div>
  )
}
