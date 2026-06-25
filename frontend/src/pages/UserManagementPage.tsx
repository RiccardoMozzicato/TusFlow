import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import { useAppSelector } from '@/app/hooks'
import { useDebouncedValue } from '@/lib/useDebouncedValue'
import { selectCurrentUser } from '@/features/auth/authSlice'
import LibraryPager from '@/features/documents/components/LibraryPager'
import { useUserManagement } from '@/features/users/useUserManagement'
import type { UserCreationPayload, UserDTO } from '@/features/users/usersApi'
import UsersTable from '@/features/users/components/UsersTable'
import UserFormModal from '@/features/users/components/UserFormModal'
import ConfirmDialog from '@/components/ConfirmDialog'

type Dialog =
  | { kind: 'create' }
  | { kind: 'edit'; user: UserDTO }
  | { kind: 'delete'; user: UserDTO }
  | null

// How long the search box waits after the last keystroke before querying the
// server, so a burst of typing results in a single request.
const SEARCH_DEBOUNCE_MS = 350

// Admin-only console for managing organization users: list, create, change role
// and storage quota, and remove. Reached via the gated /admin/users route.
// Search (name/email) and column sort are server-side and reflected in the URL
// (?search=…&sort=…&page=…), so the view is bookmarkable — mirroring the Library.
export default function UserManagementPage() {
  const currentUser = useAppSelector(selectCurrentUser)
  const {
    users,
    status,
    error,
    pageInfo,
    filters,
    applyFilters,
    goToPage,
    reload,
    isSaving,
    saveError,
    clearSaveError,
    create,
    update,
    remove,
  } = useUserManagement()

  const [dialog, setDialog] = useState<Dialog>(null)

  // The search box updates this local state immediately (responsive typing); the
  // debounced value is committed into the URL's `search` filter.
  const [searchInput, setSearchInput] = useState(filters.search ?? '')
  const debouncedSearch = useDebouncedValue(searchInput, SEARCH_DEBOUNCE_MS)

  // Keep the box in sync when the query changes from outside (Back/Forward).
  useEffect(() => {
    setSearchInput(filters.search ?? '')
  }, [filters.search])

  // Commit the debounced query into the URL as a normalized value (trimmed,
  // blank → undefined). Guarded so an unchanged query doesn't push a new entry.
  useEffect(() => {
    const next = debouncedSearch.trim() || undefined
    if (next !== filters.search) applyFilters({ ...filters, search: next })
    // applyFilters/filters are derived from the URL; depending on the committed
    // value keeps this from re-firing on unrelated renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch, filters.search])

  function openDialog(next: Dialog) {
    clearSaveError()
    setDialog(next)
  }

  async function handleCreate(payload: UserCreationPayload) {
    if (await create(payload)) setDialog(null)
  }

  async function handleUpdate(user: UserDTO) {
    if (await update(user)) setDialog(null)
  }

  async function handleDelete(id: string) {
    if (await remove(id)) setDialog(null)
  }

  const isInitialLoading = status === 'loading' && users.length === 0

  return (
    <div className="mx-auto flex max-w-[1160px] flex-col gap-6">
      <section className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <Link
            to="/admin"
            className="mb-1 inline-flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-secondary transition-colors hover:text-ds-primary"
          >
            <Icon name="chevron_left" filled={false} className="text-[16px]" />
            Admin Console
          </Link>
          <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
            User Management
          </h2>
          <p className="mt-1 font-ds-body text-ds-body-sm text-ds-secondary">
            Manage members of your organization — roles, storage quotas, and access.
          </p>
        </div>
        <button
          type="button"
          onClick={() => openDialog({ kind: 'create' })}
          className="flex items-center gap-2 rounded-ds-lg bg-ds-primary px-5 py-2.5 font-ds-label text-ds-label-md text-ds-on-primary transition-opacity hover:opacity-90"
        >
          <Icon name="person_add" filled={false} className="text-[18px]" />
          Add User
        </button>
      </section>

      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        <div className="flex items-center gap-2 border-b border-ds-outline-variant bg-ds-surface-bright px-6 py-3">
          <Icon name="search" className="text-[20px] text-ds-on-surface-variant" />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by name or email…"
            className="w-full bg-transparent font-ds-body text-ds-body-sm text-ds-on-surface outline-none placeholder:text-ds-secondary"
          />
        </div>

        {/* min-height keeps the card a consistent size whether it holds one row
            or a full page, so it never collapses to a thin strip. */}
        <div className="min-h-[28rem]">
          {isInitialLoading ? (
            <p className="px-6 py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
              Loading users…
            </p>
          ) : status === 'failed' ? (
            <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
              <p className="font-ds-body text-ds-body-sm text-ds-error">
                {error ?? 'Failed to load users.'}
              </p>
              <button
                type="button"
                onClick={reload}
                className="inline-flex items-center rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high"
              >
                Retry
              </button>
            </div>
          ) : users.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
              <Icon name="group_off" filled={false} className="text-[40px] text-ds-secondary" />
              <p className="font-ds-body text-ds-body-sm text-ds-secondary">
                {filters.search ? 'No users match your search.' : 'No users found.'}
              </p>
            </div>
          ) : (
            <UsersTable
              users={users}
              onEdit={(user) => openDialog({ kind: 'edit', user })}
              onDelete={(user) => openDialog({ kind: 'delete', user })}
              sort={filters.sort}
              onSortChange={(next) => applyFilters({ ...filters, sort: next })}
            />
          )}
        </div>

        <div className="flex items-center justify-between border-t border-ds-outline-variant bg-ds-surface-container-low px-6 py-3">
          <p className="font-ds-label text-ds-label-sm text-ds-secondary">
            {pageInfo.totalElements} user{pageInfo.totalElements === 1 ? '' : 's'}
          </p>
          <LibraryPager
            pageNumber={pageInfo.page}
            totalPages={pageInfo.totalPages}
            isFirst={pageInfo.isFirst}
            isLast={pageInfo.isLast}
            onPrev={() => goToPage(pageInfo.page - 1)}
            onNext={() => goToPage(pageInfo.page + 1)}
          />
        </div>
      </div>

      {dialog?.kind === 'create' || dialog?.kind === 'edit' ? (
        <UserFormModal
          user={dialog.kind === 'edit' ? dialog.user : null}
          defaultOrganization={currentUser?.organization ?? ''}
          isSaving={isSaving}
          saveError={saveError}
          onClose={() => setDialog(null)}
          onCreate={handleCreate}
          onUpdate={handleUpdate}
        />
      ) : null}

      {dialog?.kind === 'delete' ? (
        <ConfirmDialog
          title="Delete user"
          message={`Permanently remove ${dialog.user.fullName || dialog.user.email}? This cannot be undone.`}
          confirmLabel="Delete"
          destructive
          isBusy={isSaving}
          onConfirm={() => handleDelete(dialog.user.id)}
          onCancel={() => setDialog(null)}
        />
      ) : null}
    </div>
  )
}
