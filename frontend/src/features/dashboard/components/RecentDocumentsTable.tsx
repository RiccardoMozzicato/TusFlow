import { Link, useNavigate } from 'react-router-dom'
import Icon from '@/components/Icon'
import type { DocStatus, DocumentRow } from '../types'

const STATUS_STYLES: Record<DocStatus, { label: string; className: string; icon?: string }> = {
  processed: {
    label: 'Processed',
    className: 'bg-ds-tertiary-fixed/30 text-ds-tertiary',
  },
  in_review: {
    label: 'In Review',
    className: 'bg-ds-surface-variant text-ds-on-surface-variant',
  },
  failed: {
    label: 'Failed',
    className: 'bg-ds-error-container text-ds-error',
    icon: 'error',
  },
}

function StatusBadge({ status }: { status: DocStatus }) {
  const { label, className, icon } = STATUS_STYLES[status]
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-ds-full px-2 py-1 font-ds-label text-ds-label-sm ${className}`}
    >
      {icon ? <Icon name={icon} className="text-[12px]" /> : null}
      {label}
    </span>
  )
}

const HEADERS = ['Document Name', 'Type', 'Status', 'Date Modified']

function DocumentTableRow({ doc, last }: { doc: DocumentRow; last: boolean }) {
  const navigate = useNavigate()
  const to = `/documents/${doc.id}`

  return (
    <tr
      onClick={() => navigate(to)}
      className={`group cursor-pointer transition-colors hover:bg-ds-surface/50 ${
        last ? '' : 'border-b border-ds-outline-variant/30'
      }`}
    >
      <td className="px-6 py-4">
        <div className="flex items-center gap-3">
          <div
            className={`flex h-8 w-8 items-center justify-center rounded-ds ${doc.iconClassName}`}
          >
            <Icon name={doc.icon} className="text-[18px]" />
          </div>
          {/* Real link for keyboard/middle-click; the whole row also navigates. */}
          <Link
            to={to}
            onClick={(e) => e.stopPropagation()}
            className="font-medium text-ds-on-surface transition-colors group-hover:text-ds-primary"
          >
            {doc.name}
          </Link>
        </div>
      </td>
      <td className="px-6 py-4 text-ds-secondary">{doc.type}</td>
      <td className="px-6 py-4">
        <StatusBadge status={doc.status} />
      </td>
      <td className="px-6 py-4 text-ds-secondary">{doc.modified}</td>
      <td className="px-6 py-4 text-right">
        <Link
          to={to}
          onClick={(e) => e.stopPropagation()}
          aria-label={`Open ${doc.name}`}
          className="inline-flex text-ds-secondary opacity-0 transition-opacity hover:text-ds-primary group-hover:opacity-100"
        >
          <Icon name="open_in_new" />
        </Link>
      </td>
    </tr>
  )
}

export default function RecentDocumentsTable({
  documents,
}: {
  documents: DocumentRow[] | null
}) {
  return (
    <div className="w-full overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.02)]">
      <div className="flex items-center justify-between border-b border-ds-outline-variant bg-ds-surface-bright px-6 py-5">
        <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
          Recent Documents
        </h3>
        <Link
          to="/documents"
          className="font-ds-label text-ds-label-md text-ds-primary hover:underline"
        >
          View all
        </Link>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-ds-outline-variant/50 bg-ds-surface-container-lowest">
              {HEADERS.map((header) => (
                <th
                  key={header}
                  className="px-6 py-4 font-ds-label text-ds-label-md font-medium text-ds-on-surface-variant"
                >
                  {header}
                </th>
              ))}
              <th className="px-6 py-4 text-right font-ds-label text-ds-label-md font-medium text-ds-on-surface-variant">
                Action
              </th>
            </tr>
          </thead>
          <tbody className="text-ds-body-sm text-ds-on-surface">
            {documents === null || documents.length === 0 ? (
              <tr>
                <td
                  colSpan={HEADERS.length + 1}
                  className="px-6 py-10 text-center text-ds-on-surface-variant"
                >
                  {documents === null ? 'Loading documents…' : 'No documents yet.'}
                </td>
              </tr>
            ) : (
              documents.map((doc, index) => (
                <DocumentTableRow
                  key={doc.name}
                  doc={doc}
                  last={index === documents.length - 1}
                />
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
