import Icon from '@/components/Icon'
import { sanitizeMetadataKey } from '../metadata'
import type { MetadataField } from '../types'

interface MetadataEditorProps {
  fields: MetadataField[]
  onChange: (fields: MetadataField[]) => void
  /** Disable editing once the upload has started. */
  disabled?: boolean
}

// Immutable helpers — never mutate the incoming array.
function replaceAt(
  fields: MetadataField[],
  index: number,
  patch: Partial<MetadataField>,
): MetadataField[] {
  return fields.map((field, i) => (i === index ? { ...field, ...patch } : field))
}

// Free-form key/value editor for a staged file's metadata.
export default function MetadataEditor({
  fields,
  onChange,
  disabled = false,
}: MetadataEditorProps) {
  function addField() {
    onChange([...fields, { key: '', value: '' }])
  }

  function removeField(index: number) {
    onChange(fields.filter((_, i) => i !== index))
  }

  return (
    <div className="flex flex-col gap-2">
      {fields.map((field, index) => (
        <div key={index} className="flex items-center gap-2">
          <input
            type="text"
            value={field.key}
            disabled={disabled}
            placeholder="key"
            aria-label="Metadata key"
            onChange={(event) =>
              onChange(
                replaceAt(fields, index, {
                  key: sanitizeMetadataKey(event.target.value),
                }),
              )
            }
            className="w-40 rounded-ds border border-ds-outline-variant bg-ds-surface-container-lowest px-2 py-1 text-ds-body-sm focus:border-ds-primary focus:outline-none focus:ring-1 focus:ring-ds-primary disabled:opacity-50"
          />
          <span className="text-ds-on-surface-variant">:</span>
          <input
            type="text"
            value={field.value}
            disabled={disabled}
            placeholder="value"
            aria-label="Metadata value"
            onChange={(event) =>
              onChange(replaceAt(fields, index, { value: event.target.value }))
            }
            className="flex-1 rounded-ds border border-ds-outline-variant bg-ds-surface-container-lowest px-2 py-1 text-ds-body-sm focus:border-ds-primary focus:outline-none focus:ring-1 focus:ring-ds-primary disabled:opacity-50"
          />
          <button
            type="button"
            disabled={disabled}
            onClick={() => removeField(index)}
            title="Remove field"
            aria-label="Remove field"
            className="rounded-ds p-1 text-ds-secondary transition-colors hover:bg-ds-error-container hover:text-ds-error disabled:opacity-50"
          >
            <Icon name="close" filled={false} className="text-[18px]" />
          </button>
        </div>
      ))}

      {!disabled && (
        <button
          type="button"
          onClick={addField}
          className="flex w-fit items-center gap-1 rounded-ds px-2 py-1 font-ds-label text-ds-label-sm text-ds-primary transition-colors hover:bg-ds-primary-container"
        >
          <Icon name="add" filled={false} className="text-[16px]" />
          Add field
        </button>
      )}
    </div>
  )
}
