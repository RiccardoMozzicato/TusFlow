import { describe, expect, it } from 'vitest'
import { allowedUploadScopes } from './uploadScopes'
import { SharingScope } from '@/features/documents/documentsApi'

function values(roles: string[] | undefined) {
  return allowedUploadScopes(roles).map((o) => o.value)
}

describe('allowedUploadScopes', () => {
  it('hides Organization for staff and managers', () => {
    expect(values(['staff'])).toEqual([SharingScope.PERSONAL, SharingScope.DEPARTMENT])
    expect(values(['manager'])).toEqual([SharingScope.PERSONAL, SharingScope.DEPARTMENT])
  })

  it('allows Organization only for admins', () => {
    expect(values(['admin'])).toContain(SharingScope.ORGANIZATION)
  })

  it('defaults to the staff-safe set when roles are missing', () => {
    expect(values(undefined)).toEqual([SharingScope.PERSONAL, SharingScope.DEPARTMENT])
  })
})
