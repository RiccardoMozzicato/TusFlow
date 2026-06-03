import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'
import type { TeamOverview } from '../../types'

interface TeamOverviewCardProps {
  team: TeamOverview
}

// Team summary card with stacked initials avatars (manager only).
export default function TeamOverviewCard({ team }: TeamOverviewCardProps) {
  return (
    <div className="relative overflow-hidden rounded-ds-xl border border-ds-primary bg-ds-primary-container p-8 text-ds-on-primary-container">
      <div className="relative z-10 space-y-4">
        <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-primary">{team.name}</h3>
        <div className="flex -space-x-2">
          {team.memberNames.map((member) => (
            <div
              key={member}
              title={member}
              className="flex h-8 w-8 items-center justify-center rounded-ds-full border-2 border-ds-primary-container bg-ds-surface-container-highest font-ds-label text-ds-label-sm font-bold text-ds-primary"
            >
              {initialsOf(member)}
            </div>
          ))}
          {team.extraCount > 0 ? (
            <div className="flex h-8 w-8 items-center justify-center rounded-ds-full border-2 border-ds-primary-container bg-ds-surface-container-highest text-[10px] font-bold text-ds-primary">
              +{team.extraCount}
            </div>
          ) : null}
        </div>
        <p className="font-ds-body text-ds-body-sm opacity-90">{team.summary}</p>
        <button
          type="button"
          className="font-ds-label text-ds-label-md text-ds-on-primary underline"
        >
          Manage Team Directory
        </button>
      </div>
      <Icon
        name="groups"
        className="absolute -bottom-4 -right-4 rotate-12 text-[120px] opacity-10"
      />
    </div>
  )
}
