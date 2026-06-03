import type { UploadItem, UploadSummary } from './types'

// Placeholder transfer queue mirroring the design mock. Replace with live
// upload state (tus client + job service) once uploads are wired.

export const UPLOAD_QUEUE: UploadItem[] = [
  {
    id: 'q3-financial-draft',
    name: 'Q3_Financial_Draft.pdf',
    icon: 'picture_as_pdf',
    iconClassName: 'text-ds-error',
    status: 'uploading',
    progress: 0.67,
    detailPrimary: '4.2 MB/s • 12s left',
    detailSecondary: '18MB / 27MB',
  },
  {
    id: 'employee-handbook',
    name: 'Employee_Handbook_v2.docx',
    icon: 'description',
    iconClassName: 'text-ds-on-primary-fixed-variant',
    status: 'paused',
    progress: 0.45,
    detailPrimary: 'Paused',
    detailSecondary: '4.5MB / 10MB',
  },
  {
    id: 'marketing-video',
    name: 'Marketing_Video_Final.mp4',
    icon: 'movie',
    iconClassName: 'text-ds-tertiary',
    status: 'processing',
    progress: 1,
    detailPrimary: 'Generating thumbnails...',
    detailSecondary: '100% (145MB)',
  },
  {
    id: 'architecture-diagram',
    name: 'Architecture_Diagram.svg',
    icon: 'image',
    iconClassName: 'text-ds-outline',
    status: 'completed',
    progress: 1,
    detailPrimary: 'Done',
    detailSecondary: '1.2MB',
  },
  {
    id: 'server-log-error',
    name: 'Server_Log_Error.txt',
    icon: 'text_snippet',
    iconClassName: 'text-ds-outline',
    status: 'failed',
    progress: 0.15,
    detailPrimary: 'Network timeout',
    detailSecondary: 'Interrupted',
  },
]

export const UPLOAD_SUMMARY: UploadSummary = {
  completed: 3,
  total: 7,
  progress: 0.6,
  speed: '8.5 MB/s',
}
