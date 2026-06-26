import Icon from '@/components/Icon'
import type { ProcessingStep } from '../../types'
import DetailSection from './DetailSection'

interface ProcessingTimelineProps {
  steps: ProcessingStep[]
}

// Vertical processing timeline. A connector line runs behind the step dots;
// the final completed step is highlighted as the current state.
export default function ProcessingTimeline({ steps }: ProcessingTimelineProps) {
  return (
    <DetailSection icon="history_toggle_off" title="Status">
      <div className="relative ml-2 flex flex-col">
        <div className="absolute bottom-4 left-[7px] top-2 w-px bg-ds-tertiary-fixed-dim" />
        {steps.map((step, index) => {
          const isLast = index === steps.length - 1
          return (
            <div
              key={step.label}
              className={`relative z-10 flex items-start gap-4 ${isLast ? '' : 'pb-4'}`}
            >
              <div
                className={`mt-0.5 flex h-4 w-4 items-center justify-center rounded-ds-full shadow-sm ${
                  step.done ? 'bg-ds-tertiary' : 'bg-ds-surface-container-highest'
                } ${isLast && step.done ? 'ring-4 ring-ds-tertiary/10' : ''}`}
              >
                {step.done ? (
                  <Icon name="check" className="text-[12px] text-ds-on-tertiary" />
                ) : null}
              </div>
              <div>
                <p
                  className={`font-ds-body text-ds-body-sm font-medium leading-none ${
                    isLast && step.done ? 'text-ds-tertiary' : 'text-ds-on-surface'
                  }`}
                >
                  {step.label}
                </p>
                {step.timestamp ? (
                  <p className="mt-1 font-ds-label text-ds-label-sm text-ds-secondary">
                    {step.timestamp}
                  </p>
                ) : null}
              </div>
            </div>
          )
        })}
      </div>
    </DetailSection>
  )
}
