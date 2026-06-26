import { useState } from 'react'
import Icon from '@/components/Icon'
import MetadataEditor from '@/features/upload/components/MetadataEditor'
import { toMetadataRecord } from '@/features/upload/metadata'
import type { MetadataField } from '@/features/upload/types'
import type { MetadataEntry } from '../../types'
import DetailSection from './DetailSection'

interface UserMetadataPanelProps {
  /** User-inserted (non-AI) key/value pairs for the current version. */
  entries: MetadataEntry[]
  /** Whether the current user may edit the metadata (write access). */
  canEdit: boolean
  isSaving: boolean
  saveError: string | null
  /** Persists the merge patch and resulting entries; rejects on failure. */
  onSave: (payload: {
    metadata: Record<string, string>
    entries: MetadataEntry[]
  }) => Promise<void>
}

// Turns the editor's fields into a backend patch plus the resulting visible
// entries. Keys that existed before but are gone now are blanked ("") so the
// merge-only backend effectively removes them.
function buildSavePayload(draft: MetadataField[], original: MetadataEntry[]) {
  const record = toMetadataRecord(draft)
  const entries = Object.entries(record)
    .map(([key, value]) => ({ key, value }))
    .sort((a, b) => a.key.localeCompare(b.key))
  const metadata: Record<string, string> = { ...record }
  for (const { key } of original) {
    if (!(key in record)) metadata[key] = ''
  }
  return { metadata, entries }
}

// Panel listing the custom metadata a user attached to the file. Users with
// write access can edit values, rename keys, and add or remove pairs in place.
export default function UserMetadataPanel({
  entries,
  canEdit,
  isSaving,
  saveError,
  onSave,
}: UserMetadataPanelProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [draft, setDraft] = useState<MetadataField[]>([])

  function startEditing() {
    setDraft(entries.map((entry) => ({ key: entry.key, value: entry.value })))
    setIsEditing(true)
  }

  async function handleSave() {
    const { metadata, entries: nextEntries } = buildSavePayload(draft, entries)
    // Nothing to persist (no fields, nothing removed) — just close the editor.
    if (Object.keys(metadata).length === 0) {
      setIsEditing(false)
      return
    }
    try {
      await onSave({ metadata, entries: nextEntries })
      setIsEditing(false)
    } catch {
      // Error is rendered from the `saveError` prop; stay in edit mode.
    }
  }

  const editAction =
    canEdit && !isEditing ? (
      <button
        type="button"
        onClick={startEditing}
        className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-primary transition-colors hover:text-ds-primary-container hover:underline"
      >
        <Icon name="edit" filled={false} className="text-[14px]" />
        Edit
      </button>
    ) : null

  return (
    <DetailSection icon="label" title="User Metadata" action={editAction}>
      {isEditing ? (
        <div className="flex flex-col gap-4">
          <MetadataEditor fields={draft} onChange={setDraft} disabled={isSaving} />

          {saveError ? (
            <p className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
              <Icon name="error" className="text-[14px]" />
              {saveError}
            </p>
          ) : null}

          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => setIsEditing(false)}
              disabled={isSaving}
              className="rounded-ds px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-on-surface disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={isSaving}
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
        </div>
      ) : entries.length > 0 ? (
        <dl className="flex flex-col gap-3">
          {entries.map(({ key, value }, index) => (
            <div
              key={key}
              className={`flex flex-col gap-1 ${
                index === entries.length - 1
                  ? ''
                  : 'border-b border-dashed border-ds-surface-container pb-3'
              }`}
            >
              <dt className="font-ds-label text-ds-label-sm uppercase tracking-wider text-ds-secondary">
                {key}
              </dt>
              <dd className="break-words font-ds-body text-ds-body-sm text-ds-on-surface">
                {value}
              </dd>
            </div>
          ))}
        </dl>
      ) : (
        <div className="flex flex-col items-center gap-1 py-4 text-center">
          <Icon name="label_off" filled={false} className="text-[24px] text-ds-outline" />
          <p className="font-ds-body text-ds-body-sm text-ds-secondary">
            No user-added metadata.
          </p>
        </div>
      )}
    </DetailSection>
  )
}
