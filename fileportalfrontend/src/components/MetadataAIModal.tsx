import React, { useState, useEffect } from 'react';
import { Modal, Button, Spinner, Alert, Badge, ListGroup } from 'react-bootstrap';
import { getFileGeneratedMetadata } from '../services/fileApi';
import type { MetadataAIDTO } from '../services/fileApi';

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
  const [metadata, setMetadata] = useState<MetadataAIDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortControllerRef = React.useRef<AbortController | null>(null);

  useEffect(() => {
    if (show) {
      // Create new AbortController for this modal session
      abortControllerRef.current = new AbortController();
      loadMetadata(abortControllerRef.current.signal);
    }
    
    // Cleanup: abort ongoing requests when modal closes
    return () => {
      if (abortControllerRef.current) {
        console.log('Aborting AI metadata polling requests');
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [show, fileId, version]);

  const loadMetadata = async (signal: AbortSignal) => {
    const maxRetries = 5;
    let attempt = 0;
    
    try {
      setLoading(true);
      setError(null);
      
      while (attempt < maxRetries) {
        // Check if request was aborted
        if (signal.aborted) {
          console.log('AI metadata polling aborted by user');
          return;
        }
        
        attempt++;
        console.log(`AI metadata fetch attempt ${attempt}/${maxRetries}`);
        
        const data = await getFileGeneratedMetadata(fileId, version);
        console.log('AI Metadata retrieved from server:', data);
        console.log('Tags type:', typeof data.tags, 'Is array:', Array.isArray(data.tags));
        console.log('Key Entities type:', typeof data.keyEntities, 'Is array:', Array.isArray(data.keyEntities));
        console.log('Topics type:', typeof data.topics, 'Is array:', Array.isArray(data.topics));
        
        // Check if data is still being processed (null or "Failed" indicates processing/failure)
        if (data.tags !== null && data.summary !== null && 
            data.summary !== 'Failed to analyze' && data.tags !== 'Failed') {
          // Success - data is ready
          console.log(`AI metadata ready on attempt ${attempt}`);
          setMetadata(data);
          setLoading(false);
          return;
        }
        
        // Data still processing, retry with exponential backoff
        if (attempt < maxRetries) {
          const delayMs = Math.min(1000 * attempt, 5000); // 1s, 2s, 3s, 4s, 5s max
          console.log(`AI still processing, retrying in ${delayMs}ms...`);
          setMetadata(data); // Show processing state
          
          // Wait with ability to abort
          await new Promise((resolve, reject) => {
            const timeout = setTimeout(resolve, delayMs);
            signal.addEventListener('abort', () => {
              clearTimeout(timeout);
              reject(new DOMException('Aborted', 'AbortError'));
            });
          });
        } else {
          // Max retries reached, check if data is still null
          console.log('Max retries reached, showing current state');
          if (data.tags === null || data.summary === null) {
            // AI processing failed or timed out
            setError('AI analysis is taking longer than expected. The file may be too large or complex. Please try again later.');
          }
          setMetadata(data);
        }
      }
    } catch (err) {
      // Don't show error if it was just aborted by user
      if (err instanceof DOMException && err.name === 'AbortError') {
        console.log('AI metadata fetch aborted');
        return;
      }
      console.error('Error loading AI metadata:', err);
      setError(err instanceof Error ? err.message : 'Failed to load AI-generated metadata');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    // Abort ongoing requests
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setMetadata(null);
    setError(null);
    setLoading(false);
    onHide();
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
        ) : metadata && (metadata.tags === null || metadata.summary === null || loading) ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status" variant="primary">
              <span className="visually-hidden">AI processing...</span>
            </Spinner>
            <p className="mt-3">🤖 AI is analyzing the file...</p>
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
                    metadata.sensitivity === 'HIGH' ? 'danger' :
                    metadata.sensitivity === 'MEDIUM' ? 'warning' :
                    metadata.sensitivity === 'PUBLIC' ? 'info' :
                    'success'
                  }
                >
                  {metadata.sensitivity}
                </Badge>
              </div>
            )}

            {/* Category */}
            {metadata.category && (
              <div className="mb-3">
                <h6>📂 Category</h6>
                <Badge bg="secondary">{metadata.category}</Badge>
              </div>
            )}

            {/* Language */}
            {metadata.language && (
              <div className="mb-3">
                <h6>🌐 Language</h6>
                <Badge bg="info">{metadata.language}</Badge>
              </div>
            )}

            {/* Key Entities */}
            {metadata.keyEntities && Array.isArray(metadata.keyEntities) && metadata.keyEntities.length > 0 && (
              <div className="mb-3">
                <h6>🎯 Key Entities</h6>
                <ListGroup variant="flush">
                  {metadata.keyEntities.map((entity, index) => (
                    <ListGroup.Item key={index}>{entity}</ListGroup.Item>
                  ))}
                </ListGroup>
              </div>
            )}

            {/* Topics */}
            {metadata.topics && Array.isArray(metadata.topics) && metadata.topics.length > 0 && (
              <div className="mb-3">
                <h6>💡 Topics</h6>
                <div className="d-flex flex-wrap gap-2">
                  {metadata.topics.map((topic, index) => (
                    <Badge key={index} bg="info" className="text-dark">{topic}</Badge>
                  ))}
                </div>
              </div>
            )}

            {/* Sentiment */}
            {metadata.sentiment && (
              <div className="mb-3">
                <h6>😊 Sentiment</h6>
                <Badge 
                  bg={
                    metadata.sentiment === 'POSITIVE' ? 'success' :
                    metadata.sentiment === 'NEGATIVE' ? 'danger' :
                    'secondary'
                  }
                >
                  {metadata.sentiment}
                </Badge>
              </div>
            )}

            {/* Confidence Score */}
            {metadata.confidenceScore !== undefined && metadata.confidenceScore !== null && (
              <div className="mb-3">
                <h6>📊 Confidence Score</h6>
                <div className="d-flex align-items-center">
                  <div className="progress flex-grow-1 me-2" style={{ height: '25px' }}>
                    <div 
                      className={`progress-bar ${
                        metadata.confidenceScore >= 0.8 ? 'bg-success' :
                        metadata.confidenceScore >= 0.5 ? 'bg-warning' :
                        'bg-danger'
                      }`}
                      role="progressbar" 
                      style={{ width: `${metadata.confidenceScore * 100}%` }}
                      aria-valuenow={metadata.confidenceScore * 100} 
                      aria-valuemin={0} 
                      aria-valuemax={100}
                    >
                      {(metadata.confidenceScore * 100).toFixed(1)}%
                    </div>
                  </div>
                </div>
              </div>
            )}
          </>
        ) : (
          <Alert variant="info">
            No metadata available for this file.
          </Alert>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose}>
          Close
        </Button>
      </Modal.Footer>
    </Modal>
  );
};
