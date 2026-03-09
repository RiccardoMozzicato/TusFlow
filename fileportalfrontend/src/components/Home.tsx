import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Container, Row, Col, Card, Button, Badge } from 'react-bootstrap';
import { Layout } from './Layout';
import { UploadFileModal } from './UploadFileModal';
import { ChatBox } from './ChatBox';
import { roleColors } from "../utils/utils.ts";

export const Home: React.FC = () => {
  const { user, hasRole, isLoading } = useAuth();
  const navigate = useNavigate();
  const [showUploadModal, setShowUploadModal] = useState(false);

  const formatBytes = (bytes?: number) => {
    if (!bytes) return 'N/A';
    const size = ["B", "KB", "MB", "GB", "TB"];
    let index = 0;
    while(bytes >= 1024 && index < size.length-1) {
        bytes /= 1024;
        if(index < size.length-1) index++;
    }
    bytes = Math.floor(bytes * 100) / 100;
    return `${bytes}${size[index]}`
  };

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <Layout>
      <Container className="mt-5">
        <h1 className="mb-4">Welcome to File Portal</h1>
        <Row>
          <Col md={6}>
            <Card>
              <Card.Header>
                <h5>User Information</h5>
              </Card.Header>
              <Card.Body>
                <div className="mb-3">
                  <strong>Username:</strong>
                  <p className="mb-2">{user?.username || user?.preferred_username || 'N/A'}</p>
                </div>
                <div className="mb-3">
                  <strong>Email:</strong>
                  <p className="mb-2">{user?.email || 'N/A'}</p>
                </div>
                <div className="mb-3">
                  <strong>Full Name:</strong>
                  <p className="mb-2">{user?.fullName || 'N/A'}</p>
                </div>
                <div className="mb-3">
                  <strong>Department:</strong>
                  <p className="mb-2">{user?.department || 'N/A'}</p>
                </div>
                <div className="mb-3">
                  <strong>Organization:</strong>
                  <p className="mb-2">{user?.organization || 'N/A'}</p>
                </div>
                <div className="mb-3">
                  <strong>Roles:</strong>
                  <div className="mt-2">
                    {user?.roles.map((role, index) => (
                      <Badge
                          key={index}
                          bg={roleColors(role)}
                          className="me-2">
                        {role}
                      </Badge>
                    ))}
                  </div>
                </div>
                <div className="mb-3">
                  <strong>Storage Quota:</strong>
                  {user?.storageQuota ? (
                    <p className="mb-2">{formatBytes(user.storageQuota)}</p>
                  ) : (
                    <p className="mb-2">N/A</p>
                  )}
                </div>
              </Card.Body>
            </Card>
          </Col>
          <Col md={6}>
            <Card>
              <Card.Header>
                <h5>Dashboards</h5>
              </Card.Header>
              <Card.Body>
                <div className="d-grid gap-3">
                  {hasRole('admin') && (
                    <Button
                      variant={roleColors('admin')}
                      size="lg"
                      onClick={() => navigate('/admin')}
                    >
                      Admin Dashboard
                    </Button>
                  )}
                  {hasRole('manager') && (
                    <Button
                      variant={roleColors('manager')}
                      size="lg"
                      onClick={() => navigate('/manager')}
                    >
                      Manager Dashboard
                    </Button>
                  )}
                  {hasRole('staff') && (
                    <Button
                      variant={roleColors('staff')}
                      size="lg"
                      onClick={() => navigate('/staff')}
                    >
                      Personal Dashboard
                    </Button>
                  )}
                </div>
              </Card.Body>
            </Card>

            <Card className="mt-3">
              <Card.Header>
                <h5>Quick Actions</h5>
              </Card.Header>
              <Card.Body>
                <div className="d-grid gap-2">
                  <Button
                    variant="outline-primary"
                    size="lg"
                    onClick={() => setShowUploadModal(true)}
                  >
                    📁 Upload File (TUS)
                  </Button>
                </div>
              </Card.Body>
            </Card>
          </Col>
        </Row>

        {/* TUS Upload Modal */}
        <UploadFileModal
          show={showUploadModal}
          onHide={() => setShowUploadModal(false)}
          onSuccess={() => {
            console.log('File uploaded successfully with TUS!');
          }}
        />

        {/* AI Chat Assistant */}
        <ChatBox />

      </Container>
    </Layout>
  );
};
