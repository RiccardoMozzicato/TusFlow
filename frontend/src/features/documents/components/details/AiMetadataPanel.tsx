import { useState, type ReactNode } from 'react'
import Icon from '@/components/Icon'
import type { SensitivityLevel } from '../../detailsApi'
import type { AiAnalysisStatus } from '../../types'
import { formatSensitivity } from '../../sensitivity'
import DetailSection from './DetailSection'
import MetadataEditForm from './MetadataEditForm'

interface AiMetadataPanelProps {
  /** AI analysis outcome, shown as a small indicator in the header. */
  analysisStatus: AiAnalysisStatus
  summary: string | null
  sensitivity: string
  tags: string[]
  /** Whether the current user may edit the AI metadata (write access). */
  canEdit: boolean
  isSaving: boolean
  saveError: string | null
  /** Persists the edited summary/sensitivity/tags; rejects on failure. */
  onSave: (changes: {
    summary: string
    sensitivity: SensitivityLevel
    tags: string[]
  }) => Promise<void>
}

// Visual treatment for each analysis outcome: icon, label, and text color.
const ANALYSIS_INDICATOR: Record<
  AiAnalysisStatus,
  { icon: string; label: string; className: string; pulse?: boolean }
> = {
  completed: { icon: 'check_circle', label: 'Analyzed', className: 'text-ds-primary' },
  pending: { icon: 'hourglass_empty', label: 'Analyzing…', className: 'text-ds-secondary', pulse: true },
  failed: { icon: 'error', label: 'Analysis failed', className: 'text-ds-error' },
  unsupported: { icon: 'do_not_disturb_on', label: 'Not supported', className: 'text-ds-secondary' },
}

// Small inline indicator of whether AI analysis ran successfully.
function AnalysisIndicator({ status }: { status: AiAnalysisStatus }) {
  const { icon, label, className, pulse } = ANALYSIS_INDICATOR[status]
  return (
    <span
      className={`inline-flex items-center gap-1 font-ds-label text-ds-label-sm ${className}`}
      title={`AI analysis: ${label}`}
    >
      <Icon name={icon} filled={false} className={`text-[14px] ${pulse ? 'animate-pulse' : ''}`} />
      {label}
    </span>
  )
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

// Combined panel for the AI-generated, user-editable metadata: the generated
// summary plus sensitivity and tags. All three are edited together in place by
// users with write access.
export default function AiMetadataPanel({
  analysisStatus,
  summary,
  sensitivity,
  tags,
  canEdit,
  isSaving,
  saveError,
  onSave,
}: AiMetadataPanelProps) {
  const [isEditing, setIsEditing] = useState(false)

  // Wrap the parent's save so a successful persist exits edit mode; a failure
  // keeps the form open with the error surfaced via `saveError`.
  async function handleSave(changes: {
    summary: string
    sensitivity: SensitivityLevel
    tags: string[]
  }) {
    try {
      await onSave(changes)
      setIsEditing(false)
    } catch {
      // Error is rendered from the `saveError` prop; stay in edit mode.
    }
  }

  const editAction =
    canEdit && !isEditing ? (
      <button
        type="button"
        onClick={() => setIsEditing(true)}
        className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-primary transition-colors hover:text-ds-primary-container hover:underline"
      >
        <Icon name="edit" filled={false} className="text-[14px]" />
        Edit
      </button>
    ) : null

  const headerAction = (
    <div className="flex items-center gap-3">
      <AnalysisIndicator status={analysisStatus} />
      {editAction}
    </div>
  )

  return (
    <DetailSection icon="auto_awesome" title="AI Metadata" action={headerAction} accent>
      {isEditing ? (
        <MetadataEditForm
          summary={summary}
          sensitivity={sensitivity}
          tags={tags}
          isSaving={isSaving}
          saveError={saveError}
          onCancel={() => setIsEditing(false)}
          onSave={handleSave}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <div>
            <div className="mb-2 flex items-center gap-2">
              <span className="font-ds-label text-ds-label-sm uppercase tracking-wider text-ds-secondary">
                Summary
              </span>
            </div>
            <p className="font-ds-body text-ds-body-sm leading-relaxed text-ds-on-surface-variant">
              {summary ? (
                <>&ldquo;{summary}&rdquo;</>
              ) : (
                <span className="italic text-ds-secondary">No AI summary available yet.</span>
              )}
            </p>
          </div>

          <div className="grid grid-cols-1 gap-y-3 border-t border-dashed border-ds-outline-variant pt-3">
            <Row
              label="Sensitivity"
              value={
                <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
                  {formatSensitivity(sensitivity)}
                </span>
              }
            />
            <Row
              label="Tags"
              last
              value={
                tags.length > 0 ? (
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
                ) : (
                  <span className="font-ds-body text-ds-body-sm text-ds-secondary">—</span>
                )
              }
            />
          </div>
        </div>
      )}
    </DetailSection>
  )
}
