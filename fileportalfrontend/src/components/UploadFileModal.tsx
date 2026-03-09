import React, { useState } from 'react';
import { Modal, Form, Button, Alert, Row, Col } from 'react-bootstrap';
import { v4 as uuidv4 } from 'uuid';
import uploadFile, { SharingScope, type UploadController } from "../services/tusUpload.ts";
import { useAuth } from '../hooks/useAuth';

interface UploadFileModalProps {
  show: boolean;
  onHide: () => void;
  onSuccess: () => void;
}

interface MetadataField {
  key: string;
  value: string;
}


export const UploadFileModal: React.FC<UploadFileModalProps> = ({ show, onHide, onSuccess }) => {
  const { user } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string>('');
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [metadataFields, setMetadataFields] = useState<MetadataField[]>([]);
  const [sharingScope, setSharingScope] = useState<SharingScope>(SharingScope.PERSONAL);
  const [uploadController, setUploadController] = useState<UploadController | null>(null);
  const [isPaused, setIsPaused] = useState(false);

  // Determine available scopes based on user role
  const userRoles = user?.roles || [];
  const isAdmin = userRoles.includes('admin');
  const isManager = userRoles.includes('manager');
  const isStaff = userRoles.includes('staff');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setFile(e.target.files[0]);
      setError('');
    }
  };

  const handleAddMetadata = () => {
    setMetadataFields([...metadataFields, { key: '', value: '' }]);
  };

  const handleRemoveMetadata = (index: number) => {
    setMetadataFields(metadataFields.filter((_, i) => i !== index));
  };

  const handleMetadataChange = (index: number, field: 'key' | 'value', value: string) => {
    const updated = [...metadataFields];
    updated[index][field] = value;
    setMetadataFields(updated);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!file) {
      setError('Please select a file');
      return;
    }

    console.log('Starting upload process...');
    setUploading(true);
    setError('');
    setUploadProgress(0);
    setIsPaused(false);

    try {
      const fileId = uuidv4();
      
      // Build metadata object from fields
       const metadata: Record<string, string> = {};
       metadataFields.forEach(field => {
           if (field.key && field.value) {
           metadata[field.key] = field.value;
         }
       });

      console.log('Creating upload controller...');
      // Upload file with progress tracking and get controller
      const controller = uploadFile(file, fileId, sharingScope, metadata, (percentage) => {
        setUploadProgress(percentage);
      });

      setUploadController(controller);
      console.log('Upload controller created, waiting for completion...');

      // Wait for upload to complete
      await controller.promise;

      console.log('Upload completed successfully!');
      // Reset form after successful upload
      setFile(null);
      setMetadataFields([]);
      setSharingScope(SharingScope.PERSONAL);
      setUploadProgress(0);
      setUploading(false);
      setUploadController(null);
      setIsPaused(false);
      onSuccess();
      onHide();
    } catch (err) {
      console.error('Upload error caught:', err);
      const errorMessage = err instanceof Error ? err.message : 'Upload failed';
      setError(errorMessage);

      // Only reset if it's not a user-cancelled upload
      if (errorMessage !== 'Upload cancelled by user') {
        setUploading(false);
        setUploadController(null);
        setIsPaused(false);
      }
    }
  };

  const handlePause = () => {
    if (uploadController && !isPaused) {
      uploadController.pause();
      setIsPaused(true);
    }
  };

  const handleResume = () => {
    if (uploadController && isPaused) {
      uploadController.resume();
      setIsPaused(false);
    }
  };

  const handleAbort = () => {
    if (uploadController) {
      uploadController.abort();
      setUploading(false);
      setUploadController(null);
      setIsPaused(false);
      setUploadProgress(0);
      setError('Upload cancelled');
    }
  };

  const handleClose = () => {
    console.log('handleClose called, uploading:', uploading);
    if (!uploading) {
      console.log('Closing modal...');
      setFile(null);
      setMetadataFields([]);
      setSharingScope(SharingScope.PERSONAL);
      setError('');
      setUploadProgress(0);
      setUploadController(null);
      setIsPaused(false);
      onHide();
    } else {
      console.log('Cannot close modal: upload in progress');
    }
  };

  return (
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton={!uploading}>
        <Modal.Title>Upload File</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="danger" onClose={() => setError('')} dismissible>
            {error}
          </Alert>
        )}
        
        <Form onSubmit={handleSubmit}>
          <Form.Group className="mb-3">
            <Form.Label>Select File</Form.Label>
            <Form.Control
              type="file"
              onChange={handleFileChange}
              disabled={uploading}
              required
            />
            {file && (
              <Form.Text className="text-muted">
                Selected: {file.name} ({(file.size / 1024 / 1024).toFixed(2)} MB)
              </Form.Text>
            )}
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Sharing Scope</Form.Label>
            <Form.Select
              value={sharingScope}
              onChange={(e) => setSharingScope(e.target.value as SharingScope)}
              disabled={uploading}
              required
            >
              <option value={SharingScope.PERSONAL}>Personal - Only visible to you</option>
              {(isManager || isAdmin) && (
                <option value={SharingScope.DEPARTMENT}>Department - Visible to your department</option>
              )}
              {isAdmin && (
                <option value={SharingScope.ORGANIZATION}>Organization - Visible to entire organization</option>
              )}
            </Form.Select>
            <Form.Text className="text-muted">
              {isStaff && !isManager && !isAdmin && 'As a staff member, you can only upload personal files'}
              {isManager && !isAdmin && 'As a manager, you can upload personal and departmental files'}
              {isAdmin && 'As an admin, you can upload files with any scope'}
            </Form.Text>
          </Form.Group>

          <Form.Group className="mb-3">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <Form.Label className="mb-0">Additional Metadata (Optional)</Form.Label>
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={handleAddMetadata}
                disabled={uploading}
              >
                + Add Field
              </Button>
            </div>
            {metadataFields.map((field, index) => (
              <Row key={index} className="mb-2">
                <Col xs={5}>
                  <Form.Control
                    type="text"
                    placeholder="Key"
                    value={field.key}
                    onChange={(e) => handleMetadataChange(index, 'key', e.target.value)}
                    disabled={uploading}
                  />
                </Col>
                <Col xs={5}>
                  <Form.Control
                    type="text"
                    placeholder="Value"
                    value={field.value}
                    onChange={(e) => handleMetadataChange(index, 'value', e.target.value)}
                    disabled={uploading}
                  />
                </Col>
                <Col xs={2}>
                  <Button
                    variant="outline-danger"
                    size="sm"
                    onClick={() => handleRemoveMetadata(index)}
                    disabled={uploading}
                  >
                    ×
                  </Button>
                </Col>
              </Row>
            ))}
            <Form.Text className="text-muted">
              Add custom key-value pairs for file metadata (e.g., description, category, tags)
            </Form.Text>
          </Form.Group>

          {uploading && (
            <div className="mb-3">
              <div className="d-flex justify-content-between mb-2">
                <span className="text-muted">
                  {isPaused ? 'Upload paused' : 'Uploading file...'}
                </span>
                <span className="fw-bold">{uploadProgress.toFixed(1)}%</span>
              </div>
              <div className="progress" style={{ height: '24px' }}>
                <div
                  className={`progress-bar ${!isPaused ? 'progress-bar-striped progress-bar-animated' : ''}`}
                  role="progressbar"
                  style={{ width: `${uploadProgress}%` }}
                  aria-valuenow={uploadProgress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  {uploadProgress >= 10 && `${uploadProgress.toFixed(1)}%`}
                </div>
              </div>
              <div className="d-flex justify-content-center gap-2 mt-3">
                {!isPaused ? (
                  <Button
                    type="button"
                    variant="warning"
                    size="sm"
                    onClick={handlePause}
                  >
                    ⏸ Pause
                  </Button>
                ) : (
                  <Button
                    type="button"
                    variant="success"
                    size="sm"
                    onClick={handleResume}
                  >
                    ▶ Resume
                  </Button>
                )}
                <Button
                  type="button"
                  variant="danger"
                  size="sm"
                  onClick={handleAbort}
                >
                  ✕ Abort
                </Button>
              </div>
              <div className="text-center mt-2 text-muted small">
                {isPaused
                  ? 'Upload paused. Click Resume to continue or Abort to cancel.'
                  : 'Please wait while the file is being uploaded. You can pause or abort the upload.'
                }
              </div>
            </div>
          )}

          <div className="d-flex justify-content-end gap-2">
            <Button type="button" variant="secondary" onClick={handleClose} disabled={uploading}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={uploading || !file}>
              {uploading ? 'Uploading...' : 'Upload'}
            </Button>
          </div>
        </Form>
      </Modal.Body>
    </Modal>
  );
};
