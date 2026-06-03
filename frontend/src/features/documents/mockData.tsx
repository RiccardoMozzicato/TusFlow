import type {
  CategoryFilter,
  DocumentDetail,
  FileTypeOption,
  LibraryDocument,
  TagFilter,
} from './types'

// Placeholder data mirroring the design mock. Replace with API-backed data
// (file/job services) once the library endpoints are wired.

export const CATEGORIES: CategoryFilter[] = [
  { id: 'financial', label: 'Financial Reports', defaultChecked: true },
  { id: 'hr', label: 'HR Policies', defaultChecked: true },
  { id: 'marketing', label: 'Marketing Assets' },
]

export const DEPARTMENTS = [
  'All Departments',
  'Engineering',
  'Sales',
  'Legal',
] as const

export const FILE_TYPE_OPTIONS: FileTypeOption[] = [
  { type: 'pdf', label: 'PDF', icon: 'picture_as_pdf', iconClassName: 'text-ds-error' },
  { type: 'docx', label: 'DOCX', icon: 'description', iconClassName: 'text-ds-primary' },
  { type: 'image', label: 'Image', icon: 'image', iconClassName: 'text-ds-tertiary-container' },
  { type: 'video', label: 'Video', icon: 'movie', iconClassName: 'text-ds-secondary' },
]

export const TAGS: TagFilter[] = [
  { id: 'q3-2023', label: 'Q3 2023' },
  { id: 'confidential', label: 'Confidential', defaultSelected: true },
  { id: 'draft', label: 'Draft' },
]

export const DOCUMENTS: LibraryDocument[] = [
  {
    id: 'doc-1',
    title: 'Q3 Financial Disclosures & Regulatory Filings',
    fileType: 'pdf',
    status: 'processing',
    owner: 'Alex Mercer',
    modified: 'Oct 24, 2023',
    version: 'v2.1',
  },
  {
    id: 'doc-2',
    title: 'Employee Onboarding Manual 2024',
    fileType: 'docx',
    status: 'ready',
    owner: 'HR Dept',
    modified: 'Oct 20, 2023',
    version: 'v1.0',
  },
  {
    id: 'doc-3',
    title: 'New Brand Architecture Wireframes',
    fileType: 'image',
    status: 'draft',
    owner: 'Design Team',
    modified: 'Oct 18, 2023',
    version: 'v0.9',
  },
  {
    id: 'doc-4',
    title: 'All-Hands Meeting Recording - September',
    fileType: 'video',
    status: 'scan_failed',
    owner: 'Admin',
    modified: 'Sep 30, 2023',
    version: 'v1.2',
  },
  {
    id: 'doc-5',
    title: 'Vendor Agreement - TechCorp Solutions',
    fileType: 'pdf',
    status: 'ready',
    owner: 'Legal',
    modified: 'Oct 15, 2023',
    version: 'v3.0',
  },
]

// Detail view model. Replace with an API-backed fetch (file/job services) once
// the document detail endpoint is wired. Keyed lookup keeps the page route
// (`/documents/:id`) honest even while data is mocked.
const DOCUMENT_DETAILS: Record<string, DocumentDetail> = {
  'doc-1': {
    id: 'doc-1',
    title: 'Q3_Financial_Report_Final.pdf',
    fileType: 'pdf',
    breadcrumbs: [
      { label: 'Documents', to: '/documents' },
      { label: 'Finance', to: '/documents' },
      { label: 'Q3_Financial_Report_Final.pdf' },
    ],
    aiSummary:
      'This report outlines the Q3 fiscal performance, highlighting a 15% growth in SaaS revenue and a reduction in operational overhead due to infrastructure optimization.',
    category: 'Finance',
    tags: ['Q3', '2023', 'Final'],
    owner: 'Alex Mercer',
    date: 'Oct 24, 2023',
    pageCount: 45,
    processing: [
      { label: 'Uploaded', timestamp: 'Oct 24, 09:12 AM', done: true },
      { label: 'Processing AI Summary', timestamp: 'Oct 24, 09:13 AM', done: true },
      { label: 'Completed', done: true },
    ],
    versions: [
      { id: 'v2-1', label: 'v2.1', author: 'Alex Mercer', date: 'Today, 09:12 AM', isCurrent: true },
      { id: 'v2-0', label: 'v2.0', author: 'Alex Mercer', date: 'Oct 20, 2023' },
      { id: 'v1-0', label: 'v1.0', author: 'System Migration', date: 'Sep 01, 2023' },
    ],
  },
}

/** Look up a document's detail view model by id. Returns undefined if unknown. */
export function getDocumentDetail(id: string): DocumentDetail | undefined {
  return DOCUMENT_DETAILS[id]
}
