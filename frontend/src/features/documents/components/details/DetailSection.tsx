import type { ReactNode } from 'react'
import Icon from '@/components/Icon'

interface DetailSectionProps {
  /** Material Symbols icon name for the section heading. */
  icon: string
  /** Heading text; rendered uppercase via styling. */
  title: string
  /** Optional trailing control (e.g. a "View All" link). */
  action?: ReactNode
  /** Highlights the card with a blue accent edge + tint (used for the AI panel). */
  accent?: boolean
  children: ReactNode
}

// Shared card shell for the right-hand metadata panel sections. Keeps the
// border/heading treatment in one place so every section reads identically.
export default function DetailSection({
  icon,
  title,
  action,
  accent = false,
  children,
}: DetailSectionProps) {
  return (
    <section className="rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        {accent ? (
          // Highlighted chip heading (used for the AI panel).
          <h3 className="flex w-fit items-center gap-1 rounded bg-ds-tertiary-fixed px-2 py-0.5 font-ds-label text-ds-label-sm uppercase tracking-wider text-ds-on-tertiary-fixed">
            <Icon name={icon} filled className="text-[12px]" />
            {title}
          </h3>
        ) : (
          <h3 className="flex items-center gap-2 font-ds-label text-ds-label-md uppercase tracking-wider text-ds-secondary">
            <Icon name={icon} filled={false} className="text-[16px]" />
            {title}
          </h3>
        )}
        {action}
      </div>
      {children}
    </section>
  )
}
