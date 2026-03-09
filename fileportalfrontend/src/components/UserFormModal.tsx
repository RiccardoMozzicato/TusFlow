import React, { useState, useEffect } from 'react';
import { Modal, Form, Button, Alert } from 'react-bootstrap';
import type { User, UserCreate } from '../services/userApi';

interface UserFormModalProps {
  show: boolean;
  user?: User;
  onHide: () => void;
  onSave: (user: User | UserCreate) => Promise<void>;
}

const emptyUserCreate: UserCreate = {
  id: '',
  username: '',
  email: '',
  organization: '',
  department: '',
  role: 'staff',
  storageQuota: 1073741824, // 1 GB default
};

export const UserFormModal: React.FC<UserFormModalProps> = ({ 
  show, 
  user, 
  onHide, 
  onSave 
}) => {
  const isEditMode = !!user?.id;
  const [formData, setFormData] = useState<User | UserCreate>(user || emptyUserCreate);
  const [error, setError] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [usernameError, setUsernameError] = useState<string>('');

  useEffect(() => {
    setFormData(user || emptyUserCreate);
    setUsernameError('');
  }, [user]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;

    // Validazione speciale per username: rimuovi spazi automaticamente
    if (name === 'username') {
      const cleanValue = value.replace(/\s/g, ''); // Rimuove tutti gli spazi
      if (value !== cleanValue) {
        setUsernameError('Gli spazi non sono ammessi nello username');
        setTimeout(() => setUsernameError(''), 3000); // Rimuovi il messaggio dopo 3 secondi
      } else {
        setUsernameError('');
      }
      setFormData(prev => ({
        ...prev,
        [name]: cleanValue,
      } as User | UserCreate));
      return;
    }

    setFormData(prev => ({
      ...prev,
      [name]: name === 'storageQuota' ? parseInt(value) : value,
    } as User | UserCreate));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await onSave(formData);
      onHide();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal show={show} onHide={onHide} size="lg">
      <Modal.Header closeButton>
        <Modal.Title>{isEditMode ? 'Edit User' : 'Create New User'}</Modal.Title>
      </Modal.Header>
      <Form onSubmit={handleSubmit}>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          
          {isEditMode ? (
            <>
              <Form.Group className="mb-3">
                <Form.Label>Full Name *</Form.Label>
                <Form.Control
                  type="text"
                  name="fullName"
                  value={'fullName' in formData ? formData.fullName : ''}
                  onChange={handleChange}
                  required
                />
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Email *</Form.Label>
                <Form.Control
                  type="email"
                  name="email"
                  value={'email' in formData ? formData.email : ''}
                  onChange={handleChange}
                  required
                />
              </Form.Group>
            </>
          ) : (
            <>
              <Form.Group className="mb-3">
                <Form.Label>Username *</Form.Label>
                <Form.Control
                  type="text"
                  name="username"
                  value={'username' in formData ? formData.username : ''}
                  onChange={handleChange}
                  pattern="^\S+$"
                  isInvalid={!!usernameError}
                  required
                />
                <Form.Control.Feedback type="invalid">
                  {usernameError || 'Lo username non può contenere spazi'}
                </Form.Control.Feedback>
                <Form.Text className="text-muted">
                  Lo username non può contenere spazi
                </Form.Text>
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Email *</Form.Label>
                <Form.Control
                  type="email"
                  name="email"
                  value={'email' in formData ? formData.email : ''}
                  onChange={handleChange}
                  required
                />
              </Form.Group>
            </>
          )}

          <Form.Group className="mb-3">
            <Form.Label>Organization *</Form.Label>
            <Form.Control
              type="text"
              name="organization"
              value={formData.organization}
              onChange={handleChange}
              required
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Department</Form.Label>
            <Form.Control
              type="text"
              name="department"
              value={formData.department || ''}
              onChange={handleChange}
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Role *</Form.Label>
            <Form.Select
              name="role"
              value={formData.role}
              onChange={handleChange}
              required
            >
              <option value="staff">Staff</option>
              <option value="manager">Manager</option>
              <option value="admin">Admin</option>
            </Form.Select>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Storage Quota (bytes)</Form.Label>
            <Form.Control
              type="number"
              name="storageQuota"
              value={formData.storageQuota || ''}
              onChange={handleChange}
            />
            <Form.Text className="text-muted">
              Default: 1073741824 bytes (1 GB)
            </Form.Text>
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={onHide} disabled={loading}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" disabled={loading}>
            {loading ? 'Saving...' : 'Save'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
};
