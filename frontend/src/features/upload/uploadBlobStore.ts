import type { SharingScope } from '@/features/documents/documentsApi'

// Persists in-flight uploads (their bytes + descriptor) in IndexedDB so they can
// be resumed after a full page reload — e.g. logout → login. A live tus upload
// holds an in-memory File handle that can't be serialized to localStorage, but a
// File is a structured-cloneable Blob, so IndexedDB can keep the bytes. tus keeps
// the resumable upload URL in its own localStorage; together they let us rebuild
// the upload and continue from the server's offset on the next session.

const DB_NAME = 'fileportal-uploads'
const DB_VERSION = 1
const STORE_NAME = 'uploads'

/** A persisted in-flight upload: enough to rebuild and resume the tus transfer. */
export interface PersistedUpload {
  /** Client-generated file UUID — the tus fileId and our queue row id. */
  fileId: string
  /** UUID of the user who started the upload; restore only resumes the current user's. */
  ownerId: string
  name: string
  size: number
  /** MIME type, restored onto the rebuilt File. */
  type: string
  scope: SharingScope
  /** User-entered metadata, re-sent as tus Upload-Metadata. */
  metadata: Record<string, string>
  /** Whether the upload was paused by the user; restored uploads honor this. */
  paused: boolean
  /** Confirmed progress (0–1) captured when paused, so a restored paused upload
   *  shows its real offset instead of "0 B". Absent until first paused. */
  progress?: number
  /** Epoch ms when first persisted; lets callers reason about staleness. */
  createdAt: number
  /** The raw file bytes. */
  blob: Blob
}

// IndexedDB is unavailable in non-browser contexts (tests, SSR); callers degrade
// gracefully (no persistence) rather than crash.
function hasIndexedDb(): boolean {
  return typeof indexedDB !== 'undefined'
}

let dbPromise: Promise<IDBDatabase> | null = null

function openDb(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION)
      request.onupgradeneeded = () => {
        const db = request.result
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'fileId' })
        }
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }
  return dbPromise
}

// Runs a transaction on the uploads store and resolves once it completes.
async function withStore<T>(
  mode: IDBTransactionMode,
  run: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await openDb()
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, mode)
    const request = run(tx.objectStore(STORE_NAME))
    tx.oncomplete = () => resolve(request.result)
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

/**
 * Saves (or overwrites) a persisted upload. May reject if the browser storage
 * quota is exceeded — callers treat that as non-fatal (the upload still runs in
 * memory; it just won't survive a reload).
 */
export async function putUpload(record: PersistedUpload): Promise<void> {
  if (!hasIndexedDb()) return
  await withStore('readwrite', (store) => store.put(record))
}

/**
 * Updates the paused flag of a persisted upload (and, when given, the confirmed
 * progress captured at pause) so a restore can bring it back in the same state and
 * show its real offset. Read-modify-write in one transaction; a no-op if the record
 * is gone (e.g. the upload already completed).
 */
export async function setUploadPaused(
  fileId: string,
  paused: boolean,
  progress?: number,
): Promise<void> {
  if (!hasIndexedDb()) return
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const getRequest = store.get(fileId)
    getRequest.onsuccess = () => {
      const record = getRequest.result as PersistedUpload | undefined
      if (record) {
        store.put({ ...record, paused, ...(progress !== undefined ? { progress } : {}) })
      }
    }
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

/** Returns every persisted upload, for rehydration on app boot. */
export async function getAllUploads(): Promise<PersistedUpload[]> {
  if (!hasIndexedDb()) return []
  const db = await openDb()
  return new Promise<PersistedUpload[]>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const request = tx.objectStore(STORE_NAME).getAll()
    request.onsuccess = () => resolve(request.result as PersistedUpload[])
    request.onerror = () => reject(request.error)
  })
}

/** Removes a persisted upload once it completes, is cancelled, or fails terminally. */
export async function deleteUpload(fileId: string): Promise<void> {
  if (!hasIndexedDb()) return
  await withStore('readwrite', (store) => store.delete(fileId))
}
