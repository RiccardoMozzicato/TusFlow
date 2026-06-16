import { describe, expect, it } from 'vitest'
import { hasErrors, validateNewUser, type NewUserInput } from './userValidation'

function validInput(overrides: Partial<NewUserInput> = {}): NewUserInput {
  return {
    username: 'grace',
    email: 'grace@example.com',
    organization: 'Acme',
    quotaOk: true,
    quotaBytes: 1024,
    ...overrides,
  }
}

describe('validateNewUser', () => {
  it('accepts a well-formed input', () => {
    expect(validateNewUser(validInput())).toEqual({})
    expect(hasErrors(validateNewUser(validInput()))).toBe(false)
  })

  it('requires a username and rejects whitespace in it', () => {
    expect(validateNewUser(validInput({ username: '  ' })).username).toMatch(/required/i)
    expect(validateNewUser(validInput({ username: 'gr ace' })).username).toMatch(/spaces/i)
    expect(validateNewUser(validInput({ username: 'grace ' })).username).toMatch(/spaces/i)
  })

  it('requires a plausible email address', () => {
    expect(validateNewUser(validInput({ email: '' })).email).toMatch(/required/i)
    expect(validateNewUser(validInput({ email: 'not-an-email' })).email).toMatch(/valid/i)
    expect(validateNewUser(validInput({ email: 'a@b' })).email).toMatch(/valid/i)
    expect(validateNewUser(validInput({ email: 'a@b.co' })).email).toBeUndefined()
  })

  it('requires an organization', () => {
    expect(validateNewUser(validInput({ organization: '  ' })).organization).toMatch(/required/i)
  })

  it('requires a defined, non-negative storage quota', () => {
    expect(validateNewUser(validInput({ quotaOk: false })).quota).toMatch(/non-negative/i)
    expect(validateNewUser(validInput({ quotaBytes: null })).quota).toMatch(/required/i)
  })

  it('reports every failing field at once', () => {
    const errors = validateNewUser({
      username: 'bad name',
      email: 'nope',
      organization: '',
      quotaOk: true,
      quotaBytes: null,
    })
    expect(Object.keys(errors).sort()).toEqual(['email', 'organization', 'quota', 'username'])
  })
})
