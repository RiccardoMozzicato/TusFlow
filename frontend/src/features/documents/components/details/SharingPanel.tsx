import { useMemo, useState } from 'react'
import Icon from '@/components/Icon'
import { useOrgUsers } from '@/features/users/useOrgUsers'
import { Permission } from '../../detailsApi'
import type { DocumentShare } from '../../types'
import DetailSection from './DetailSection'
import ShareUserCombobox, { type ShareTarget } from './ShareUserCombobox'

interface SharingPanelProps {
  shares: DocumentShare[]
  isLoadingShares: boolean
  isCreating: boolean
  createError: string | null
  onCreateShare: (userId: string, permission: Permission) => void
  /** Whether to surface per-share remove/permission controls (needs write access). */
  canManage: boolean
  /** Busy flag while a share is being removed or updated. */
  isMutating: boolean
  mutationError: string | null
  onRemoveShare: (shareId: number) => void
  onUpdatePermission: (shareId: number, permission: Permission) => void
}

const COPIED_RESET_MS = 2000

// Human label for each permission level.
const PERMISSION_LABEL: Record<Permission, string> = {
  [Permission.READ_ONLY]: 'View',
  [Permission.READ_WRITE]: 'Edit',
}

// Shows a UUID compactly (first segment) so the list stays readable.
function shortId(userId: string): string {
  return userId.split('-')[0] ?? userId
}

