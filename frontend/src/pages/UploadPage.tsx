import DropZone from '@/features/upload/components/DropZone'
import TransferQueue from '@/features/upload/components/TransferQueue'
import UploadSummaryBar from '@/features/upload/components/UploadSummaryBar'
import { UPLOAD_QUEUE, UPLOAD_SUMMARY } from '@/features/upload/mockData'

export default function UploadPage() {
  return (
    <>
      <header className="mb-6">
        <h2 className="mb-1 font-ds-heading text-ds-headline-md text-ds-on-surface">
          Upload Manager
        </h2>
        <p className="text-ds-body-sm text-ds-on-surface-variant">
          Drag and drop files or manage your current transfer queue.
        </p>
      </header>

      <DropZone />
      <TransferQueue items={UPLOAD_QUEUE} />

      <UploadSummaryBar summary={UPLOAD_SUMMARY} />
    </>
  )
}
