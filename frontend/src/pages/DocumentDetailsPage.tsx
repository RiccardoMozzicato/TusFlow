import { Link, useParams } from 'react-router-dom'
import Icon from '@/components/Icon'
import { getDocumentDetail } from '@/features/documents/mockData'
import DocumentDetailsHeader from '@/features/documents/components/details/DocumentDetailsHeader'
import DocumentPreview from '@/features/documents/components/details/DocumentPreview'
import PrimaryInfoCard from '@/features/documents/components/details/PrimaryInfoCard'
import MetadataSection from '@/features/documents/components/details/MetadataSection'
import ProcessingTimeline from '@/features/documents/components/details/ProcessingTimeline'
import VersionHistory from '@/features/documents/components/details/VersionHistory'
import SharingPanel from '@/features/documents/components/details/SharingPanel'

// Document Details: a document preview alongside a metadata panel (AI summary,
// properties, processing status, versions, sharing). Data is mock today, keyed
// by the `:id` route param; the file/job services will fill it once wired.
export default function DocumentDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const document = id ? getDocumentDetail(id) : undefined

  if (!document) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
        <Icon name="search_off" filled={false} className="text-[48px] text-ds-secondary" />
        <h2 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
          Document not found
        </h2>
        <p className="font-ds-body text-ds-body-sm text-ds-secondary">
          We couldn't find a document with that id.
        </p>
        <Link
          to="/documents"
          className="mt-2 rounded-ds-lg bg-ds-primary px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary transition-colors hover:bg-ds-primary-container"
        >
          Back to Library
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-6">
      <DocumentDetailsHeader breadcrumbs={document.breadcrumbs} />

      <div className="grid flex-1 grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="flex flex-col lg:col-span-8">
          <DocumentPreview pageCount={document.pageCount} />
        </div>

        <div className="flex flex-col gap-4 lg:col-span-4">
          <PrimaryInfoCard
            title={document.title}
            fileType={document.fileType}
            aiSummary={document.aiSummary}
          />
          <MetadataSection
            category={document.category}
            tags={document.tags}
            owner={document.owner}
            date={document.date}
          />
          <ProcessingTimeline steps={document.processing} />
          <VersionHistory versions={document.versions} />
          <SharingPanel />

          <button
            type="button"
            className="mt-2 flex w-full items-center justify-center gap-2 rounded-ds-lg border border-ds-tertiary-container bg-ds-tertiary px-6 py-3 font-ds-heading text-ds-body-md font-semibold text-ds-on-tertiary shadow-sm transition-all duration-200 hover:bg-ds-tertiary-container hover:shadow-md active:scale-[0.98]"
          >
            <Icon name="download" filled={false} className="text-[20px]" />
            Download Document
          </button>
        </div>
      </div>
    </div>
  )
}
