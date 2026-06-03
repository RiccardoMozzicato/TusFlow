import type { StatCard, DocumentRow, ActivityItem } from './types'

// Placeholder data mirroring the design mock. Replace with API-backed data
// (file/job/user services) once the dashboard endpoints are wired.

export const STATS: StatCard[] = [
  {
    label: 'Total Documents',
    value: '12,482',
    icon: 'description',
    iconClassName: 'bg-ds-surface-variant text-ds-on-surface',
  },
  {
    label: 'Pending Processing',
    value: '34',
    note: 'files',
    icon: 'hourglass_empty',
    iconClassName: 'bg-ds-primary-fixed text-ds-primary',
    progress: 0.66,
  },
  {
    label: 'Completed Today',
    value: '128',
    icon: 'check_circle',
    iconClassName: 'bg-ds-tertiary-fixed text-ds-tertiary',
  },
  {
    label: 'Failed Uploads',
    value: '2',
    note: 'Action required',
    noteTone: 'error',
    icon: 'error',
    iconClassName: 'bg-ds-error-container text-ds-error',
  },
]

export const RECENT_DOCUMENTS: DocumentRow[] = [
  {
    name: 'Q3_Financial_Report_Final.pdf',
    type: 'PDF',
    status: 'processed',
    modified: 'Today, 10:42 AM',
    icon: 'picture_as_pdf',
    iconClassName: 'bg-ds-error-container/20 text-ds-error',
  },
  {
    name: 'Vendor_Agreement_AcmeCorp.docx',
    type: 'DOCX',
    status: 'in_review',
    modified: 'Yesterday, 4:15 PM',
    icon: 'description',
    iconClassName: 'bg-ds-primary-fixed/50 text-ds-primary',
  },
  {
    name: 'Employee_Onboarding_Assets.zip',
    type: 'ZIP',
    status: 'failed',
    modified: 'Oct 24, 2023',
    icon: 'folder_zip',
    iconClassName: 'bg-ds-surface-variant text-ds-secondary',
  },
  {
    name: 'Marketing_Brand_Guidelines_v2.pdf',
    type: 'PDF',
    status: 'processed',
    modified: 'Oct 22, 2023',
    icon: 'picture_as_pdf',
    iconClassName: 'bg-ds-error-container/20 text-ds-error',
  },
]

export const ACTIVITY: ActivityItem[] = [
  {
    icon: 'upload_file',
    time: '10 minutes ago',
    message: (
      <>
        <span className="font-semibold">Sarah Jenkins</span> uploaded 5 new files
        to{' '}
        <span className="cursor-pointer font-medium text-ds-primary hover:underline">
          Legal Documents
        </span>
      </>
    ),
  },
  {
    icon: 'group_add',
    time: '2 hours ago',
    message: (
      <>
        <span className="font-semibold">Marcus Cole</span> shared{' '}
        <span className="cursor-pointer font-medium text-ds-primary hover:underline">
          Q3_Financial_Report_Final.pdf
        </span>{' '}
        with the Finance Team
      </>
    ),
  },
  {
    icon: 'error',
    tone: 'error',
    time: 'Yesterday, 11:30 AM',
    message: (
      <>
        System alert: Batch upload <span className="font-semibold">#9928</span>{' '}
        encountered errors.
      </>
    ),
  },
  {
    icon: 'verified_user',
    time: 'Oct 23, 2023',
    message: (
      <>
        <span className="font-semibold">Admin</span> updated security policies for
        external sharing.
      </>
    ),
  },
]
