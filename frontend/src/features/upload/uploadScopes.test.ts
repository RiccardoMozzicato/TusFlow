import { describe, expect, it } from 'vitest'
import { allowedUploadScopes } from './uploadScopes'
import { SharingScope } from '@/features/documents/documentsApi'

function values(roles: string[] | undefined) {
  return allowedUploadScopes(roles).map((o) => o.value)
}

describe('allowedUploadScopes', () => {
  it('limits staff to Personal only', () => {
    expect(values(['staff'])).toEqual([SharingScope.PERSONAL])
  })

  it('allows managers Personal and Department but not Organization', () => {
    expect(values(['manager'])).toEqual([SharingScope.PERSONAL, SharingScope.DEPARTMENT])
  })

  it('allows admins every scope', () => {
    expect(values(['admin'])).toEqual([
      SharingScope.PERSONAL,
      SharingScope.DEPARTMENT,
      SharingScope.ORGANIZATION,
    ])
  })

  it('defaults to the staff-safe set when roles are missing', () => {
    expect(values(undefined)).toEqual([SharingScope.PERSONAL])
  })
})
