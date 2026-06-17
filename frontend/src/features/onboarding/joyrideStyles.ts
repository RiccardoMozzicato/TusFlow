import type { Locale, Options, PartialDeep, Styles } from 'react-joyride'

// react-joyride v3 splits configuration into `options` (behaviour + theme colors)
// and `styles` (per-element CSS). The raw `--ds-*` CSS variables live in :root, so
// var() references resolve at runtime and follow the active theme.

export const joyrideOptions: Partial<Options> = {
  primaryColor: 'var(--ds-primary)',
  backgroundColor: 'var(--ds-surface-container-lowest)',
  arrowColor: 'var(--ds-surface-container-lowest)',
  textColor: 'var(--ds-on-surface)',
  overlayColor: 'rgba(0, 0, 0, 0.5)',
  // Above the sidebar (z-20), chat (z-40) and dialogs (z-50).
  zIndex: 1000,
  width: 360,
  showProgress: true,
  // Skip the beacon so each guided step opens its tooltip immediately.
  skipBeacon: true,
  // Which controls render in the tooltip footer.
  buttons: ['skip', 'back', 'primary'],
  spotlightRadius: 8,
  // Don't dismiss the tour when the user clicks the dimmed overlay outside the
  // tooltip — they must use Skip/Back/Next (or the Done button) to leave.
  overlayClickAction: false,
  // Give async-mounted targets (post-navigation, data-loading pages) time to appear
  // before Joyride reports TARGET_NOT_FOUND.
  targetWaitTimeout: 3000,
}

export const joyrideStyles: PartialDeep<Styles> = {
  tooltip: {
    borderRadius: 16,
    padding: 20,
    fontFamily: 'var(--font-ds-body, inherit)',
  },
  tooltipTitle: {
    fontSize: 18,
    fontWeight: 700,
    color: 'var(--ds-on-surface)',
  },
  tooltipContent: {
    padding: '12px 0',
    fontSize: 14,
    lineHeight: 1.5,
    textAlign: 'left',
    color: 'var(--ds-on-surface-variant)',
  },
  buttonPrimary: {
    backgroundColor: 'var(--ds-primary)',
    color: 'var(--ds-on-primary)',
    borderRadius: 10,
    fontSize: 14,
    fontWeight: 600,
    padding: '8px 16px',
  },
  buttonBack: {
    color: 'var(--ds-primary)',
    fontSize: 14,
    marginRight: 8,
  },
  buttonSkip: {
    color: 'var(--ds-secondary)',
    fontSize: 14,
  },
}

export const joyrideLocale: Locale = {
  back: 'Back',
  close: 'Close',
  last: 'Done',
  next: 'Next',
  skip: 'Skip',
}
