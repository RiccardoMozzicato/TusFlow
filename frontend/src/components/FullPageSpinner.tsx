interface FullPageSpinnerProps {
  label?: string
}

export default function FullPageSpinner({ label }: FullPageSpinnerProps) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-ds-surface-dim font-ds-body">
      <div
        className="h-10 w-10 animate-spin rounded-ds-full border-4 border-ds-outline-variant border-t-ds-primary"
        role="status"
        aria-label={label ?? 'Loading'}
      />
      {label ? <p className="text-sm text-ds-on-surface-variant">{label}</p> : null}
    </div>
  )
}
