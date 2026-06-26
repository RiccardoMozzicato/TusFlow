import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import DropZone from './DropZone'

describe('DropZone', () => {
  it('forwards browsed files to onFiles', async () => {
    const onFiles = vi.fn()
    const { container } = render(<DropZone onFiles={onFiles} />)
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })

    await userEvent.upload(input, file)

    expect(onFiles).toHaveBeenCalledTimes(1)
    expect(onFiles.mock.calls[0][0][0]).toBe(file)
  })
})
