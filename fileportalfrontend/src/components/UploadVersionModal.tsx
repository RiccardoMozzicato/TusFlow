import React, { useState } from 'react';
import { Modal, Form, Button, Alert } from 'react-bootstrap';
import uploadFile, { type UploadController } from "../services/tusUpload.ts";
import type { FileMetadata } from '../services/fileApi';

interface UploadVersionModalProps {
  show: boolean;
  onHide: () => void;
  onSuccess: () => void;
  file: FileMetadata | null;
}

export const UploadVersionModal: React.FC<UploadVersionModalProps> = ({
  show,
  onHide,
  onSuccess,
  file
}) => {
  const [newFile, setNewFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string>('');
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [uploadController, setUploadController] = useState<UploadController | null>(null);
  const [isPaused, setIsPaused] = useState(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setNewFile(e.target.files[0]);
      setError('');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!newFile) {
      setError('Please select a file');
      return;
    }

    if (!file) {
      setError('Original file information is missing');
      return;
    }

    console.log(`Starting version upload for file ${file.id}...`);
    setUploading(true);
    setError('');
    setUploadProgress(0);
    setIsPaused(false);

    try {
      console.log('Creating upload controller for new version...');
      // Upload file with the existing file ID to create a new version
      const controller = uploadFile(
        newFile,
        file.id,
        file.sharingScope || 'PERSONAL',
        undefined,
        (percentage) => {
          setUploadProgress(percentage);
        }
      );

      setUploadController(controller);
      console.log('Upload controller created, waiting for completion...');

      // Wait for upload to complete
      await controller.promise;

      console.log('Version upload completed successfully!');
      // Reset form after successful upload
      setNewFile(null);
      setUploadProgress(0);
      setUploading(false);
      setUploadController(null);
      setIsPaused(false);
      onSuccess();
      onHide();
    } catch (err) {
      console.error('Version upload error caught:', err);
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
    if (!uploading) {
      setNewFile(null);
      setError('');
      setUploadProgress(0);
      setUploadController(null);
      setIsPaused(false);
      onHide();
    }
  };

  return (
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton={!uploading}>
        <Modal.Title>Upload New Version</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="danger" onClose={() => setError('')} dismissible>
            {error}
          </Alert>
        )}

        {file && (
          <Alert variant="info" className="mb-3">
            <div><strong>Current File:</strong> {file.fileName}</div>
            <div><strong>Current Version:</strong> v{file.version || 1}</div>
            <div><strong>Sharing Scope:</strong> {file.sharingScope || 'PERSONAL'}</div>
          </Alert>
        )}

        <Form onSubmit={handleSubmit}>
          <Form.Group className="mb-3">
            <Form.Label>Select New Version File</Form.Label>
            <Form.Control
              type="file"
              onChange={handleFileChange}
              disabled={uploading}
              required
            />
            {newFile && (
              <Form.Text className="text-muted">
                Selected: {newFile.name} ({(newFile.size / 1024 / 1024).toFixed(2)} MB)
              </Form.Text>
            )}
          </Form.Group>

          <Alert variant="warning">
            <small>
              <strong>Note:</strong> This will create version {(file?.version || 1) + 1} of the file.
              The new version will inherit the sharing scope of the original file.
            </small>
          </Alert>

          {uploading && (
            <div className="mb-3">
              <div className="d-flex justify-content-between mb-2">
                <span className="text-muted">
                  {isPaused ? 'Upload paused' : 'Uploading new version...'}
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
                  ✕ Cancel
                </Button>
              </div>
            </div>
          )}
        </Form>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose} disabled={uploading}>
          Close
        </Button>
        <Button
          variant="primary"
          onClick={handleSubmit}
          disabled={uploading || !newFile}
        >
          {uploading ? 'Uploading...' : 'Upload New Version'}
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

