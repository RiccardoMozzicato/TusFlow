import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Modal, Button, Spinner, Alert, Badge, Form } from 'react-bootstrap';
import { updateGeneratedMetadata, streamFileGeneratedMetadata, retryFailedMetadataGeneration } from '../services/fileApi';
import type { MetadataAIDTO, SensitivityLevel } from '../services/fileApi';

interface MetadataAIModalProps {
  show: boolean;
  onHide: () => void;
  fileId: string;
  fileName: string;
  version?: number;
}

export const MetadataAIModal: React.FC<MetadataAIModalProps> = ({
  show,
  onHide,
  fileId,
  fileName,
  version
}) => {
  const streamSessionRef = useRef(0);
  const [metadata, setMetadata] = useState<MetadataAIDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [processingStatus, setProcessingStatus] = useState<string>('idle');
  const eventSourceRef = React.useRef<EventSource | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [formDirty, setFormDirty] = useState(false);
  const [editSummary, setEditSummary] = useState('');
  const [editTags, setEditTags] = useState('');
  const [editSensitivity, setEditSensitivity] = useState<SensitivityLevel | ''>('');
  const abortControllerRef = React.useRef<AbortController | null>(null);

    const startMetadataStream = useCallback((signal: AbortSignal) => {
        streamSessionRef.current += 1;
        const sessionId = streamSessionRef.current;
        console.log(`Starting metadata stream via SSE for file ${fileName} v${version}`);
        setLoading(true);
        setError(null);
        setProcessingStatus('connecting');

        // Try SSE first
        const eventSource = streamFileGeneratedMetadata(
            fileId,
            version,
            (data) => handleSSEUpdate(data, sessionId),
            (msg) => handleSSEError(msg, sessionId),
            signal
        );

        if (eventSource) {
            eventSourceRef.current = eventSource;
        }
    }, [fileId, version]);

  useEffect(() => {
    if (show) {
      // Create new AbortController for this modal session
      abortControllerRef.current = new AbortController();
      startMetadataStream(abortControllerRef.current.signal);
    }

    // Cleanup: close SSE connection when modal closes
    return () => {
      if (eventSourceRef.current) {
        console.log('Closing SSE connection');
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [show, fileId, version, startMetadataStream]);

  useEffect(() => {
    if (metadata && !formDirty) {
      setEditSummary(metadata.summary ?? '');
      const tagsValue = Array.isArray(metadata.tags)
        ? metadata.tags.join(', ')
        : metadata.tags ?? '';
      setEditTags(tagsValue);
      setEditSensitivity((metadata.sensitivity ?? '') as SensitivityLevel | '');
    }
  }, [metadata, formDirty]);


  const handleSSEUpdate = (data: any, sessionId: number) => {
    if (sessionId !== streamSessionRef.current) return;
    console.log('SSE Update received:', data);

    switch (data.status) {
      case 'completed':
        console.log('Metadata received via SSE');
        setMetadata(data.data);
        setProcessingStatus('completed');
        setLoading(false);
        break;

      case 'processing':
        setProcessingStatus(`processing (attempt ${data.attempt})`);
        break;

      case 'waiting':
        setProcessingStatus('waiting');
        break;

      case 'failed':
        setError('AI analysis failed: ' + (data.error || 'Unknown error'));
        setProcessingStatus('failed');
        setLoading(false);
        break;

      case 'unsupported':
        setError('File type not supported: ' + (data.error || 'This file format cannot be analyzed'));
        setProcessingStatus('unsupported');
        setLoading(false);
        break;

      case 'timeout':
        setError(data.error || 'AI analysis is taking longer than expected. The file may be too large or complex. Please try again later.');
        setProcessingStatus('timeout');
        setLoading(false);
        break;

      case 'error':
        setError(data.error || 'An error occurred during AI analysis');
        setProcessingStatus('error');
        setLoading(false);
        break;
    }
  };

  const handleSSEError = (errorMessage: string, sessionId: number) => {
    if (sessionId !== streamSessionRef.current) return;
    console.error('SSE Error:', errorMessage);
    setError(errorMessage);
    setProcessingStatus('error');
    setLoading(false);
    abortControllerRef.current?.abort()
  };

  const retryMetadataGeneration = async () => {
    if (!fileId) return;

    if (version === undefined) {
      setError('Cannot retry metadata generation: missing file version');
      setProcessingStatus('error');
      return;
    }

    // Close any existing SSE and abort ongoing requests
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Reset UI state
    abortControllerRef.current = new AbortController();
    const signal = abortControllerRef.current.signal;

    setMetadata(null);
    setSaveError(null);
    setSaveSuccess(null);
    setSaving(false);
    setFormDirty(false);
    setEditSummary('');
    setEditTags('');
    setEditSensitivity('');
    setError(null);
    setProcessingStatus('retrying');
    setLoading(true);

    try {
        await updateGeneratedMetadata(fileId, {"summary": ""} ,version)
      await retryFailedMetadataGeneration(fileId, version);

      // After successfully triggering retry, reopen SSE to listen for updates
      startMetadataStream(signal);
    } catch (err) {
      console.error('Retry failed:', err);
      const msg = err instanceof Error ? err.message : 'Failed to trigger retry';
      setError(msg);
      setProcessingStatus('error');
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setMetadata(null);
    setError(null);
    setLoading(false);
    setProcessingStatus('idle');
    setSaveError(null);
    setSaveSuccess(null);
    setSaving(false);
    setFormDirty(false);
    setEditSummary('');
    setEditTags('');
    setEditSensitivity('');
    onHide();
  };

  const handleSaveMetadata = async () => {
    if (!fileId) return;

    if (!editSensitivity) {
      setSaveError('Please choose a sensitivity level.');
      return;
    }

    try {
      setSaving(true);
      setSaveError(null);
      setSaveSuccess(null);

      await updateGeneratedMetadata(
        fileId,
        {
          summary: editSummary.trim(),
          tags: editTags.trim(),
          sensitivity: editSensitivity,
        },
        version
      );

      setMetadata((prev) => ({
        ...(prev ?? {}),
        summary: editSummary.trim(),
        tags: editTags.trim(),
        sensitivity: editSensitivity,
      }));
      setFormDirty(false);
      setSaveSuccess('AI metadata updated successfully.');
    } catch (err) {
      console.error('Error updating AI metadata:', err);
      setSaveError(err instanceof Error ? err.message : 'Failed to update AI metadata');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal show={show} onHide={handleClose} size="lg">
      <Modal.Header closeButton>
        <Modal.Title>
          🤖 AI-Generated Metadata
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {/* AI Disclaimer */}
        <Alert variant="warning" className="mb-3">
          <Alert.Heading className="h6">⚠️ AI-Generated Content Disclaimer</Alert.Heading>
          <p className="mb-0 small">
            This metadata has been generated using artificial intelligence and may contain inaccuracies or errors. 
            Please verify the information before using it for critical decisions. AI-generated content should be 
            reviewed and validated by a human.
          </p>
        </Alert>

        {/* File Info */}
        <div className="mb-3">
          <strong>File:</strong> {fileName}
          {version && <Badge bg="info" className="ms-2">v{version}</Badge>}
        </div>

        {error ? (
          <Alert variant="danger" className="d-flex align-items-start">
            <div className="me-3" style={{ fontSize: '2rem' }}>⚠️</div>
            <div>
              <Alert.Heading className="h6 mb-2">AI Analysis Failed</Alert.Heading>
              <p className="mb-2">{error}</p>
              <hr className="my-2" />
              <div className="small">
                <strong>Possible reasons:</strong>
                <ul className="mb-0 mt-1">
                  <li>The file is too large or complex</li>
                  <li>The AI service is temporarily unavailable</li>
                  <li>The file format is not supported</li>
                </ul>
              </div>
            </div>
          </Alert>
        ) : loading || !metadata ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status" variant="primary">
              <span className="visually-hidden">AI processing...</span>
            </Spinner>
            <p className="mt-3">🤖 AI is analyzing the file...</p>
            <p className="text-muted small">Status: {processingStatus}</p>
            <p className="text-muted small">This may take a few moments. Please wait...</p>
          </div>
        ) : metadata ? (
          <>
            {/* Summary */}
            {metadata.summary && (
              <div className="mb-3">
                <h6>📝 Summary</h6>
                <p className="text-muted">{metadata.summary}</p>
              </div>
            )}

            {/* Tags */}
            {metadata.tags && (
              <div className="mb-3">
                <h6>🏷️ Tags</h6>
                <div className="d-flex flex-wrap gap-2">
                  {Array.isArray(metadata.tags) ? (
                    metadata.tags.map((tag, index) => (
                      <Badge 
                        key={index} 
                        bg={tag.toLowerCase() === 'failed' ? 'danger' : 'primary'}
                      >
                        {tag}
                      </Badge>
                    ))
                  ) : (
                    // Split string by common delimiters (comma, semicolon, space) and create separate badges
                    metadata.tags.split(/,+/).filter(tag => tag.trim()).map((tag, index) => (
                      <Badge 
                        key={index} 
                        bg={tag.trim().toLowerCase() === 'failed' ? 'danger' : 'primary'}
                      >
                        {tag.trim()}
                      </Badge>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* Sensitivity */}
            {metadata.sensitivity && (
              <div className="mb-3">
                <h6>🔒 Sensitivity Level</h6>
                <Badge 
                  bg={
                    metadata.sensitivity === 'HIGHLY_CONFIDENTIAL' ? 'danger' :
                    metadata.sensitivity === 'CONFIDENTIAL' ? 'warning' :
                    metadata.sensitivity === 'INTERNAL' ? 'secondary' :
                    'info'
                  }
                >
                  {metadata.sensitivity}
                </Badge>
              </div>
            )}

            <hr />

            <h6 className="mb-3">✏️ Edit AI Metadata</h6>
            {saveError && (
              <Alert variant="danger" onClose={() => setSaveError(null)} dismissible>
                {saveError}
              </Alert>
            )}
            {saveSuccess && (
              <Alert variant="success" onClose={() => setSaveSuccess(null)} dismissible>
                {saveSuccess}
              </Alert>
            )}

            <Form.Group className="mb-3">
              <Form.Label>Summary <small className="text-muted">(max 500 characters)</small></Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                maxLength={500}
                value={editSummary}
                onChange={(e) => {
                  setEditSummary(e.target.value);
                  setFormDirty(true);
                }}
                disabled={saving}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Tags (comma-separated) <small className="text-muted">(max 100 characters)</small></Form.Label>
              <Form.Control
                type="text"
                maxLength={100}
                value={editTags}
                onChange={(e) => {
                  setEditTags(e.target.value);
                  setFormDirty(true);
                }}
                disabled={saving}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Sensitivity</Form.Label>
              <Form.Select
                value={editSensitivity}
                onChange={(e) => {
                  setEditSensitivity(e.target.value as SensitivityLevel);
                  setFormDirty(true);
                }}
                disabled={saving}
              >
                <option value="" disabled>
                  Select a level
                </option>
                <option value="PUBLIC">PUBLIC</option>
                <option value="INTERNAL">INTERNAL</option>
                <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                <option value="HIGHLY_CONFIDENTIAL">HIGHLY_CONFIDENTIAL</option>
              </Form.Select>
            </Form.Group>

            <div className="d-grid">
              <Button
                variant="primary"
                onClick={handleSaveMetadata}
                disabled={saving || !editSensitivity}
              >
                {saving ? (
                  <>
                    <Spinner animation="border" size="sm" className="me-2" />
                    Saving...
                  </>
                ) : (
                  'Save AI Metadata'
                )}
              </Button>
            </div>
          </>
        ) : null}
      </Modal.Body>
      <Modal.Footer>
          {error && <Button variant="primary" onClick={retryMetadataGeneration}>Retry</Button>}
          <Button variant="secondary" onClick={handleClose}>Close</Button>
      </Modal.Footer>
    </Modal>
  );
};
