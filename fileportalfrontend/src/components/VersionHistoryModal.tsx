import React, { useState, useEffect, useCallback } from 'react';
import { Modal, Table, Button, Alert, Spinner, Badge } from 'react-bootstrap';
import { getFileVersions, downloadFileVersion } from '../services/fileApi';
import type { FileMetadata, FileVersion } from '../services/fileApi';

interface VersionHistoryModalProps {
  show: boolean;
  onHide: () => void;
  file: FileMetadata | null;
  onPreviewRequested?: (args: { fileId: string; version: number; contentType: string; filename: string }) => void,
  onGenerateMetadata: (file: FileMetadata) => void
}

export const VersionHistoryModal: React.FC<VersionHistoryModalProps> = ({
  show,
  onHide,
  file,
  onPreviewRequested,
  onGenerateMetadata
}) => {
  const [versions, setVersions] = useState<FileVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const [downloadingVersion, setDownloadingVersion] = useState<number | null>(null);

  // non gestiamo più la preview internamente; la UI di history si limita a chiedere al padre di aprire la preview

  const loadVersions = useCallback(async () => {
    if (!file) return;

    setLoading(true);
    setError('');
    try {
      const versionsList = await getFileVersions(file.id);
      setVersions(versionsList);
    } catch (err) {
      console.error('Error loading versions:', err);
      setError(err instanceof Error ? err.message : 'Failed to load versions');
    } finally {
      setLoading(false);
    }
  }, [file]);

  useEffect(() => {
    if (show && file) {
      void loadVersions();
    }
  }, [show, file, loadVersions]);

  const handleDownloadVersion = async (version: number, filename: string) => {
    if (!file) return;

    setDownloadingVersion(version);
    setError('');
    try {
      const blob = await downloadFileVersion(file.id, version);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename || `${file.fileName}_v${version}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Error downloading version:', err);
      setError(err instanceof Error ? err.message : 'Failed to download version');
    } finally {
      setDownloadingVersion(null);
    }
  };

  const handlePreviewVersion = (version: number, contentType: string, filename: string) => {
    if (!file) return;
    // deleghiamo al padre la gestione della preview (docx o iframe) e chiudiamo il modal di history
    onPreviewRequested?.({
      fileId: file.id,
      version,
      contentType,
      filename
    });
    onHide();
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

  const getStatusBadgeVariant = (status: string): string => {
    switch (status) {
      case 'AVAILABLE':
        return 'success';
      case 'MERGE_PENDING':
        return 'warning';
      case 'DELETED':
        return 'danger';
      default:
        return 'secondary';
    }
  };

  return (
    <Modal show={show} onHide={onHide} size="lg" centered>
      <Modal.Header closeButton>
        <Modal.Title>Version History</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="danger" onClose={() => setError('')} dismissible>
            {error}
          </Alert>
        )}

        {/* Il modal di history non contiene più la preview: la preview viene gestita dal componente padre */}

        {file && (
          <div className="mb-3">
            <h6>File: {file.fileName}</h6>
            <small className="text-muted">Current Version: v{file.version || 1}</small>
          </div>
        )}

        {loading ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status">
              <span className="visually-hidden">Loading versions...</span>
            </Spinner>
            <p className="mt-3">Loading version history...</p>
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
                <th>Uploaded</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {versions.map((version) => (
                <tr key={version.id}>
                  <td>
                    <Badge bg={version.version === file?.version ? 'primary' : 'secondary'}>
                      v{version.version}
                      {version.version === file?.version && ' (Current)'}
                    </Badge>
                  </td>
                  <td>{version.filename}</td>
                  <td>{formatFileSize(version.size)}</td>
                  <td>
                    <Badge bg={getStatusBadgeVariant(version.status)}>
                      {version.status}
                    </Badge>
                  </td>
                  <td>
                    <small>{formatDate(version.createdAt)}</small>
                  </td>
                  <td>
                    <Button
                      variant="outline-primary"
                      size="sm"
                      onClick={() => handleDownloadVersion(version.version, version.filename)}
                      disabled={
                        downloadingVersion === version.version ||
                        (version.status !== 'AVAILABLE' && version.status !== 'MERGE_PENDING')
                      }
                    >
                      {downloadingVersion === version.version ? (
                        <>
                          <Spinner
                            as="span"
                            animation="border"
                            size="sm"
                            role="status"
                            aria-hidden="true"
                            className="me-1"
                          />
                          Downloading...
                        </>
                      ) : (
                        '📥 Download'
                      )}
                    </Button>

                    <Button
                      variant="outline-secondary"
                      size="sm"
                      onClick={() => handlePreviewVersion(version.version, version.contentType, version.filename)}
                    >
                      👁️ Preview
                    </Button>

                    <Button
                      variant="outline-warning"
                      size="sm"
                      className="me-2"
                      onClick={() => {
                        if (!file) return;
                        // onHide()
                        onGenerateMetadata({...file, version: version.version});
                      }}
                      disabled={version.status !== 'AVAILABLE' && version.status !== 'MERGE_PENDING'}
                    >
                      🤖 Get Metadata
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={onHide}>
          Close
        </Button>
      </Modal.Footer>
    </Modal>
  );
};
