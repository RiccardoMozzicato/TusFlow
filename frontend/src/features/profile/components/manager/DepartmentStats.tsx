import Icon from '@/components/Icon'
import type { DepartmentStat, TrendTone } from '../../types'

interface DepartmentStatsProps {
  stats: DepartmentStat[]
}

const BADGE_TONE_CLASS: Record<TrendTone, string> = {
  positive: 'text-ds-on-tertiary-container',
  neutral: 'text-ds-on-surface-variant',
  urgent: 'text-ds-error',
}

function StatTile({
  label,
  value,
  unit,
  icon,
  iconClassName,
  badge,
  badgeTone = 'neutral',
}: DepartmentStat) {
  return (
    <div className="flex flex-col gap-3 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <span className={`rounded-ds-lg p-2 ${iconClassName}`}>
          <Icon name={icon} className="text-[24px]" />
        </span>
        {badge ? (
          <span className={`font-ds-label text-ds-label-sm ${BADGE_TONE_CLASS[badgeTone]}`}>
            {badge}
          </span>
        ) : null}
      </div>
      <div className="mt-4">
        <p className="font-ds-label text-ds-label-md uppercase tracking-wider text-ds-on-surface-variant">
          {label}
        </p>
        <h3 className="mt-1 font-ds-heading text-ds-display-lg text-ds-on-surface">
          {value}
          {unit ? (
            <span className="ml-1 font-ds-body text-ds-body-md font-normal text-ds-on-surface-variant">
              {unit}
            </span>
          ) : null}
        </h3>
      </div>
    </div>
  )
}

// Bento-style department KPI grid (manager only).
export default function DepartmentStats({ stats }: DepartmentStatsProps) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
      {stats.map((stat) => (
        <StatTile key={stat.label} {...stat} />
      ))}
    </div>
  )
}
