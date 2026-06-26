import { describe, expect, test } from 'vitest'
import { sanitizeMetadataKey, toMetadataRecord } from './metadata'

describe('sanitizeMetadataKey', () => {
  test('strips spaces and commas (illegal in tus metadata keys)', () => {
    expect(sanitizeMetadataKey('my key, name')).toBe('mykeyname')
  })

  test('leaves a clean key unchanged', () => {
    expect(sanitizeMetadataKey('department')).toBe('department')
  })
})

describe('toMetadataRecord', () => {
  test('builds a record from complete fields, trimming whitespace', () => {
    const record = toMetadataRecord([
      { key: 'department', value: '  legal  ' },
      { key: 'project', value: 'atlas' },
    ])

    expect(record).toEqual({ department: 'legal', project: 'atlas' })
  })

  test('drops rows with an empty key or value', () => {
    const record = toMetadataRecord([
      { key: 'department', value: 'legal' },
      { key: '', value: 'orphan' },
      { key: 'note', value: '' },
    ])

    expect(record).toEqual({ department: 'legal' })
  })

  test('last value wins on a duplicate key', () => {
    const record = toMetadataRecord([
      { key: 'tag', value: 'first' },
      { key: 'tag', value: 'second' },
    ])

    expect(record).toEqual({ tag: 'second' })
  })

  test('returns an empty record for no usable fields', () => {
    expect(toMetadataRecord([])).toEqual({})
  })
})
