import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import StatsGrid from '@/features/dashboard/components/StatsGrid'
import RecentDocumentsTable from '@/features/dashboard/components/RecentDocumentsTable'
import { useDashboardData } from '@/features/dashboard/useDashboardData'

export default function DashboardPage() {
  const { stats, recentDocuments, hasError } = useDashboardData()

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
        <Link
          to="/upload"
          className="flex items-center gap-2 rounded-ds-lg bg-ds-tertiary-container px-6 py-2.5 font-ds-label text-ds-label-md text-ds-on-tertiary shadow-sm transition-colors hover:bg-ds-tertiary"
        >
          <Icon name="cloud_upload" className="text-sm" />
          Upload files
        </Link>
      </div>

      {hasError ? (
        <div className="mb-8 flex items-center gap-2 rounded-ds-lg border border-ds-error/20 bg-ds-error-container/40 px-4 py-3 text-ds-body-sm text-ds-on-error-container">
          <Icon name="error" className="text-[18px] text-ds-error" />
          Could not load dashboard data. Refresh the page to try again.
        </div>
      ) : null}

      <StatsGrid stats={stats} />

      <RecentDocumentsTable documents={recentDocuments} />
    </>
  )
}
