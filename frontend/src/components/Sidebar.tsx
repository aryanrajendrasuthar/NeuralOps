import { NavLink } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'

const navItems = [
  { to: '/', label: 'Overview', exact: true },
  { to: '/agents', label: 'Agents' },
  { to: '/alerts', label: 'Alerts' },
]

export function Sidebar() {
  const { user, logout } = useAuth()

  return (
    <aside className="w-56 flex-shrink-0 bg-gray-900 border-r border-gray-800 flex flex-col">
      <div className="px-5 py-4 border-b border-gray-800">
        <span className="text-lg font-bold text-white tracking-tight">NeuralOps</span>
        <p className="text-xs text-gray-500 mt-0.5">AI Observability</p>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1">
        {navItems.map(({ to, label, exact }) => (
          <NavLink
            key={to}
            to={to}
            end={exact}
            className={({ isActive }) =>
              `block px-3 py-2 rounded-md text-sm transition-colors duration-100 ${
                isActive
                  ? 'bg-gray-800 text-white font-medium'
                  : 'text-gray-400 hover:text-white hover:bg-gray-800'
              }`
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="px-4 py-4 border-t border-gray-800">
        <p className="text-xs text-gray-400 truncate mb-2">{user?.email}</p>
        <button
          onClick={() => logout()}
          className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
        >
          Sign out
        </button>
      </div>
    </aside>
  )
}
