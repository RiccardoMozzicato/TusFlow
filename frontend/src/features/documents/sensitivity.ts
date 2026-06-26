import { SensitivityLevel } from './detailsApi'

export interface SensitivityOption {
  value: SensitivityLevel
  label: string
}

// The four classification levels accepted by the backend, in increasing order
// of sensitivity, paired with a human-readable label for display/selection.
export const SENSITIVITY_OPTIONS: SensitivityOption[] = [
  { value: SensitivityLevel.PUBLIC, label: 'Public' },
  { value: SensitivityLevel.INTERNAL, label: 'Internal' },
  { value: SensitivityLevel.CONFIDENTIAL, label: 'Confidential' },
  { value: SensitivityLevel.HIGHLY_CONFIDENTIAL, label: 'Highly Confidential' },
]

// Normalizes a stored sensitivity string to a known level, or '' when it matches
// none. Accepts both the enum form ("HIGHLY_CONFIDENTIAL") and spaced/mixed case
// ("Highly Confidential") so legacy values still map onto the select.
export function toSensitivityLevel(value: string): SensitivityLevel | '' {
  const normalized = value.trim().toUpperCase().replace(/\s+/g, '_')
  const match = SENSITIVITY_OPTIONS.find((option) => option.value === normalized)
  return match ? match.value : ''
}

// Humanizes a stored sensitivity for display; falls back to the raw value (e.g.
// the "—" placeholder) when it matches no known level.
export function formatSensitivity(value: string): string {
  const normalized = value.trim().toUpperCase().replace(/\s+/g, '_')
  const match = SENSITIVITY_OPTIONS.find((option) => option.value === normalized)
  return match ? match.label : value
}
