import { useEffect, useRef, useState } from 'react';
import { renderAsync } from 'docx-preview';

export interface DocxFilePreviewerProps {
  /** Blob del docx da visualizzare */
  blob: Blob | null;
  /** Mostra errore nel componente */
  showError?: boolean;
  /** Altezza massima dell'area preview */
  maxHeightPx?: number;
}

/**
 * Componente React pronto all'uso che mostra in pagina la preview di un `.docx`.
 *
 * Nota: `docx-preview` supporta `.docx` (OOXML), non `.doc` legacy.
 */
export default function DocxFilePreviewer({
  blob,
  showError = true,
  maxHeightPx = 420
}: DocxFilePreviewerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setError('');

      if (!containerRef.current) return;

      // pulizia quando manca il blob
      if (!blob) {
        containerRef.current.innerHTML = '';
        return;
      }

      try {
        containerRef.current.innerHTML = '';

        const arrayBuffer = await blob.arrayBuffer();
        await renderAsync(arrayBuffer, containerRef.current, undefined, {
          inWrapper: true,
          breakPages: true
        });
      } catch (e) {
        if (cancelled) return;
        const message = e instanceof Error ? e.message : 'DOCX preview failed';
        setError(message);
      }
    }

    void run();

    return () => {
      cancelled = true;
    };
  }, [blob]);

  return (
    <div>
      {showError && error ? (
        <div style={{ color: '#b00020', marginBottom: 8 }}>{error}</div>
      ) : null}
      <div
        ref={containerRef}
        style={{
          maxHeight: maxHeightPx,
          overflow: 'auto',
          border: '1px solid #ddd',
          borderRadius: 6,
          padding: 8,
          background: '#fff'
        }}
      />
    </div>
  );
}
