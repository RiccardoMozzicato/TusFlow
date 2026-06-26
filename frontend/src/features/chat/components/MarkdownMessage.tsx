import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'

// The RAG model answers in Markdown (headings, lists, code, tables, links).
// We map each element onto the design-system tokens so the rendered answer
// matches the rest of the panel. Sizes use `em`, so they scale with the
// container `fontSize` the panel sets from the user-controlled zoom.
const COMPONENTS: Components = {
  p: ({ children }) => <p className="whitespace-pre-wrap break-words">{children}</p>,
  h1: ({ children }) => <h1 className="mt-2 text-[1.4em] font-semibold text-ds-on-surface">{children}</h1>,
  h2: ({ children }) => <h2 className="mt-2 text-[1.25em] font-semibold text-ds-on-surface">{children}</h2>,
  h3: ({ children }) => <h3 className="mt-2 text-[1.1em] font-semibold text-ds-on-surface">{children}</h3>,
  ul: ({ children }) => <ul className="list-disc space-y-1 pl-5">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal space-y-1 pl-5">{children}</ol>,
  li: ({ children }) => <li className="break-words">{children}</li>,
  a: ({ children, href }) => (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="text-ds-primary underline underline-offset-2 hover:opacity-80"
    >
      {children}
    </a>
  ),
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  blockquote: ({ children }) => (
    <blockquote className="border-l-2 border-ds-outline-variant pl-3 italic text-ds-on-surface-variant">
      {children}
    </blockquote>
  ),
  hr: () => <hr className="border-ds-outline-variant" />,
  code: ({ className, children }) => {
    // react-markdown gives block code a language class; inline code has none.
    const isBlock = Boolean(className)
    if (isBlock) {
      return (
        <code className={`${className ?? ''} font-mono text-[0.9em]`}>{children}</code>
      )
    }
    return (
      <code className="rounded-ds bg-ds-surface-container px-1 py-0.5 font-mono text-[0.85em] text-ds-on-surface">
        {children}
      </code>
    )
  },
  pre: ({ children }) => (
    <pre className="overflow-x-auto rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container p-3">
      {children}
    </pre>
  ),
  table: ({ children }) => (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-[0.95em]">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border border-ds-outline-variant px-2 py-1 text-left font-semibold">{children}</th>
  ),
  td: ({ children }) => (
    <td className="border border-ds-outline-variant px-2 py-1 align-top">{children}</td>
  ),
}

export default function MarkdownMessage({ text }: { text: string }) {
  return (
    <div className="space-y-3">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={COMPONENTS}>
        {text}
      </ReactMarkdown>
    </div>
  )
}