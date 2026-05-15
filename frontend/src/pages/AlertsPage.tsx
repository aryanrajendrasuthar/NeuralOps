import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { alertsApi, AlertRuleRequest, AlertMetric, AlertOperator } from '@/api/alerts'

const METRIC_LABELS: Record<AlertMetric, string> = {
  LATENCY_P99: 'p99 Latency (ms)',
  LATENCY_P95: 'p95 Latency (ms)',
  ERROR_RATE: 'Error Rate',
  ANOMALY_SCORE: 'Anomaly Score',
}

const OPERATOR_LABELS: Record<AlertOperator, string> = {
  GT: '>',
  LT: '<',
}

function CreateRuleForm({ onCreated }: { onCreated: () => void }) {
  const [form, setForm] = useState<AlertRuleRequest>({
    metric: 'LATENCY_P99',
    operator: 'GT',
    threshold: 2000,
    webhookUrl: '',
  })
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: alertsApi.createRule,
    onSuccess: () => { onCreated(); setError(null) },
    onError: (err: any) => setError(err?.response?.data?.detail ?? 'Failed to create rule.'),
  })

  function set(field: keyof AlertRuleRequest, value: string | number) {
    setForm((f) => ({ ...f, [field]: value }))
  }

  return (
    <div className="card p-5 mb-6 max-w-xl">
      <h2 className="text-sm font-medium text-gray-300 mb-4">New alert rule</h2>
      <div className="space-y-3">
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">Metric</label>
            <select
              value={form.metric}
              onChange={(e) => set('metric', e.target.value as AlertMetric)}
              className="w-full bg-gray-800 border border-gray-700 rounded-md px-2 py-1.5 text-sm text-white"
            >
              {(Object.keys(METRIC_LABELS) as AlertMetric[]).map((m) => (
                <option key={m} value={m}>{METRIC_LABELS[m]}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">Operator</label>
            <select
              value={form.operator}
              onChange={(e) => set('operator', e.target.value as AlertOperator)}
              className="w-full bg-gray-800 border border-gray-700 rounded-md px-2 py-1.5 text-sm text-white"
            >
              {(Object.keys(OPERATOR_LABELS) as AlertOperator[]).map((op) => (
                <option key={op} value={op}>{OPERATOR_LABELS[op]}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">Threshold</label>
            <input
              type="number"
              value={form.threshold}
              onChange={(e) => set('threshold', Number(e.target.value))}
              className="w-full bg-gray-800 border border-gray-700 rounded-md px-2 py-1.5 text-sm text-white"
            />
          </div>
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">Agent ID (optional — leave blank for all agents)</label>
          <input
            type="text"
            value={form.agentId ?? ''}
            onChange={(e) => set('agentId', e.target.value)}
            placeholder="agent-id or blank for global"
            className="w-full bg-gray-800 border border-gray-700 rounded-md px-2 py-1.5 text-sm text-white placeholder-gray-600"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">Webhook URL</label>
          <input
            type="url"
            value={form.webhookUrl}
            onChange={(e) => set('webhookUrl', e.target.value)}
            placeholder="https://hooks.example.com/alert"
            className="w-full bg-gray-800 border border-gray-700 rounded-md px-2 py-1.5 text-sm text-white placeholder-gray-600"
          />
        </div>

        {error && <p className="text-xs text-red-400">{error}</p>}

        <button
          onClick={() => mutation.mutate(form)}
          disabled={mutation.isPending || !form.webhookUrl}
          className="btn-primary text-sm disabled:opacity-50"
        >
          {mutation.isPending ? 'Creating...' : 'Create rule'}
        </button>
      </div>
    </div>
  )
}

export function AlertsPage() {
  const queryClient = useQueryClient()
  const { data: rules, isLoading } = useQuery({
    queryKey: ['alerts', 'rules'],
    queryFn: alertsApi.listRules,
    refetchInterval: 30_000,
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      alertsApi.setEnabled(id, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts', 'rules'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => alertsApi.deleteRule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts', 'rules'] }),
  })

  return (
    <div>
      <h1 className="text-xl font-semibold text-white mb-6">Alert Rules</h1>

      <CreateRuleForm onCreated={() => queryClient.invalidateQueries({ queryKey: ['alerts', 'rules'] })} />

      {isLoading && <p className="text-sm text-gray-500">Loading rules...</p>}

      {rules && rules.length === 0 && (
        <p className="text-sm text-gray-500">No alert rules configured yet.</p>
      )}

      {rules && rules.length > 0 && (
        <div className="space-y-2 max-w-3xl">
          {rules.map((rule) => (
            <div
              key={rule.id}
              className={`card p-4 flex items-center justify-between gap-4 ${
                !rule.isEnabled ? 'opacity-50' : ''
              }`}
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-mono text-gray-300">
                    {METRIC_LABELS[rule.metric]} {OPERATOR_LABELS[rule.operator]} {rule.threshold}
                  </span>
                  {rule.agentId ? (
                    <span className="text-xs bg-gray-800 text-gray-400 px-2 py-0.5 rounded">
                      {rule.agentId}
                    </span>
                  ) : (
                    <span className="text-xs bg-gray-800 text-gray-500 px-2 py-0.5 rounded">global</span>
                  )}
                  {!rule.isEnabled && (
                    <span className="text-xs text-gray-600">disabled</span>
                  )}
                </div>
                <p className="text-xs text-gray-600 truncate mt-0.5">{rule.webhookUrl}</p>
              </div>

              <div className="flex items-center gap-2 flex-shrink-0">
                <button
                  onClick={() => toggleMutation.mutate({ id: rule.id, enabled: !rule.isEnabled })}
                  className="text-xs text-gray-400 hover:text-white transition-colors"
                >
                  {rule.isEnabled ? 'Disable' : 'Enable'}
                </button>
                <button
                  onClick={() => {
                    if (confirm('Delete this alert rule?')) deleteMutation.mutate(rule.id)
                  }}
                  className="text-xs text-red-500 hover:text-red-400 transition-colors"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
