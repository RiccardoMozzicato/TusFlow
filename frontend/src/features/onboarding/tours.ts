import { isAdmin } from '@/features/profile/roles'
import type { TourId } from './onboardingSlice'

// A library-agnostic step description. The OnboardingTour driver maps these onto
// react-joyride steps, so swapping the tour library only touches that one file.
export interface TourStep {
  /** CSS selector for the anchor, or 'center' for an unanchored modal step. */
  target: string
  title?: string
  /** Tooltip copy. Kept in English to match the (English) on-screen labels. */
  content: string
  /** Route this step lives on; the driver navigates here before showing it. */
  path?: string
  placement?: 'top' | 'bottom' | 'left' | 'right' | 'center' | 'auto'
  /** Only include the step for users holding this role. */
  role?: 'admin'
  /** If the anchor is missing, skip past it instead of blocking the tour. */
  optional?: boolean
}

const sel = (name: string) => `[data-tour="${name}"]`

// The cross-page welcome tour: one high-level stop per area. Auto-starts on first
// access; the driver navigates between `path`s as the user clicks Next.
const MAIN_TOUR: TourStep[] = [
  {
    target: 'center',
    title: 'Welcome to DocPortal',
    content: 'Take a quick tour to learn how to find, upload, and work with your documents.',
    path: '/',
    placement: 'center',
  },
  {
    target: sel('nav'),
    title: 'Navigation',
    content:
      'Use the sidebar to move between Dashboard, Documents, Upload, Shared with me, and Favorites.',
    path: '/',
    placement: 'right',
  },
  {
    target: sel('dashboard-stats'),
    title: 'Your overview',
    content:
      'See your totals at a glance: documents, items processing, completed today, and any failures.',
    path: '/',
    placement: 'bottom',
  },
  {
    target: sel('upload-scope'),
    title: 'Choose a sharing scope',
    content:
      'First pick who will see the files you add next — Personal, Department, or Organization (admin-only).',
    path: '/upload',
    placement: 'bottom',
  },
  {
    target: sel('upload-dropzone'),
    title: 'Upload files',
    content:
      'Then drag & drop or browse. Files are staged so you can add metadata before the upload starts.',
    path: '/upload',
    placement: 'top',
  },
  {
    target: sel('library-filters'),
    title: 'Filter your library',
    content:
      'Narrow documents by file type, creation date and sharing scope — and show only files you own or those shared with you.',
    path: '/documents',
    placement: 'right',
  },
  {
    target: sel('library-toolbar'),
    title: 'Search & quick actions',
    content:
      'Search within the library and switch between list and grid. Each row has quick actions: star, download, and a ⋮ menu to share, version, or delete.',
    path: '/documents',
    placement: 'bottom',
  },
  {
    target: sel('chat-launcher'),
    title: 'Ask your documents',
    content:
      'Ask a question in natural language — the assistant answers using the documents you can access, with clickable sources.',
    path: '/documents',
    placement: 'left',
  },
  {
    target: sel('sidebar-profile'),
    title: 'Your profile',
    content: 'Your profile and role live here. Sign out from the icon next to it.',
    path: '/documents',
    placement: 'right',
  },
  {
    target: sel('nav-admin'),
    title: 'Admin console',
    content: 'As an admin, manage users, quotas, and organization settings from here.',
    path: '/documents',
    placement: 'right',
    role: 'admin',
    optional: true,
  },
  {
    target: 'center',
    title: "You're all set",
    content: 'Replay this tour or get tips for a page anytime from the Help (?) button in the sidebar.',
    path: '/documents',
    placement: 'center',
  },
]

// Contextual tours: assume the user is already on the page (no navigation), and
// are replayable on demand from the Help menu.
const LIBRARY_TOUR: TourStep[] = [
  {
    target: sel('library-filters'),
    title: 'Filters',
    content: 'Narrow documents by file type, creation date, and sharing scope.',
    placement: 'right',
  },
  {
    target: sel('library-ownership'),
    title: 'Ownership',
    content: 'Show only files you own, or only those shared with you.',
    placement: 'right',
  },
  {
    target: sel('library-view-toggle'),
    title: 'List or grid',
    content: 'Switch between the dense list and the card grid — your choice sticks while you browse.',
    placement: 'bottom',
  },
  {
    target: sel('library-row-actions'),
    title: 'Row actions',
    content:
      'Star a version, download a file, or open the ⋮ menu to share, upload a new version, or delete — actions appear on documents you can edit.',
    placement: 'left',
    optional: true,
  },
]

const UPLOAD_TOUR: TourStep[] = [
  {
    target: sel('upload-scope'),
    title: 'Sharing scope',
    content: 'Choose who will see the files you add next. Organization-wide is admin-only.',
    placement: 'bottom',
  },
  {
    target: sel('upload-storage'),
    title: 'Storage',
    content: 'Track how much of your quota is used so you know the next upload will fit.',
    placement: 'left',
  },
  {
    target: sel('upload-dropzone'),
    title: 'Add files',
    content: 'Drag & drop or browse. Files are staged so you can add metadata before uploading.',
    placement: 'top',
  },
]

const DETAILS_TOUR: TourStep[] = [
  {
    target: sel('details-preview'),
    title: 'Live preview',
    content: 'PDFs, images, and text render inline. Use the header to print or download.',
    placement: 'right',
  },
  {
    target: sel('details-ai'),
    title: 'AI metadata',
    content: 'The summary, sensitivity, and tags are AI-generated — editable if you have write access.',
    placement: 'left',
  },
  {
    target: sel('details-versions'),
    title: 'Versions',
    content: 'Download any version, upload a new one, or star a specific version to pin it as a favorite.',
    placement: 'left',
    optional: true,
  },
  {
    target: sel('details-share'),
    title: 'Sharing',
    content: 'Invite people at View or Edit permission, manage existing access, or copy a direct link.',
    placement: 'left',
    optional: true,
  },
]

const TOURS: Record<TourId, TourStep[]> = {
  main: MAIN_TOUR,
  library: LIBRARY_TOUR,
  upload: UPLOAD_TOUR,
  details: DETAILS_TOUR,
}

// Resolves a tour's steps for the given user, dropping role-gated steps the user
// can't see (e.g. the admin step for non-admins) so the controlled index stays
// aligned with what react-joyride renders.
export function buildSteps(tourId: TourId, roles: readonly string[] | undefined): TourStep[] {
  return TOURS[tourId].filter((step) => {
    if (step.role === 'admin') return isAdmin(roles)
    return true
  })
}

// Which contextual tour (if any) matches a given route, for the Help menu's
// "Tips for this page" entry.
export function contextualTourForPath(pathname: string): TourId | null {
  if (pathname.startsWith('/documents/')) return 'details'
  if (pathname === '/documents') return 'library'
  if (pathname === '/upload') return 'upload'
  return null
}
