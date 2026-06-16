// Pragmatic email shape check: one @, no spaces, and a dotted domain. Full
// RFC-5322 validation is overkill for an admin form — the backend/Keycloak is
// the source of truth — this just catches obvious typos before submitting.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export interface NewUserFieldErrors {
  username?: string
  email?: string
  organization?: string
  quota?: string
}

export interface NewUserInput {
  username: string
  email: string
  organization: string
  /** Whether the raw quota input parsed to a number. */
  quotaOk: boolean
  /** Parsed quota in bytes; null means "unlimited" (not allowed on create). */
  quotaBytes: number | null
}

/**
 * Validates the create-user form. Returns a map of field → message; an empty
 * object means the form is valid. Rules: username must be present and contain
 * no whitespace, email must look like an address, organization is required, and
 * a finite storage quota must be set (unlimited is not allowed at creation).
 */
export function validateNewUser(input: NewUserInput): NewUserFieldErrors {
  const errors: NewUserFieldErrors = {}

  if (input.username.trim() === '') {
    errors.username = 'Username is required.'
  } else if (/\s/.test(input.username)) {
    errors.username = 'Username cannot contain spaces.'
  }

  const email = input.email.trim()
  if (email === '') {
    errors.email = 'Email is required.'
  } else if (!EMAIL_RE.test(email)) {
    errors.email = 'Enter a valid email address.'
  }

  if (input.organization.trim() === '') {
    errors.organization = 'Organization is required.'
  }

  if (!input.quotaOk) {
    errors.quota = 'Storage quota must be a non-negative number.'
  } else if (input.quotaBytes === null) {
    errors.quota = 'Storage quota is required.'
  }

  return errors
}

/** True when a validation result carries at least one field error. */
export function hasErrors(errors: NewUserFieldErrors): boolean {
  return Object.keys(errors).length > 0
}
