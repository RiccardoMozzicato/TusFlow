import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { buildOverviewStats } from './adminOverview'
import DepartmentsOverviewList from './components/DepartmentsOverviewList'
import type { OrgStats } from '@/features/users/orgStats'

const STATS: OrgStats = {
  totalUsers: 20,
  roleCounts: { ROLE_admin: 2, ROLE_manager: 3, ROLE_staff: 15 },
  departmentCount: 2,
  departments: [
    { name: 'Engineering', count: 12 },
    { name: 'Finance', count: 6 },
    { name: 'Unassigned', count: 2 },
  ],
  storageUsed: 0,
  storageQuota: 0,
  unlimitedCount: 20,
  nearLimitCount: 0,
}

describe('buildOverviewStats', () => {
  it('wires the click handlers to the matching tiles', async () => {
    const onTotalUsers = vi.fn()
    const onDepartments = vi.fn()
    const [usersTile, deptTile] = buildOverviewStats(STATS, { onTotalUsers, onDepartments })

    expect(usersTile.label).toBe('Total Users')
    expect(deptTile.label).toBe('Departments')

    usersTile.onClick?.()
    deptTile.onClick?.()
    expect(onTotalUsers).toHaveBeenCalledOnce()
    expect(onDepartments).toHaveBeenCalledOnce()
  })

  it('leaves tiles non-interactive when no handlers are given', () => {
    const [usersTile] = buildOverviewStats(STATS)
    expect(usersTile.onClick).toBeUndefined()
  })
})

describe('DepartmentsOverviewList', () => {
  it('renders every department with its count', () => {
    render(<DepartmentsOverviewList departments={STATS.departments} />)
    expect(screen.getByText('Engineering')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
  })

  it('shows a placeholder when there are no departments', () => {
    render(<DepartmentsOverviewList departments={[]} />)
    expect(screen.getByText('No departments.')).toBeInTheDocument()
  })
})
