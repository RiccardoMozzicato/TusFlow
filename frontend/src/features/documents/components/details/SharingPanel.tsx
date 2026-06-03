import { useState } from 'react'
import Icon from '@/components/Icon'
import DetailSection from './DetailSection'

type Permission = 'view' | 'edit'

const COPIED_RESET_MS = 2000

// Share controls: invite by email/group with a permission level, plus a
// copy-link affordance. Submission is a placeholder until the share API exists;
// copy-link uses the live document URL.
export default function SharingPanel() {
  const [recipient, setRecipient] = useState('')
  const [permission, setPermission] = useState<Permission>('view')
  const [copied, setCopied] = useState(false)

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
        <div className="flex gap-2">
          <input
            type="email"
            value={recipient}
            onChange={(e) => setRecipient(e.target.value)}
            placeholder="Share with users or groups"
            className="flex-1 rounded-ds-md border border-ds-outline-variant bg-ds-surface px-3 py-2 font-ds-body text-ds-body-sm text-ds-on-surface outline-none transition-all placeholder:text-ds-secondary focus:border-ds-primary focus:ring-1 focus:ring-ds-primary"
          />
          <select
            value={permission}
            onChange={(e) => setPermission(e.target.value as Permission)}
            aria-label="Permission level"
            className="cursor-pointer rounded-ds-md border border-ds-outline-variant bg-ds-surface px-2 py-2 font-ds-body text-ds-body-sm text-ds-on-surface outline-none focus:ring-1 focus:ring-ds-primary"
          >
            <option value="view">View</option>
            <option value="edit">Edit</option>
          </select>
        </div>
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
