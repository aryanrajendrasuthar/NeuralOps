import { Routes, Route, Navigate } from 'react-router-dom'

const Dashboard = () => (
  <div className="flex items-center justify-center h-screen">
    <div className="text-center">
      <h1 className="text-3xl font-bold text-white mb-2">NeuralOps</h1>
      <p className="text-gray-400">AI Agent Observability Platform</p>
      <p className="text-gray-500 text-sm mt-4">Dashboard will be built in Sprint 5</p>
    </div>
  </div>
)

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
