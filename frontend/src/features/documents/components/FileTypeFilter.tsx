import Icon from '@/components/Icon'
import { FILE_TYPE_CONTENT_TYPE } from '../mappers'
import { OTHER_CONTENT_TYPE_EXCLUSIONS, SELECTABLE_FILE_TYPES } from '../filterOptions'

/** The slice of filter state this control owns. Both fields are mutually exclusive. */
export interface FileTypeFilterValue {
  /** Single content-type "contains" token for a known bucket (PDF/DOCX/TXT). */
  contentType?: string
  /** Tokens to exclude for the "Other" bucket; set means "Other" is active. */
  excludeContentTypes?: string[]
}

interface FileTypeFilterProps {
  value: FileTypeFilterValue
  /** Emits the next value; the parent merges it into its filter state. */
  onChange: (value: FileTypeFilterValue) => void
}

// A single selectable file-type chip. Pressed state mirrors the filter's design.
function FileTypeButton({
  icon,
  iconClassName,
  label,
  isActive,
  onClick,
}: {
  icon: string
  iconClassName: string
  label: string
  isActive: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={isActive}
      onClick={onClick}
      className={`flex cursor-pointer items-center gap-1 rounded-ds border px-2 py-1.5 transition-colors ${
        isActive
          ? 'border-ds-primary bg-ds-primary-fixed'
          : 'border-transparent bg-ds-surface-container hover:border-ds-outline-variant'
      }`}
    >
      <Icon name={icon} className={`text-[16px] ${iconClassName}`} />
      <span className="font-ds-label text-ds-label-sm text-ds-on-surface">{label}</span>
    </button>
  )
}

// File Type filter shared by the Document Library and Shared-with-me rails. The
// known buckets (PDF/DOCX/TXT) each map to one content-type "contains" token; the
// "Other" bucket is their complement, sent as the set of tokens to exclude. The
// two are mutually exclusive — selecting one clears the other.
export default function FileTypeFilter({ value, onChange }: FileTypeFilterProps) {
  const activeType = SELECTABLE_FILE_TYPES.find(
    (option) => FILE_TYPE_CONTENT_TYPE[option.type] === value.contentType,
  )?.type
  const isOtherActive = (value.excludeContentTypes?.length ?? 0) > 0

  return (
    <div className="grid grid-cols-2 gap-3">
      {SELECTABLE_FILE_TYPES.map((option) => {
        const isActive = activeType === option.type
        return (
          <FileTypeButton
            key={option.type}
            icon={option.icon}
            iconClassName={option.iconClassName}
            label={option.label}
            isActive={isActive}
            onClick={() =>
              onChange({
                contentType: isActive ? undefined : FILE_TYPE_CONTENT_TYPE[option.type],
                excludeContentTypes: undefined,
              })
            }
          />
        )
      })}
      <FileTypeButton
        icon="more_horiz"
        iconClassName="text-ds-secondary"
        label="Other"
        isActive={isOtherActive}
        onClick={() =>
          onChange({
            contentType: undefined,
            excludeContentTypes: isOtherActive ? undefined : OTHER_CONTENT_TYPE_EXCLUSIONS,
          })
        }
      />
    </div>
  )
}
