import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useAuth } from '../hooks/useAuth';
import { Container, Table, Button, Alert, Spinner, Badge, Form, Row, Col, Modal } from 'react-bootstrap';
import { Layout } from './Layout';
import {
    getFilesMetadata,
    getSharedFiles,
    getAllFiles,
    deleteFile,
    downloadFile,
    SharingScope, previewFile
} from '../services/fileApi';
import type { FileMetadata } from '../services/fileApi';
import { ShareFileModal } from './ShareFileModal';
import { UploadVersionModal } from './UploadVersionModal';
import { VersionHistoryModal } from './VersionHistoryModal';
import { MetadataAIModal } from './MetadataAIModal';
import { renderAsync } from 'docx-preview';

export const StaffDashboard: React.FC = () => {
  const { user } = useAuth();
  const [files, setFiles] = useState<FileMetadata[]>([]);
  const [sharedFiles, setSharedFiles] = useState<FileMetadata[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  
  // Shared files state
  const [sharedLoading, setSharedLoading] = useState(true);
  const [sharedError, setSharedError] = useState<string | null>(null);
  const [sharedTotalPages, setSharedTotalPages] = useState(0);
  const [sharedCurrentPage, setSharedCurrentPage] = useState(0);
  const [sharedPageSize, setSharedPageSize] = useState(10);
  
  // All files state
  const [allFiles, setAllFiles] = useState<FileMetadata[]>([]);
  const [allFilesLoading, setAllFilesLoading] = useState(false);
  const [allFilesError, setAllFilesError] = useState<string | null>(null);

  // Delete confirmation modal state
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [fileToDelete, setFileToDelete] = useState<FileMetadata | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Share file modal state
  const [showShareModal, setShowShareModal] = useState(false);
  const [fileToShare, setFileToShare] = useState<FileMetadata | null>(null);

  // Upload version modal state
  const [showUploadVersionModal, setShowUploadVersionModal] = useState(false);
  const [fileToVersion, setFileToVersion] = useState<FileMetadata | null>(null);

  // Version history modal state
  const [showVersionHistoryModal, setShowVersionHistoryModal] = useState(false);
  const [fileToViewHistory, setFileToViewHistory] = useState<FileMetadata | null>(null);

  // AI Metadata modal state
  const [showMetadataAIModal, setShowMetadataAIModal] = useState(false);
  const [fileToShowAI, setFileToShowAI] = useState<FileMetadata | null>(null);

  // Filters
  const [contentTypeFilter, setContentTypeFilter] = useState('');
  const [dateFromFilter, setDateFromFilter] = useState('');
  const [dateToFilter, setDateToFilter] = useState('');
  const [sortField, setSortField] = useState('createdAt');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
  const [pageSize, setPageSize] = useState(10);

  // DOCX preview state (inline)
  const [docxPreviewVisible, setDocxPreviewVisible] = useState(false);
  const [previewingDocx, setPreviewingDocx] = useState(false);
  const [fileInPreview, setFileInPreview] = useState<FileMetadata | null>(null);
  const docxPreviewRef = useRef<HTMLDivElement | null>(null);

  const closeDocxPreview = useCallback(() => {
    setDocxPreviewVisible(false);
    setPreviewingDocx(false);
    setFileInPreview(null);
    if (docxPreviewRef.current) docxPreviewRef.current.innerHTML = '';
  }, []);

  useEffect(() => {
    if (!docxPreviewVisible) return;

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        closeDocxPreview();
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [docxPreviewVisible, closeDocxPreview]);

  const loadPersonalFiles = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      console.log('Loading personal files from staff dashboard...');
      const response = await getFilesMetadata({
        sharingScope: SharingScope.PERSONAL,
        page: currentPage,
        size: pageSize,
        sort: `${sortField},${sortDirection}`,
        contentType: contentTypeFilter || undefined,
        dateFrom: dateFromFilter ? `${dateFromFilter}T00:00:00Z` : undefined,
        dateTo: dateToFilter ? `${dateToFilter}T23:59:59Z` : undefined
      });

      console.log(response);
      setFiles(response.content);
      setTotalPages(response.totalPages);
    } catch (err) {
      console.error('Error loading personal files:', err);
      // Handle 404 as empty result (no files found)
      if (err instanceof Error && err.message.includes('404')) {
        setFiles([]);
        setTotalPages(0);
        setError(null); // Don't show error for empty results
      } else {
        setError(err instanceof Error ? err.message : 'Failed to load files');
      }
    } finally {
      setLoading(false);
    }
  }, [
    contentTypeFilter,
    currentPage,
    dateFromFilter,
    dateToFilter,
    pageSize,
    sortDirection,
    sortField
  ]);

  const loadSharedFiles = useCallback(async () => {
    try {
      setSharedLoading(true);
      setSharedError(null);
      const response = await getSharedFiles(sharedCurrentPage, sharedPageSize);
      setSharedFiles(response.content);
      setSharedTotalPages(response.totalPages);
    } catch (err) {
      console.error('Error loading shared files:', err);
      // Handle 404 as empty result (no shared files)
      if (err instanceof Error && err.message.includes('404')) {
        setSharedFiles([]);
        setSharedTotalPages(0);
        setSharedError(null);
      } else {
        setSharedError(err instanceof Error ? err.message : 'Failed to load shared files');
      }
    } finally {
      setSharedLoading(false);
    }
  }, [sharedCurrentPage, sharedPageSize]);

  useEffect(() => {
    void loadPersonalFiles();
  }, [loadPersonalFiles]);

  useEffect(() => {
    void loadSharedFiles();
  }, [loadSharedFiles]);

  const handleDownload = async (file: FileMetadata) => {
    try {
      const blob = await downloadFile(file.id);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file.fileName || 'download';
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Error downloading file:', err);
      setError(err instanceof Error ? err.message : 'Failed to download file');
    }
  };

  const handlePreview = async (file: FileMetadata) => {
    try {
      setError(null);

      const baseContentType = (file.contentType ?? '').split(';')[0].trim();
      const isDocx =
        baseContentType ===
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

      if (isDocx) {
        setFileInPreview(file);
        setDocxPreviewVisible(true);
        setPreviewingDocx(true);

        // pulisci eventuale preview precedente
        if (docxPreviewRef.current) docxPreviewRef.current.innerHTML = '';

        const blob = await previewFile(file.id);
        const arrayBuffer = await blob.arrayBuffer();

        if (!docxPreviewRef.current) {
          throw new Error('Preview container non disponibile');
        }

        await renderAsync(arrayBuffer, docxPreviewRef.current, undefined, {
          inWrapper: true,
          breakPages: true
        });

        setPreviewingDocx(false);
        return;
      }

      // default: apri su nuova tab tramite Blob URL
      const blob = await previewFile(file.id);
      const url = window.URL.createObjectURL(blob);
      const previewWindow = window.open(url, '_blank');
      if (!previewWindow) window.location.href = url;
    } catch (err) {
      console.error('Error previewing file:', err);
      setError(err instanceof Error ? err.message : 'Failed to preview file');
      closeDocxPreview();
    }
  };

  const handleDeleteClick = (file: FileMetadata) => {
    setFileToDelete(file);
    setShowDeleteModal(true);
  };

  const handleDeleteConfirm = async () => {
    if (!fileToDelete) return;

    try {
      setDeleting(true);
      await deleteFile(fileToDelete.id);
      setShowDeleteModal(false);
      setFileToDelete(null);
      // Reload files after deletion
      await loadPersonalFiles();
    } catch (err) {
      console.error('Error deleting file:', err);
      setError(err instanceof Error ? err.message : 'Failed to delete file');
      setShowDeleteModal(false);
    } finally {
      setDeleting(false);
    }
  };

  const handleDeleteCancel = () => {
    setShowDeleteModal(false);
    setFileToDelete(null);
  };

  const handleShareClick = (file: FileMetadata) => {
    setFileToShare(file);
    setShowShareModal(true);
  };

  const handleShareSuccess = () => {
    // Optionally show a success message
    console.log('File shared successfully');
  };

  const handleUploadVersionClick = (file: FileMetadata) => {
    setFileToVersion(file);
    setShowUploadVersionModal(true);
  };

  const handleUploadVersionSuccess = () => {
    console.log('New version uploaded successfully');
    // Reload files to show the new version
    loadPersonalFiles();
  };

  const handleViewHistoryClick = (file: FileMetadata) => {
    setFileToViewHistory(file);
    setShowVersionHistoryModal(true);
  };

  const handleGenerateMetadataClick = (file: FileMetadata) => {
    setFileToShowAI(file);
    setShowMetadataAIModal(true);
  };

  const loadAllFiles = async () => {
    try {
      setAllFilesLoading(true);
      setAllFilesError(null);
      console.log('Loading all files from repository...');
      const response = await getAllFiles();
      setAllFiles(response);
      console.log('Loaded', response.length, 'files from repository');
    } catch (err) {
      console.error('Error loading all files:', err);
      // Handle 404 as empty result (no files found)
      if (err instanceof Error && err.message.includes('404')) {
        setAllFiles([]);
        setAllFilesError(null);
      } else {
        setAllFilesError(err instanceof Error ? err.message : 'Failed to load all files');
      }
    } finally {
      setAllFilesLoading(false);
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

  const handleResetFilters = () => {
    setContentTypeFilter('');
    setDateFromFilter('');
    setDateToFilter('');
    setSortField('createdAt');
    setSortDirection('desc');
    setPageSize(10);
    setCurrentPage(0);
  };

  return (
    <Layout>
      <Container className="mt-5">
        <div className="mb-4">
          <h1>Personal Dashboard</h1>
          <p className="text-muted">Welcome, {user?.preferred_username || user?.email}!</p>
        </div>

        {error && (
          <Alert variant="danger" dismissible onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {/* DOCX Preview Overlay */}
        {docxPreviewVisible ? (
          <>
            <div
              onClick={closeDocxPreview}
              style={{
                position: 'fixed',
                inset: 0,
                background: 'rgba(0, 0, 0, 0.55)',
                zIndex: 1050
              }}
            />

            <div
              role="dialog"
              aria-modal="true"
              style={{
                position: 'fixed',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                width: 'min(1100px, calc(100vw - 32px))',
                maxHeight: 'calc(100vh - 32px)',
                zIndex: 1060
              }}
            >
              <div
                className="card"
                onClick={(e) => e.stopPropagation()}
                style={{
                  boxShadow: '0 10px 30px rgba(0,0,0,0.35)'
                }}
              >
                <div className="card-header d-flex justify-content-between align-items-center">
                  <div>
                    <strong>Anteprima DOCX</strong>
                    {fileInPreview ? (
                      <span className="text-muted ms-2">{fileInPreview.fileName}</span>
                    ) : null}
                  </div>
                  <Button variant="outline-secondary" size="sm" onClick={closeDocxPreview}>
                    Close
                  </Button>
                </div>
                <div className="card-body">
                  {previewingDocx ? (
                    <div className="py-2">
                      <Spinner animation="border" role="status" size="sm" className="me-2" />
                      <span>Rendering preview...</span>
                    </div>
                  ) : null}

                  <div
                    ref={docxPreviewRef}
                    style={{
                      maxHeight: 'calc(100vh - 180px)',
                      overflow: 'auto',
                      border: '1px solid #ddd',
                      borderRadius: 6,
                      padding: 10,
                      background: '#fff'
                    }}
                  />
                </div>
              </div>
            </div>
          </>
        ) : null}

        {/* Filters and Sorting */}
        <div className="card mb-3">
          <div className="card-header">
            <h6 className="mb-0">🔍 Filters & Sorting</h6>
          </div>
          <div className="card-body">
            <Form>
              <Row className="g-3">
                <Col md={3}>
                  <Form.Group>
                    <Form.Label>Content Type</Form.Label>
                    <Form.Control
                      type="text"
                      placeholder="e.g., image/png"
                      value={contentTypeFilter}
                      onChange={(e) => {
                        setContentTypeFilter(e.target.value);
                        setCurrentPage(0);
                      }}
                    />
                  </Form.Group>
                </Col>
                <Col md={3}>
                  <Form.Group>
                    <Form.Label>Date From</Form.Label>
                    <Form.Control
                      type="date"
                      value={dateFromFilter}
                      onChange={(e) => {
                        setDateFromFilter(e.target.value);
                        setCurrentPage(0);
                      }}
                    />
                  </Form.Group>
                </Col>
                <Col md={3}>
                  <Form.Group>
                    <Form.Label>Date To</Form.Label>
                    <Form.Control
                      type="date"
                      value={dateToFilter}
                      onChange={(e) => {
                        setDateToFilter(e.target.value);
                        setCurrentPage(0);
                      }}
                    />
                  </Form.Group>
                </Col>
                <Col md={3}>
                  <Form.Group>
                    <Form.Label>Page Size</Form.Label>
                    <Form.Select
                      value={pageSize}
                      onChange={(e) => {
                        setPageSize(Number(e.target.value));
                        setCurrentPage(0);
                      }}
                    >
                      <option value={5}>5</option>
                      <option value={10}>10</option>
                      <option value={25}>25</option>
                      <option value={50}>50</option>
                    </Form.Select>
                  </Form.Group>
                </Col>
              </Row>
              <Row className="g-3 mt-2">
                <Col md={4}>
                  <Form.Group>
                    <Form.Label>Sort By</Form.Label>
                    <Form.Select
                      value={sortField}
                      onChange={(e) => {
                        setSortField(e.target.value);
                        setCurrentPage(0);
                      }}
                    >
                      <option value="createdAt">Upload Date</option>
                      <option value="lastUpdateAt">Last Modified</option>
                      <option value="lastFilename">File Name</option>
                      <option value="currentVersion">Version</option>
                    </Form.Select>
                  </Form.Group>
                </Col>
                <Col md={4}>
                  <Form.Group>
                    <Form.Label>Order</Form.Label>
                    <Form.Select
                      value={sortDirection}
                      onChange={(e) => {
                        setSortDirection(e.target.value as 'asc' | 'desc');
                        setCurrentPage(0);
                      }}
                    >
                      <option value="desc">Descending</option>
                      <option value="asc">Ascending</option>
                    </Form.Select>
                  </Form.Group>
                </Col>
                <Col md={4} className="d-flex align-items-end">
                  <Button
                    variant="outline-secondary"
                    onClick={handleResetFilters}
                    className="w-100"
                  >
                    🔄 Reset Filters
                  </Button>
                </Col>
              </Row>
            </Form>
          </div>
        </div>

        <div className="card">
          <div className="card-header d-flex justify-content-between align-items-center">
            <h5 className="mb-0">My Personal Files</h5>
            <Button 
              variant="outline-primary" 
              size="sm"
              onClick={loadPersonalFiles}
              disabled={loading}
            >
              {loading ? <Spinner animation="border" size="sm" /> : '🔄 Refresh'}
            </Button>
          </div>
          <div className="card-body">
            {loading && files.length === 0 ? (
              <div className="text-center py-5">
                <Spinner animation="border" role="status">
                  <span className="visually-hidden">Loading...</span>
                </Spinner>
                <p className="mt-3">Loading your files...</p>
              </div>
            ) : files.length === 0 ? (
              <div className="text-center py-5">
                <p className="text-muted">No personal files found. Upload your first file!</p>
              </div>
            ) : (
              <>
                <Table responsive hover>
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Type</th>
                      <th>Size</th>
                      <th>Uploaded</th>
                      <th>Version</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {files.map((file) => (
                      <tr key={file.id}>
                        <td>{file.fileName}</td>
                        <td>
                          <Badge bg="secondary">{file.contentType || 'Unknown'}</Badge>
                        </td>
                        <td>{formatFileSize(file.size || 0)}</td>
                        <td>{formatDate(file.createdAt)}</td>
                        <td>
                          <Badge bg="info">v{file.version || 1}</Badge>
                        </td>
                        <td>
                          <Button
                            variant="outline-primary"
                            size="sm"
                            className="me-2"
                            onClick={() => handleDownload(file)}
                          >
                            📥 Download
                          </Button>
                            <Button
                                variant="outline-secondary"
                                size="sm"
                                className="me-2"
                                onClick={() => handlePreview(file)}
                            >
                                👁️ Preview
                            </Button>
                          <Button
                            variant="outline-secondary"
                            size="sm"
                            className="me-2"
                            onClick={() => handleViewHistoryClick(file)}
                          >
                            📜 History
                          </Button>
                          <Button
                            variant="outline-info"
                            size="sm"
                            className="me-2"
                            onClick={() => handleUploadVersionClick(file)}
                          >
                            📤 New Version
                          </Button>
                          <Button
                            variant="outline-success"
                            size="sm"
                            className="me-2"
                            onClick={() => handleShareClick(file)}
                          >
                            🔗 Share
                          </Button>
                          <Button
                            variant="outline-warning"
                            size="sm"
                            className="me-2"
                            onClick={() => handleGenerateMetadataClick(file)}
                          >
                            🤖 Get Metadata
                          </Button>
                          <Button
                            variant="outline-danger"
                            size="sm"
                            onClick={() => handleDeleteClick(file)}
                          >
                            🗑️ Delete
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>

                {totalPages > 1 && (
                  <div className="d-flex justify-content-center mt-3">
                    <Button
                      variant="outline-secondary"
                      size="sm"
                      disabled={currentPage === 0}
                      onClick={() => setCurrentPage(currentPage - 1)}
                      className="me-2"
                    >
                      Previous
                    </Button>
                    <span className="align-self-center mx-3">
                      Page {currentPage + 1} of {totalPages}
                    </span>
                    <Button
                      variant="outline-secondary"
                      size="sm"
                      disabled={currentPage >= totalPages - 1}
                      onClick={() => setCurrentPage(currentPage + 1)}
                    >
                      Next
                    </Button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>

        {/* Shared Files Section */}
        <div className="card mt-4">
          <div className="card-header d-flex justify-content-between align-items-center">
            <h5 className="mb-0">Files Shared With Me</h5>
            <div className="d-flex gap-2">
              <Form.Select
                size="sm"
                value={sharedPageSize}
                onChange={(e) => {
                  setSharedPageSize(Number(e.target.value));
                  setSharedCurrentPage(0);
                }}
                style={{ width: 'auto' }}
              >
                <option value={5}>5 per page</option>
                <option value={10}>10 per page</option>
                <option value={25}>25 per page</option>
              </Form.Select>
              <Button 
                variant="outline-primary" 
                size="sm"
                onClick={loadSharedFiles}
                disabled={sharedLoading}
              >
                {sharedLoading ? <Spinner animation="border" size="sm" /> : '🔄 Refresh'}
              </Button>
            </div>
          </div>
          <div className="card-body">
            {sharedError && (
              <Alert variant="danger" dismissible onClose={() => setSharedError(null)}>
                {sharedError}
              </Alert>
            )}

            {sharedLoading && sharedFiles.length === 0 ? (
              <div className="text-center py-5">
                <Spinner animation="border" role="status">
                  <span className="visually-hidden">Loading...</span>
                </Spinner>
                <p className="mt-3">Loading shared files...</p>
              </div>
            ) : sharedFiles.length === 0 ? (
              <div className="text-center py-5">
                <p className="text-muted">No files have been shared with you yet.</p>
              </div>
            ) : (
              <>
                <Table responsive hover>
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Type</th>
                      <th>Size</th>
                      <th>Owner</th>
                      <th>Scope</th>
                      <th>Shared Date</th>
                      <th>Version</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sharedFiles.map((file) => (
                      <tr key={file.id}>
                        <td>{file.fileName}</td>
                        <td>
                          <Badge bg="secondary">{file.contentType || 'Unknown'}</Badge>
                        </td>
                        <td>{formatFileSize(file.size || 0)}</td>
                        <td>
                          <Badge bg="info">{file.ownerName || 'Unknown'}</Badge>
                        </td>
                        <td>
                          <Badge bg={file.sharingScope === SharingScope.PERSONAL ? 'primary' : file.sharingScope === SharingScope.DEPARTMENT ? 'warning' : 'success'}>
                            {file.sharingScope}
                          </Badge>
                        </td>
                        <td>{formatDate(file.createdAt)}</td>
                        <td>
                          <Badge bg="info">v{file.version || 1}</Badge>
                        </td>
                        <td>
                          <Button
                            variant="outline-primary"
                            size="sm"
                            className="me-2"
                            onClick={() => handleDownload(file)}
                          >
                            📥 Download
                          </Button>
                            <Button
                                variant="outline-secondary"
                                size="sm"
                                className="me-2"
                                onClick={() => handlePreview(file)}
                            >
                                👁️ Preview
                            </Button>
                          <Button
                            variant="outline-warning"
                            size="sm"
                            className="me-2"
                            onClick={() => handleGenerateMetadataClick(file)}
                          >
                            🤖 Get Metadata
                          </Button>
                          <Button
                            variant="outline-success"
                            size="sm"
                            onClick={() => handleShareClick(file)}
                          >
                            🔗 Share
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>

                {sharedTotalPages > 1 && (
                  <div className="d-flex justify-content-center mt-3">
                    <Button
                      variant="outline-secondary"
                      size="sm"
                      disabled={sharedCurrentPage === 0}
                      onClick={() => setSharedCurrentPage(sharedCurrentPage - 1)}
                      className="me-2"
                    >
                      Previous
                    </Button>
                    <span className="align-self-center mx-3">
                      Page {sharedCurrentPage + 1} of {sharedTotalPages}
                    </span>
                    <Button
                      variant="outline-secondary"
                      size="sm"
                      disabled={sharedCurrentPage >= sharedTotalPages - 1}
                      onClick={() => setSharedCurrentPage(sharedCurrentPage + 1)}
                    >
                      Next
                    </Button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>

        {/* All Files Section */}
        <div className="card mt-4">
          <div className="card-header d-flex justify-content-between align-items-center">
            <h5 className="mb-0">📂 All Files in Repository</h5>
            <Button
              variant="primary"
              size="sm"
              onClick={loadAllFiles}
              disabled={allFilesLoading}
            >
              {allFilesLoading ? <Spinner animation="border" size="sm" /> : '📥 Load All Files'}
            </Button>
          </div>
          <div className="card-body">
            {allFilesError && (
              <Alert variant="danger" dismissible onClose={() => setAllFilesError(null)}>
                {allFilesError}
              </Alert>
            )}

            {allFilesLoading ? (
              <div className="text-center py-5">
                <Spinner animation="border" role="status">
                  <span className="visually-hidden">Loading...</span>
                </Spinner>
                <p className="mt-3">Loading all files from repository...</p>
              </div>
            ) : allFiles.length === 0 ? (
              <div className="text-center py-5">
                <p className="text-muted">Click "Load All Files" to fetch all files from the repository.</p>
              </div>
            ) : (
              <>
                <Alert variant="info">
                  <strong>📊 Total Files:</strong> {allFiles.length} file(s) found in repository
                </Alert>
                <Table responsive hover>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>File Name</th>
                      <th>Created At</th>
                      <th>Completed At</th>
                      <th>Version</th>
                    </tr>
                  </thead>
                  <tbody>
                    {allFiles.map((file) => (
                      <tr key={file.id}>
                        <td>
                          <code style={{ fontSize: '0.85em' }}>{file.id}</code>
                        </td>
                        <td>
                          <strong>{file.fileName || 'N/A'}</strong>
                        </td>
                        <td>{formatDate(file.createdAt)}</td>
                        <td>{file.completedAt ? formatDate(file.completedAt) : <Badge bg="warning">Pending</Badge>}</td>
                        <td>
                          <Badge bg="info">v{file.version || 1}</Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </>
            )}
          </div>
        </div>
      </Container>

      {/* Delete Confirmation Modal */}
      <Modal show={showDeleteModal} onHide={handleDeleteCancel} centered>
        <Modal.Header closeButton>
          <Modal.Title>Confirm Delete</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {fileToDelete && (
            <>
              <p>Are you sure you want to delete this file?</p>
              <div className="alert alert-warning">
                <strong>{fileToDelete.fileName}</strong>
                <br />
                <small className="text-muted">
                  Type: {fileToDelete.contentType || 'Unknown'} | Size: {formatFileSize(fileToDelete.size || 0)}
                </small>
              </div>
              <p className="text-danger mb-0">
                <strong>Warning:</strong> This action cannot be undone.
              </p>
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={handleDeleteCancel} disabled={deleting}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleDeleteConfirm} disabled={deleting}>
            {deleting ? (
              <>
                <Spinner animation="border" size="sm" className="me-2" />
                Deleting...
              </>
            ) : (
              'Delete File'
            )}
          </Button>
        </Modal.Footer>
      </Modal>

      {/* Share File Modal */}
      <ShareFileModal
        show={showShareModal}
        onHide={() => setShowShareModal(false)}
        onSuccess={handleShareSuccess}
        file={fileToShare}
      />

      {/* Upload Version Modal */}
      <UploadVersionModal
        show={showUploadVersionModal}
        onHide={() => setShowUploadVersionModal(false)}
        onSuccess={handleUploadVersionSuccess}
        file={fileToVersion}
      />

      {/* Version History Modal */}
      <VersionHistoryModal
        show={showVersionHistoryModal}
        onHide={() => setShowVersionHistoryModal(false)}
        file={fileToViewHistory}
      />

      {/* AI Metadata Modal */}
      <MetadataAIModal
        show={showMetadataAIModal}
        onHide={() => setShowMetadataAIModal(false)}
        fileId={fileToShowAI?.id || ''}
        fileName={fileToShowAI?.fileName || ''}
        version={fileToShowAI?.version}
      />
    </Layout>
  );
};
