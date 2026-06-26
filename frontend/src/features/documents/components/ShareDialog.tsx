import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import OverviewModal from '@/features/profile/components/OverviewModal'
import { documentPageUrl, Permission } from '../detailsApi'
import {
  createShare,
  loadShares,
  removeShare,
  resetDetail,
  selectCreateShareError,
  selectIsCreatingShare,
  selectIsMutatingShare,
  selectShareMutationError,
  selectShares,
  selectSharesStatus,
  updateShare,
} from '../documentDetailsSlice'
import type { LibraryDocument } from '../types'
import SharingPanel from './details/SharingPanel'

interface ShareDialogProps {
  doc: LibraryDocument
  onClose: () => void
}

// Manage-sharing modal opened from the library. Reuses the Document Details
// sharing store (the detail page is not mounted here, so the shared slice state
// is free) and the presentational SharingPanel.
export default function ShareDialog({ doc, onClose }: ShareDialogProps) {
  const dispatch = useAppDispatch()
  const shares = useAppSelector(selectShares)
  const sharesStatus = useAppSelector(selectSharesStatus)
  const isCreating = useAppSelector(selectIsCreatingShare)
  const createError = useAppSelector(selectCreateShareError)
  const isMutating = useAppSelector(selectIsMutatingShare)
  const mutationError = useAppSelector(selectShareMutationError)

  useEffect(() => {
    void dispatch(loadShares(doc.id))
    // Clear the shared detail slice when the modal closes so a later detail-page
    // visit starts clean.
    return () => {
      dispatch(resetDetail())
    }
  }, [dispatch, doc.id])

  const documentUrl = documentPageUrl(doc.id)

  return (
    <OverviewModal title={`Share "${doc.title}"`} onClose={onClose}>
      <SharingPanel
        shares={shares}
        isLoadingShares={sharesStatus === 'loading'}
        isCreating={isCreating}
        createError={createError}
        onCreateShare={(userId, permission: Permission) =>
          void dispatch(createShare({ id: doc.id, userId, permission }))
        }
        canManage={doc.isWritable}
        isMutating={isMutating}
        mutationError={mutationError}
        onRemoveShare={(shareId) => void dispatch(removeShare({ id: doc.id, shareId }))}
        onUpdatePermission={(shareId, permission) =>
          void dispatch(updateShare({ id: doc.id, shareId, permission }))
        }
        linkUrl={documentUrl}
      />
    </OverviewModal>
  )
}
