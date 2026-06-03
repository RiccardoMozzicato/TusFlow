import type { ReactNode } from 'react'
import DetailSection from './DetailSection'

interface MetadataSectionProps {
  category: string
  tags: string[]
  owner: string
  date: string
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

// A single label/value row. The last row drops its dashed divider.
function Row({ label, value, last }: { label: string; value: ReactNode; last?: boolean }) {
  return (
    <div
      className={`flex items-center justify-between py-1 ${
        last ? '' : 'border-b border-dashed border-ds-surface-container'
      }`}
    >
      <span className="font-ds-body text-ds-body-sm text-ds-secondary">{label}</span>
      {value}
    </div>
  )
}

// Key document properties: category, tags, owner, and date.
export default function MetadataSection({ category, tags, owner, date }: MetadataSectionProps) {
  return (
    <DetailSection icon="info" title="Metadata">
      <div className="grid grid-cols-1 gap-y-3">
        <Row
          label="Category"
          value={
            <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
              {category}
            </span>
          }
        />
        <Row
          label="Tags"
          value={
            <div className="flex flex-wrap justify-end gap-1">
              {tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-ds-full bg-ds-surface-variant px-2 py-0.5 font-ds-label text-ds-label-sm font-medium text-ds-on-surface"
                >
                  {tag}
                </span>
              ))}
            </div>
          }
        />
        <Row
          label="Owner"
          value={
            <div className="flex items-center gap-2">
              <div className="flex h-5 w-5 items-center justify-center rounded-ds-full bg-ds-primary-container font-ds-label text-ds-label-sm font-bold text-ds-on-primary-container">
                {initialsOf(owner)}
              </div>
              <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
                {owner}
              </span>
            </div>
          }
        />
        <Row
          label="Date"
          last
          value={
            <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
              {date}
            </span>
          }
        />
      </div>
    </DetailSection>
  )
}
