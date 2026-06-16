// Human-readable formatters for transfer-queue detail lines.

const KB = 1024
const MB = KB * 1024
const GB = MB * 1024

/** Formats a byte count as B / KB / MB / GB with one decimal above KB. */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '0 B'
  if (bytes < KB) return `${bytes} B`
  if (bytes < MB) return `${(bytes / KB).toFixed(1)} KB`
  if (bytes < GB) return `${(bytes / MB).toFixed(1)} MB`
  return `${(bytes / GB).toFixed(1)} GB`
}

/** Formats a transfer rate (bytes/second) as "X.X MB/s" (or KB/s when small). */
export function formatSpeed(bytesPerSec: number): string {
  if (!Number.isFinite(bytesPerSec) || bytesPerSec <= 0) return '—'
  if (bytesPerSec < MB) return `${(bytesPerSec / KB).toFixed(0)} KB/s`
  return `${(bytesPerSec / MB).toFixed(1)} MB/s`
}

/** Formats a remaining-time estimate as "Ns left" / "Nm left"; blank if unknown. */
export function formatEta(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return ''
  if (seconds < 60) return `${Math.ceil(seconds)}s left`
  return `${Math.ceil(seconds / 60)}m left`
}