// Share controls: lists who a file is shared with and invites a user (picked
// from org suggestions, or by pasted UUID) at a chosen permission level.
// Copy-link uses the live document URL.
export default function SharingPanel({
  shares,
  isLoadingShares,
  isCreating,
  createError,
  onCreateShare,
  canManage,
  isMutating,
  mutationError,
  onRemoveShare,
  onUpdatePermission,
}: SharingPanelProps) {
  const [target, setTarget] = useState<ShareTarget | null>(null)
  const [permission, setPermission] = useState<Permission>(Permission.READ_ONLY)
  const [copied, setCopied] = useState(false)
  // Bumped after a successful share to remount (and reset) the combobox.
  const [resetKey, setResetKey] = useState(0)

  const { users, isLoading: isLoadingUsers, error: usersError, load } = useOrgUsers()

  // Resolve known users to display names, both in the shares list and below.
  const usersById = useMemo(
    () => new Map(users.map((user) => [user.id, user])),
    [users],
  )
  // Hide already-shared users from suggestions to avoid duplicate shares.
  const excludeIds = useMemo(
    () => new Set(shares.map((share) => share.userId)),
    [shares],
  )

  const canSubmit = target != null && !isCreating

  function handleShare() {
    if (!target || isCreating) return
    onCreateShare(target.userId, permission)
    setTarget(null)
    setResetKey((key) => key + 1)
  }

  async function handleCopyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setCopied(true)
      window.setTimeout(() => setCopied(false), COPIED_RESET_MS)
    } catch {
      // Clipboard can be blocked (insecure context / denied permission);
      // fail quietly rather than throwing in the UI.
      setCopied(false)
    }
  }

  return (
    <DetailSection icon="share" title="Share">
      <div className="flex flex-col gap-3">
        {isLoadingShares ? (
          <p className="font-ds-body text-ds-body-sm text-ds-secondary">Loading shares…</p>
        ) : shares.length > 0 ? (
          <ul className="flex flex-col gap-1">
            {shares.map((share) => (
              <li
                key={share.id}
                className="flex items-center justify-between rounded-ds bg-ds-surface-container px-2 py-1.5"
              >
                <span className="flex items-center gap-2 font-ds-body text-ds-body-sm text-ds-on-surface">
                  <Icon name="person" className="text-[16px] text-ds-secondary" />
                  {(() => {
                    // Prefer the server-resolved name, then a locally-loaded user,
                    // falling back to the shortened UUID only when neither is known.
                    const name = share.userName ?? usersById.get(share.userId)?.fullName
                    return name ? (
                      <span className="truncate">{name}</span>
                    ) : (
                      <span className="font-mono">{shortId(share.userId)}</span>
                    )
                  })()}
                </span>
                {canManage && share.shareId != null ? (
                  <span className="flex items-center gap-1">
                    <select
                      value={share.permission}
                      disabled={isMutating}
                      onChange={(e) =>
                        onUpdatePermission(share.shareId as number, e.target.value as Permission)
                      }
                      aria-label={`Permission for ${share.userName ?? share.userId}`}
                      className="cursor-pointer rounded-ds-md border border-ds-outline-variant bg-ds-surface px-1.5 py-0.5 font-ds-label text-ds-label-sm text-ds-on-surface outline-none focus:ring-1 focus:ring-ds-primary disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <option value={Permission.READ_ONLY}>View</option>
                      <option value={Permission.READ_WRITE}>Edit</option>
                    </select>
                    <button
                      type="button"
                      onClick={() => onRemoveShare(share.shareId as number)}
                      disabled={isMutating}
                      title="Remove share"
                      aria-label={`Remove share for ${share.userName ?? share.userId}`}
                      className="rounded-ds-md p-1 text-ds-secondary transition-colors hover:bg-ds-surface-variant hover:text-ds-error disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <Icon name="close" filled={false} className="text-[16px]" />
                    </button>
                  </span>
                ) : (
                  <span className="rounded-ds-full bg-ds-surface-variant px-2 py-0.5 font-ds-label text-ds-label-sm text-ds-on-surface">
                    {PERMISSION_LABEL[share.permission as Permission] ?? share.permission}
                  </span>
                )}
              </li>
            ))}
          </ul>
        ) : null}

        {!isLoadingShares && shares.length === 0 ? (
          <p className="font-ds-body text-ds-body-sm text-ds-secondary">
            Not shared with anyone yet.
          </p>
        ) : null}

        {mutationError ? (
          <p className="font-ds-body text-ds-body-sm text-ds-error">{mutationError}</p>
        ) : null}

        <div className="flex gap-2">
          <ShareUserCombobox
            key={resetKey}
            users={users}
            isLoading={isLoadingUsers}
            loadError={usersError}
            excludeIds={excludeIds}
            disabled={isCreating}
            onFocusLoad={load}
            onResolvedChange={setTarget}
          />
          <select
            value={permission}
            onChange={(e) => setPermission(e.target.value as Permission)}
            aria-label="Permission level"
            className="cursor-pointer rounded-ds-md border border-ds-outline-variant bg-ds-surface px-2 py-2 font-ds-body text-ds-body-sm text-ds-on-surface outline-none focus:ring-1 focus:ring-ds-primary"
          >
            <option value={Permission.READ_ONLY}>View</option>
            <option value={Permission.READ_WRITE}>Edit</option>
          </select>
        </div>

        {createError ? (
          <p className="font-ds-body text-ds-body-sm text-ds-error">{createError}</p>
        ) : null}

        <button
          type="button"
          onClick={handleShare}
          disabled={!canSubmit}
          className="flex w-full items-center justify-center gap-2 rounded-ds-md bg-ds-primary py-2 font-ds-label text-ds-label-md text-ds-on-primary transition-colors hover:bg-ds-primary-container disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Icon name="person_add" filled={false} className="text-[18px]" />
          {isCreating ? 'Sharing…' : 'Share'}
        </button>

        <button
          type="button"
          onClick={handleCopyLink}
          className="flex w-full items-center justify-center gap-2 rounded-ds-md border border-ds-outline-variant bg-ds-surface-container py-2 font-ds-label text-ds-label-md text-ds-on-surface-variant transition-colors hover:bg-ds-surface-variant"
        >
          <Icon name={copied ? 'check' : 'link'} filled={false} className="text-[18px]" />
          {copied ? 'Link Copied' : 'Copy Link'}
        </button>
      </div>
    </DetailSection>
  )
}
