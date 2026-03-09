import React from 'react';
import { Container, Button, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

export const Unauthorized: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Container className="d-flex justify-content-center align-items-center vh-100">
      <div style={{ maxWidth: '500px' }}>
        <Alert variant="danger">
          <Alert.Heading>Access Denied</Alert.Heading>
          <p>
            You do not have the required permissions to access this page.
            Please contact your administrator if you believe this is an error.
          </p>
          <hr />
          <div className="d-flex justify-content-end">
            <Button onClick={() => navigate('/')} variant="outline-danger">
              Go to Home
            </Button>
          </div>
        </Alert>
      </div>
    </Container>
  );
};
