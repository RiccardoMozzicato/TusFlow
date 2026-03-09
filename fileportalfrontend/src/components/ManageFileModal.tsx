import React, { useState, useEffect } from 'react';
import { Modal, Button, Alert, Spinner, Tabs, Tab, Table, Form, Badge } from 'react-bootstrap';
import { getFileShares, deleteFileShare, updateFileMetadata, deleteFile } from '../services/fileApi';
import type { FileMetadata, FileShare } from '../services/fileApi';
import { ShareFileModal } from './ShareFileModal';

interface ManageFileModalProps {
  show: boolean;
  onHide: () => void;
  onSuccess: () => void;
  file: FileMetadata | null;
}

export const ManageFileModal: React.FC<ManageFileModalProps> = ({ show, onHide, onSuccess, file }) => {
  const [shares, setShares] = useState<FileShare[]>([]);
  const [loadingShares, setLoadingShares] = useState(false);
  const [error, setError] = useState<string>('');
  const [deletingShareId, setDeletingShareId] = useState<number | null>(null);
  
  // Metadata state
  const [metadata, setMetadata] = useState<Record<string, string>>({});
  const [updatingMetadata, setUpdatingMetadata] = useState(false);
  const [newMetadataKey, setNewMetadataKey] = useState('');
  const [newMetadataValue, setNewMetadataValue] = useState('');
  
  // Share modal
  const [showShareModal, setShowShareModal] = useState(false);
  
  // Delete file confirmation
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deletingFile, setDeletingFile] = useState(false);

  useEffect(() => {
    if (show && file) {
      loadFileShares();
      setMetadata({});
    }
  }, [show, file]);

  const loadFileShares = async () => {
    if (!file) return;
    
    try {
      setLoadingShares(true);
      setError('');
      const response = await getFileShares(file.id);
      setShares(response.content);
    } catch (err) {
      console.error('Error loading file shares:', err);
      if (err instanceof Error && err.message.includes('404')) {
        setShares([]);
      } else {
        setError(err instanceof Error ? err.message : 'Failed to load file shares');
      }
    } finally {
      setLoadingShares(false);
    }
  };

  const handleDeleteShare = async (shareId: number) => {
    if (!file) return;
    
    try {
      setDeletingShareId(shareId);
      await deleteFileShare(file.id, shareId);
      await loadFileShares();
    } catch (err) {
      console.error('Error deleting share:', err);
      setError(err instanceof Error ? err.message : 'Failed to delete share');
    } finally {
      setDeletingShareId(null);
    }
  };

  const handleUpdateMetadata = async () => {
    if (!file || Object.keys(metadata).length === 0) {
      setError('Please add at least one metadata field');
      return;
    }

    try {
      setUpdatingMetadata(true);
      setError('');
      await updateFileMetadata(file.id, file.version || 1, metadata);
      setMetadata({});
      setError('');
      alert('Metadata updated successfully!');
    } catch (err) {
      console.error('Error updating metadata:', err);
      setError(err instanceof Error ? err.message : 'Failed to update metadata');
    } finally {
      setUpdatingMetadata(false);
    }
  };

  const handleDeleteFile = async () => {
    if (!file) return;

    try {
      setDeletingFile(true);
      await deleteFile(file.id);
      setShowDeleteConfirm(false);
      onSuccess();
      onHide();
    } catch (err) {
      console.error('Error deleting file:', err);
      setError(err instanceof Error ? err.message : 'Failed to delete file');
    } finally {
      setDeletingFile(false);
    }
  };

  const addMetadataField = () => {
    if (newMetadataKey.trim() && newMetadataValue.trim()) {
      if (metadata[newMetadataKey.trim()]) {
        setError('Metadata key already exists');
        return;
      }
      setMetadata({ ...metadata, [newMetadataKey.trim()]: newMetadataValue.trim() });
      setNewMetadataKey('');
      setNewMetadataValue('');
      setError('');
    } else {
      setError('Both key and value are required');
    }
  };

  const removeMetadataField = (key: string) => {
    const newMetadata = { ...metadata };
    delete newMetadata[key];
    setMetadata(newMetadata);
  };

  const handleShareSuccess = () => {
    loadFileShares();
  };

  const handleClose = () => {
    setMetadata({});
    setError('');
    setShowDeleteConfirm(false);
    onHide();
  };

  return (
    <>
      <Modal show={show} onHide={handleClose} size="lg" centered>
        <Modal.Header closeButton>
          <Modal.Title>Manage File</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && (
            <Alert variant="danger" onClose={() => setError('')} dismissible>
              {error}
            </Alert>
          )}

          {file && (
            <>
              <div className="alert alert-info mb-3">
                <strong>File:</strong> {file.fileName}
                <br />
                <small className="text-muted">
                  Type: {file.contentType || 'Unknown'} | Version: {file.version || 1}
                </small>
              </div>

              <Tabs defaultActiveKey="shares" className="mb-3">
                <Tab eventKey="shares" title="Shares">
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <h6>File Shares</h6>
                    <Button
                      variant="success"
                      size="sm"
                      onClick={() => setShowShareModal(true)}
                    >
                      + Add Share
                    </Button>
                  </div>

                  {loadingShares ? (
                    <div className="text-center py-3">
                      <Spinner animation="border" />
                    </div>
                  ) : shares.length === 0 ? (
                    <p className="text-muted text-center py-3">No shares found</p>
                  ) : (
                    <Table responsive hover size="sm">
                      <thead>
                        <tr>
                          <th>User ID</th>
                          <th>Permission</th>
                          <th>Shared At</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {shares.map((share) => (
                          <tr key={share.id}>
                            <td><small>{share.userID}</small></td>
                            <td>
                              <Badge bg={share.permission === 'READ_ONLY' ? 'info' : 'warning'}>
                                {share.permission}
                              </Badge>
                            </td>
                            <td><small>{new Date(share.timestamp).toLocaleString()}</small></td>
                            <td>
                              <Button
                                variant="outline-danger"
                                size="sm"
                                onClick={() => handleDeleteShare(share.id!)}
                                disabled={deletingShareId === share.id}
                              >
                                {deletingShareId === share.id ? (
                                  <Spinner animation="border" size="sm" />
                                ) : (
                                  '🗑️'
                                )}
                              </Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  )}
                </Tab>

                <Tab eventKey="metadata" title="Metadata">
                  <h6 className="mb-3">Update Metadata</h6>
                  
                  {/* Add Metadata Form */}
                  <div className="card mb-3">
                    <div className="card-body">
                      <Form.Group className="mb-2">
                        <Form.Label>Key</Form.Label>
                        <Form.Control
                          type="text"
                          placeholder="e.g., description, category, author"
                          value={newMetadataKey}
                          onChange={(e) => setNewMetadataKey(e.target.value)}
                          disabled={updatingMetadata}
                        />
                      </Form.Group>
                      <Form.Group className="mb-3">
                        <Form.Label>Value</Form.Label>
                        <Form.Control
                          type="text"
                          placeholder="Enter metadata value"
                          value={newMetadataValue}
                          onChange={(e) => setNewMetadataValue(e.target.value)}
                          disabled={updatingMetadata}
                        />
                      </Form.Group>
                      <Button
                        variant="outline-primary"
                        size="sm"
                        onClick={addMetadataField}
                        disabled={updatingMetadata || !newMetadataKey.trim() || !newMetadataValue.trim()}
                      >
                        + Add Field
                      </Button>
                    </div>
                  </div>

                  {Object.keys(metadata).length === 0 ? (
                    <p className="text-muted text-center py-3">No metadata fields added</p>
                  ) : (
                    <Table responsive size="sm" className="mb-3">
                      <thead>
                        <tr>
                          <th>Key</th>
                          <th>Value</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(metadata).map(([key, value]) => (
                          <tr key={key}>
                            <td><code>{key}</code></td>
                            <td>{value}</td>
                            <td>
                              <Button
                                variant="outline-danger"
                                size="sm"
                                onClick={() => removeMetadataField(key)}
                              >
                                ×
                              </Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  )}

                  <div className="d-grid">
                    <Button
                      variant="primary"
                      onClick={handleUpdateMetadata}
                      disabled={updatingMetadata || Object.keys(metadata).length === 0}
                    >
                      {updatingMetadata ? (
                        <>
                          <Spinner animation="border" size="sm" className="me-2" />
                          Updating...
                        </>
                      ) : (
                        'Update Metadata'
                      )}
                    </Button>
                  </div>
                </Tab>
              </Tabs>
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <div className="d-flex justify-content-between w-100">
            <Button
              variant="danger"
              onClick={() => setShowDeleteConfirm(true)}
              disabled={deletingFile}
            >
              {deletingFile ? (
                <>
                  <Spinner animation="border" size="sm" className="me-2" />
                  Deleting...
                </>
              ) : (
                'Delete File'
              )}
            </Button>
            <Button variant="secondary" onClick={handleClose} disabled={deletingFile}>
              Close
            </Button>
          </div>

          {/* Delete Confirmation Alert */}
          {showDeleteConfirm && (
            <div className="alert alert-danger mt-3 mb-0">
              <p className="mb-2">
                <strong>Are you sure?</strong> This will permanently delete the file and cannot be undone.
                {shares.length > 0 && (
                  <>
                    <br />
                    <small className="text-warning">
                      ⚠️ This file has {shares.length} active share{shares.length > 1 ? 's' : ''} that will also be deleted.
                    </small>
                  </>
                )}
              </p>
              <div className="d-flex gap-2">
                <Button
                  variant="danger"
                  size="sm"
                  onClick={handleDeleteFile}
                  disabled={deletingFile}
                >
                  Yes, Delete File
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setShowDeleteConfirm(false)}
                  disabled={deletingFile}
                >
                  Cancel
                </Button>
              </div>
            </div>
          )}
        </Modal.Footer>
      </Modal>

      {/* Share File Modal */}
      <ShareFileModal
        show={showShareModal}
        onHide={() => setShowShareModal(false)}
        onSuccess={handleShareSuccess}
        file={file}
      />
    </>
  );
};
