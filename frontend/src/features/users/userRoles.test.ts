import { describe, expect, it } from 'vitest'
import { isUserRole, userRoleLabel } from './userRoles'

describe('isUserRole', () => {
  it('accepts the three known prefixed roles', () => {
    expect(isUserRole('ROLE_admin')).toBe(true)
    expect(isUserRole('ROLE_manager')).toBe(true)
    expect(isUserRole('ROLE_staff')).toBe(true)
  })

  it('rejects unknown or unprefixed values', () => {
    expect(isUserRole('admin')).toBe(false)
    expect(isUserRole('ROLE_root')).toBe(false)
  })
})

describe('userRoleLabel', () => {
  it('maps known roles to friendly labels', () => {
    expect(userRoleLabel('ROLE_admin')).toBe('Admin')
    expect(userRoleLabel('ROLE_staff')).toBe('Staff')
  })

  it('echoes unknown roles back unchanged', () => {
    expect(userRoleLabel('ROLE_root')).toBe('ROLE_root')
  })
})
