import React, { useState, useEffect } from 'react';
import { Modal, Table, Button, Alert, Spinner, Badge } from 'react-bootstrap';
import { getFileVersions, downloadFileVersion, type FileVersion } from '../services/fileApi';
import type { FileMetadata } from '../services/fileApi';

interface FileVersionHistoryModalProps {
  show: boolean;
  onHide: () => void;
  file: FileMetadata | null;
}

export const FileVersionHistoryModal: React.FC<FileVersionHistoryModalProps> = ({
  show,
  onHide,
  file
}) => {
  const [versions, setVersions] = useState<FileVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const [downloading, setDownloading] = useState<number | null>(null);

  useEffect(() => {
    if (show && file) {
      loadVersions();
    }
  }, [show, file]);

  const loadVersions = async () => {
    if (!file) return;

    setLoading(true);
    setError('');

    try {
        console.log("Loading versions for file ID:", file.id);
      const versionsData = await getFileVersions(file.id);
      console.log('Loaded versions:', versionsData);
      setVersions(versionsData);
    } catch (err) {
      console.error('Error loading versions:', err);
      setError(err instanceof Error ? err.message : 'Failed to load versions');
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadVersion = async (version: FileVersion) => {
    if (!file) return;

    setDownloading(version.version);
    setError('');

    try {
      const blob = await downloadFileVersion(file.id, version.version);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = version.filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Error downloading version:', err);
      setError(err instanceof Error ? err.message : 'Failed to download version');
    } finally {
      setDownloading(null);
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleString();
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'AVAILABLE':
        return <Badge bg="success">Available</Badge>;
      case 'MERGE_PENDING':
        return <Badge bg="warning">Pending</Badge>;
      case 'DELETED':
        return <Badge bg="danger">Deleted</Badge>;
      default:
        return <Badge bg="secondary">{status}</Badge>;
    }
  };

  const handleClose = () => {
    setVersions([]);
    setError('');
    setDownloading(null);
    onHide();
  };

  return (
    <Modal show={show} onHide={handleClose} size="lg" centered>
      <Modal.Header closeButton>
        <Modal.Title>Version History</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {file && (
          <Alert variant="info" className="mb-3">
            <div><strong>File:</strong> {file.fileName}</div>
            <div><strong>Current Version:</strong> v{file.version || 1}</div>
          </Alert>
        )}

        {error && (
          <Alert variant="danger" onClose={() => setError('')} dismissible>
            {error}
          </Alert>
        )}

        {loading ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status">
              <span className="visually-hidden">Loading...</span>
            </Spinner>
            <p className="mt-3">Loading versions...</p>
          </div>
        ) : versions.length === 0 ? (
          <div className="text-center py-5">
            <p className="text-muted">No versions found</p>
          </div>
        ) : (
          <Table responsive hover>
            <thead>
              <tr>
                <th>Version</th>
                <th>Filename</th>
                <th>Size</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {versions.map((version) => {
                const statusUpper = version.status?.toUpperCase().trim() || '';
                const canDownload = statusUpper === 'AVAILABLE' || statusUpper === 'MERGE_PENDING';
                console.log(`Version ${version.version}: status='${version.status}', statusUpper='${statusUpper}', canDownload=${canDownload}`);
                return (
                <tr key={version.id} className={version.version === file?.version ? 'table-primary' : ''}>
                  <td>
                    <Badge bg="info">v{version.version}</Badge>
                    {version.version === file?.version && (
                      <Badge bg="success" className="ms-2">Current</Badge>
                    )}
                  </td>
                  <td>{version.filename}</td>
                  <td>{formatFileSize(version.size)}</td>
                  <td>{getStatusBadge(version.status)}</td>
                  <td>{formatDate(version.createdAt)}</td>
                  <td>
                    {canDownload ? (
                      <Button
                        variant="outline-primary"
                        size="sm"
                        onClick={() => handleDownloadVersion(version)}
                        disabled={downloading !== null}
                      >
                        {downloading === version.version ? (
                          <>
                            <Spinner
                              as="span"
                              animation="border"
                              size="sm"
                              role="status"
                              aria-hidden="true"
                              className="me-2"
                            />
                            Downloading...
                          </>
                        ) : (
                          '📥 Download'
                        )}
                      </Button>
                    ) : (
                      <span className="text-muted">N/A</span>
                    )}
                  </td>
                </tr>
              )})}
            </tbody>
          </Table>
        )}

        {versions.length > 0 && (
          <div className="mt-3">
            <small className="text-muted">
              <strong>Total Versions:</strong> {versions.length}
            </small>
          </div>
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

