import StatCard from './StatCard'
import { STATS } from '../mockData'

export default function StatsGrid() {
  return (
    <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
      {STATS.map((stat) => (
        <StatCard key={stat.label} {...stat} />
      ))}
    </div>
  )
}
