interface IconProps {
  /** Material Symbols Outlined ligature name, e.g. "dashboard". */
  name: string
  /** Filled (FILL 1) vs outlined (FILL 0). Defaults to filled, matching the design. */
  filled?: boolean
  /** Tailwind classes for size/color, e.g. "text-[20px] text-ds-primary". */
  className?: string
}

// Thin wrapper over a Material Symbols span. Size and color come from Tailwind
// utility classes; only the FILL axis is set inline (it has no utility).
export default function Icon({ name, filled = true, className = '' }: IconProps) {
  return (
    <span
      className={`material-symbols-outlined ${className}`}
      style={filled ? { fontVariationSettings: "'FILL' 1" } : undefined}
      aria-hidden="true"
    >
      {name}
    </span>
  )
}
