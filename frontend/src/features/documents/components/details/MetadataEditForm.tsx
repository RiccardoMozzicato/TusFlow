import { useState } from 'react'
import Icon from '@/components/Icon'
import type { SensitivityLevel } from '../../detailsApi'
import { parseTags } from '../../detailsMappers'
import { SENSITIVITY_OPTIONS, toSensitivityLevel } from '../../sensitivity'

// Backend caps the comma-joined tag string at 100 chars and the summary at 500;
// mirror both client-side.
const TAGS_MAX_LENGTH = 100
const SUMMARY_MAX_LENGTH = 500

interface MetadataEditFormProps {
  /** Current AI summary (raw), used to seed the textarea. */
  summary: string | null
  /** Current stored sensitivity (raw), used to seed the select. */
  sensitivity: string
  /** Current tags, used to seed the comma-separated input. */
  tags: string[]
  isSaving: boolean
  saveError: string | null
  onCancel: () => void
  /** Fired with the edited values; the parent persists and exits edit mode. */
  onSave: (changes: {
    summary: string
    sensitivity: SensitivityLevel
    tags: string[]
  }) => void
}

// Inline editor for the AI-generated, user-editable metadata (summary,
// sensitivity, tags). A sensitivity level is required to save, matching the
// backend's validation. Tags are entered comma-separated and parsed on submit.
export default function MetadataEditForm({
  summary,
  sensitivity,
  tags,
  isSaving,
  saveError,
  onCancel,
  onSave,
}: MetadataEditFormProps) {
  const [draftSummary, setDraftSummary] = useState(summary ?? '')
  const [draftSensitivity, setDraftSensitivity] = useState<SensitivityLevel | ''>(
    toSensitivityLevel(sensitivity),
  )
  const [draftTags, setDraftTags] = useState(tags.join(', '))

  const canSave = draftSensitivity !== '' && !isSaving

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (draftSensitivity === '') return
    onSave({
      summary: draftSummary.trim(),
      sensitivity: draftSensitivity,
      tags: parseTags(draftTags),
    })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <p className="flex items-start gap-1.5 rounded-ds bg-ds-surface-variant/60 px-2.5 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
        <Icon name="info" filled={false} className="mt-0.5 text-[14px]" />
        These fields are AI-generated. Review and correct them before relying on
        them for important decisions.
      </p>

      <label className="flex flex-col gap-1">
        <span className="flex items-center justify-between font-ds-label text-ds-label-sm text-ds-secondary">
          Summary
          <span className="font-ds-body text-ds-body-sm text-ds-outline">
            {draftSummary.length}/{SUMMARY_MAX_LENGTH}
          </span>
        </span>
        <textarea
          value={draftSummary}
          rows={4}
          maxLength={SUMMARY_MAX_LENGTH}
          disabled={isSaving}
          placeholder="A concise summary of the document…"
          onChange={(event) => setDraftSummary(event.target.value)}
          className="resize-y rounded-ds border border-ds-outline-variant bg-ds-surface-container-lowest px-2 py-1.5 font-ds-body text-ds-body-sm leading-relaxed text-ds-on-surface focus:border-ds-primary focus:outline-none focus:ring-1 focus:ring-ds-primary disabled:opacity-50"
        />
      </label>

      <label className="flex flex-col gap-1">
        <span className="font-ds-label text-ds-label-sm text-ds-secondary">
          Sensitivity
        </span>
        <select
          value={draftSensitivity}
          disabled={isSaving}
          onChange={(event) =>
            setDraftSensitivity(event.target.value as SensitivityLevel | '')
          }
          className="rounded-ds border border-ds-outline-variant bg-ds-surface-container-lowest px-2 py-1.5 font-ds-body text-ds-body-sm text-ds-on-surface focus:border-ds-primary focus:outline-none focus:ring-1 focus:ring-ds-primary disabled:opacity-50"
        >
          <option value="" disabled>
            Select a level…
          </option>
          {SENSITIVITY_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        <span className="flex items-center justify-between font-ds-label text-ds-label-sm text-ds-secondary">
          Tags
          <span className="font-ds-body text-ds-body-sm text-ds-outline">
            comma-separated
          </span>
        </span>
        <input
          type="text"
          value={draftTags}
          maxLength={TAGS_MAX_LENGTH}
          disabled={isSaving}
          placeholder="e.g. Q3, 2023, Final"
          onChange={(event) => setDraftTags(event.target.value)}
          className="rounded-ds border border-ds-outline-variant bg-ds-surface-container-lowest px-2 py-1.5 font-ds-body text-ds-body-sm text-ds-on-surface focus:border-ds-primary focus:outline-none focus:ring-1 focus:ring-ds-primary disabled:opacity-50"
        />
      </label>

      {saveError ? (
        <p className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
          <Icon name="error" className="text-[14px]" />
          {saveError}
        </p>
      ) : null}

      <div className="flex items-center justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          className="rounded-ds px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-on-surface disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={!canSave}
          title={draftSensitivity === '' ? 'Choose a sensitivity level to save' : undefined}
          className="flex items-center gap-1.5 rounded-ds-lg bg-ds-primary px-4 py-1.5 font-ds-label text-ds-label-md text-ds-on-primary transition-colors hover:bg-ds-primary-container disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Icon
            name={isSaving ? 'progress_activity' : 'save'}
            filled={false}
            className={`text-[16px] ${isSaving ? 'animate-spin' : ''}`}
          />
          {isSaving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </form>
  )
}
