import Icon from '@/components/Icon'
import StatsGrid from '@/features/dashboard/components/StatsGrid'
import RecentDocumentsTable from '@/features/dashboard/components/RecentDocumentsTable'
import DepartmentActivity from '@/features/dashboard/components/DepartmentActivity'

export default function DashboardPage() {
  return (
    <>
      <div className="mb-8 flex items-end justify-between">
        <div>
          <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
            Overview
          </h2>
          <p className="mt-1 text-ds-body-sm text-ds-secondary">
            Monitor document processing and department activity.
          </p>
        </div>
        <button
          type="button"
          className="flex items-center gap-2 rounded-ds-lg bg-ds-tertiary-container px-6 py-2.5 font-ds-label text-ds-label-md text-ds-on-tertiary shadow-sm transition-colors hover:bg-ds-tertiary"
        >
          <Icon name="cloud_upload" className="text-sm" />
          Upload files
        </button>
      </div>

      <StatsGrid />

      <div className="grid grid-cols-12 gap-6">
        <RecentDocumentsTable />
        <DepartmentActivity />
      </div>
    </>
  )
}
