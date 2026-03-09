import React, { useEffect, useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { Container, Table, Button, Alert, Spinner, Badge, Form, Row, Col } from 'react-bootstrap';
import { Layout } from './Layout';
import { getFilesMetadata, downloadFile, SharingScope } from '../services/fileApi';
import type { FileMetadata } from '../services/fileApi';
import { ManageFileModal } from './ManageFileModal';
import { UploadVersionModal } from './UploadVersionModal';
import { VersionHistoryModal } from './VersionHistoryModal';
import { MetadataAIModal } from './MetadataAIModal';

export const ManagerDashboard: React.FC = () => {
  const { user } = useAuth();
  const [files, setFiles] = useState<FileMetadata[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  
  // Filters
  const [contentTypeFilter, setContentTypeFilter] = useState('');
  const [dateFromFilter, setDateFromFilter] = useState('');
  const [dateToFilter, setDateToFilter] = useState('');
  const [sortField, setSortField] = useState('createdAt');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
  const [pageSize, setPageSize] = useState(10);

  // Manage file modal state
  const [showManageModal, setShowManageModal] = useState(false);
  const [fileToManage, setFileToManage] = useState<FileMetadata | null>(null);

  // Upload version modal state
  const [showUploadVersionModal, setShowUploadVersionModal] = useState(false);
  const [fileToVersion, setFileToVersion] = useState<FileMetadata | null>(null);

  // Version history modal state
  const [showVersionHistoryModal, setShowVersionHistoryModal] = useState(false);
  const [fileToViewHistory, setFileToViewHistory] = useState<FileMetadata | null>(null);

  // AI Metadata modal state
  const [showMetadataAIModal, setShowMetadataAIModal] = useState(false);
  const [fileToShowAI, setFileToShowAI] = useState<FileMetadata | null>(null);

  useEffect(() => {
    loadDepartmentalFiles();
  }, [currentPage, contentTypeFilter, dateFromFilter, dateToFilter, sortField, sortDirection, pageSize]);

  const loadDepartmentalFiles = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await getFilesMetadata({
        sharingScope: SharingScope.DEPARTMENT,
        page: currentPage,
        size: pageSize,
        sort: `${sortField},${sortDirection}`,
        contentType: contentTypeFilter || undefined,
        dateFrom: dateFromFilter ? `${dateFromFilter}T00:00:00Z` : undefined,
        dateTo: dateToFilter ? `${dateToFilter}T23:59:59Z` : undefined,
      });
      setFiles(response.content);
      setTotalPages(response.totalPages);
    } catch (err) {
      console.error('Error loading departmental files:', err);
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
  };

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

  const handleManageClick = (file: FileMetadata) => {
    setFileToManage(file);
    setShowManageModal(true);
  };

  const handleManageSuccess = () => {
    loadDepartmentalFiles();
  };

  const handleUploadVersionClick = (file: FileMetadata) => {
    setFileToVersion(file);
    setShowUploadVersionModal(true);
  };

  const handleUploadVersionSuccess = () => {
    console.log('New version uploaded successfully');
    loadDepartmentalFiles();
  };

  const handleViewHistoryClick = (file: FileMetadata) => {
    setFileToViewHistory(file);
    setShowVersionHistoryModal(true);
  };

  const handleGenerateMetadataClick = (file: FileMetadata) => {
    setFileToShowAI(file);
    setShowMetadataAIModal(true);
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
          <h1>Manager Dashboard</h1>
          <p className="text-muted">Welcome, {user?.preferred_username || user?.email}!</p>
          <p className="text-muted">Department: <strong>{user?.department || 'N/A'}</strong></p>
        </div>

        {error && (
          <Alert variant="danger" dismissible onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

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
            <h5 className="mb-0">Departmental Files</h5>
            <Button 
              variant="outline-primary" 
              size="sm"
              onClick={loadDepartmentalFiles}
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
                <p className="mt-3">Loading departmental files...</p>
              </div>
            ) : files.length === 0 ? (
              <div className="text-center py-5">
                <p className="text-muted">No departmental files found. Upload files with department sharing scope!</p>
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
                        <td>
                          <Badge bg="info">{file.ownerName || 'Unknown'}</Badge>
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
                            onClick={() => handleGenerateMetadataClick(file)}
                          >
                            🤖 Get Metadata
                          </Button>
                          <Button
                            variant="outline-warning"
                            size="sm"
                            onClick={() => handleManageClick(file)}
                          >
                            ⚙️ Manage
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
      </Container>

      {/* Manage File Modal */}
      <ManageFileModal
        show={showManageModal}
        onHide={() => setShowManageModal(false)}
        onSuccess={handleManageSuccess}
        file={fileToManage}
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
