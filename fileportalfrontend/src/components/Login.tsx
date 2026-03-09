import React from 'react';
import { useAuth } from '../hooks/useAuth';
import { Container, Button, Card } from 'react-bootstrap';
import { Navigate } from 'react-router-dom';

export const Login: React.FC = () => {
  const { isAuthenticated, login, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <Container className="d-flex justify-content-center align-items-center vh-100">
      <Card style={{ width: '400px' }}>
        <Card.Body className="text-center p-5">
          <h2 className="mb-4">File Portal</h2>
          <p className="text-muted mb-4">
            Please login to access the file management system
          </p>
          <Button 
            variant="primary" 
            size="lg" 
            onClick={login}
            className="w-100"
          >
            Login with Keycloak
          </Button>
        </Card.Body>
      </Card>
    </Container>
  );
};
