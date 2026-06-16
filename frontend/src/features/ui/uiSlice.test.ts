import { describe, expect, test } from 'vitest'
import uiReducer, {
  setSidebarCollapsed,
  toggleSidebar,
} from './uiSlice'

describe('uiSlice', () => {
  test('toggleSidebar flips the collapsed flag', () => {
    // Arrange
    const state = { sidebarCollapsed: false }

    // Act
    const next = uiReducer(state, toggleSidebar())

    // Assert
    expect(next.sidebarCollapsed).toBe(true)
    expect(uiReducer(next, toggleSidebar()).sidebarCollapsed).toBe(false)
  })

  test('setSidebarCollapsed sets the collapsed flag explicitly', () => {
    // Arrange
    const state = { sidebarCollapsed: false }

    // Act + Assert
    expect(uiReducer(state, setSidebarCollapsed(true)).sidebarCollapsed).toBe(
      true,
    )
    expect(uiReducer(state, setSidebarCollapsed(false)).sidebarCollapsed).toBe(
      false,
    )
  })
})
