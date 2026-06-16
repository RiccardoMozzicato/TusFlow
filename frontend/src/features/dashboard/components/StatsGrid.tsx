import StatCard from './StatCard'
import type { StatCard as StatCardModel } from '../types'

// Placeholder boxes shown while the stats load, matching the card footprint.
function SkeletonCard() {
  return (
    <div className="h-28 animate-pulse rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest" />
  )
}

const CARD_COUNT = 4

export default function StatsGrid({ stats }: { stats: StatCardModel[] | null }) {
  return (
    <div className="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
      {stats === null
        ? Array.from({ length: CARD_COUNT }, (_, index) => <SkeletonCard key={index} />)
        : stats.map((stat) => <StatCard key={stat.label} {...stat} />)}
    </div>
  )
}
