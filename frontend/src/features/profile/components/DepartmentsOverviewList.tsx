import type { DepartmentCount } from '@/features/users/orgStats'

// Department breakdown with a proportion bar per department, shown when the
// admin opens the Departments tile. Bars are scaled to the largest department.
export default function DepartmentsOverviewList({
  departments,
}: {
  departments: DepartmentCount[]
}) {
  if (departments.length === 0) {
    return <p className="py-6 text-center font-ds-body text-ds-body-sm text-ds-secondary">No departments.</p>
  }

  const max = Math.max(...departments.map((d) => d.count), 1)

  return (
    <ul className="flex flex-col gap-3">
      {departments.map((dept) => (
        <li key={dept.name} className="flex items-center gap-3">
          <span className="w-32 shrink-0 truncate font-ds-body text-ds-body-sm text-ds-on-surface">
            {dept.name}
          </span>
          <div className="h-2 flex-1 overflow-hidden rounded-ds-full bg-ds-surface-container-high">
            <div
              className="h-full rounded-ds-full bg-ds-primary"
              style={{ width: `${Math.round((dept.count / max) * 100)}%` }}
            />
          </div>
          <span className="w-8 shrink-0 text-right font-ds-label text-ds-label-sm tabular-nums text-ds-secondary">
            {dept.count}
          </span>
        </li>
      ))}
    </ul>
  )
}
