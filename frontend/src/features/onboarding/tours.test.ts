import { describe, expect, test } from 'vitest'
import { buildSteps, contextualTourForPath } from './tours'

describe('buildSteps', () => {
  test('includes the admin step only for admins', () => {
    const admin = buildSteps('main', ['admin'])
    const staff = buildSteps('main', ['staff'])

    const hasAdminStep = (steps: ReturnType<typeof buildSteps>) =>
      steps.some((step) => step.role === 'admin')

    expect(hasAdminStep(admin)).toBe(true)
    expect(hasAdminStep(staff)).toBe(false)
    // Dropping the admin step keeps the rest intact.
    expect(staff.length).toBe(admin.length - 1)
  })

  test('non-main tours have no role-gated steps', () => {
    for (const id of ['library', 'upload', 'details'] as const) {
      expect(buildSteps(id, undefined).every((s) => s.role === undefined)).toBe(true)
    }
  })

  test('every step has a target and content', () => {
    const steps = buildSteps('main', ['admin'])
    expect(steps.length).toBeGreaterThan(0)
    for (const step of steps) {
      expect(step.target).toBeTruthy()
      expect(step.content).toBeTruthy()
    }
  })
})

describe('contextualTourForPath', () => {
  test('maps routes to their contextual tour', () => {
    expect(contextualTourForPath('/documents')).toBe('library')
    expect(contextualTourForPath('/documents/abc-123')).toBe('details')
    expect(contextualTourForPath('/upload')).toBe('upload')
  })

  test('returns null for routes without a contextual tour', () => {
    expect(contextualTourForPath('/')).toBeNull()
    expect(contextualTourForPath('/favorites')).toBeNull()
    expect(contextualTourForPath('/admin')).toBeNull()
  })
})
