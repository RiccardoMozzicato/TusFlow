import React, { useState, useEffect } from 'react';
import { Modal, Form, Button, Alert, Spinner, ListGroup } from 'react-bootstrap';
import { shareFile, PermissionLevel } from '../services/fileApi';
import type { FileMetadata } from '../services/fileApi';

interface User {
  id: string;
  username?: string;
  email: string;
  fullName?: string;
  firstName?: string;
  lastName?: string;
}

interface ShareFileModalProps {
  show: boolean;
  onHide: () => void;
  onSuccess: () => void;
  file: FileMetadata | null;
}

export const ShareFileModal: React.FC<ShareFileModalProps> = ({ show, onHide, onSuccess, file }) => {
  const [userId, setUserId] = useState('');
  const [permission, setPermission] = useState<PermissionLevel>(PermissionLevel.READ_ONLY);
  const [sharing, setSharing] = useState(false);
  const [error, setError] = useState<string>('');
  const [users, setUsers] = useState<User[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [showUserList, setShowUserList] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    if (show) {
      loadUsers();
    }
  }, [show]);

  const loadUsers = async () => {
    setLoadingUsers(true);
    try {
      // Load all users by requesting a large page size
      const response = await fetch('/api/v1/users?page=0&size=1000', {
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
        }
      });

      if (!response.ok) {
        throw new Error(`Failed to load users: ${response.statusText}`);
      }

      const data = await response.json();
      // Backend returns a Page object with content array
      setUsers(data.content || []);
    } catch (err) {
      console.error('Error loading users:', err);
      setError(err instanceof Error ? err.message : 'Failed to load users');
    } finally {
      setLoadingUsers(false);
    }
  };

  const handleUserSelect = (user: User) => {
    setUserId(user.id);
    const displayName = user.fullName || user.username || user.email;
    setSearchTerm(`${displayName} (${user.email})`);
    setShowUserList(false);
    setError(''); // Clear any previous errors
  };

  const filteredUsers = users.filter(user => {
    if (!searchTerm) return true;
    const search = searchTerm.toLowerCase();
    return (
      (user.username && user.username.toLowerCase().includes(search)) ||
      (user.email && user.email.toLowerCase().includes(search)) ||
      (user.fullName && user.fullName.toLowerCase().includes(search)) ||
      (user.firstName && user.firstName.toLowerCase().includes(search)) ||
      (user.lastName && user.lastName.toLowerCase().includes(search))
    );
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!file) {
      setError('No file selected');
      return;
    }

    if (!userId || !userId.trim()) {
      setError('Please select a user from the list');
      return;
    }

    // Validate UUID format
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(userId.trim())) {
      setError('Invalid user selection. Please select a user from the list.');
      return;
    }

    setSharing(true);
    setError('');

    try {
      await shareFile(file.id, userId.trim(), permission);
      
      // Reset form
      setUserId('');
      setSearchTerm('');
      setPermission(PermissionLevel.READ_ONLY);
      setSharing(false);
      setShowUserList(false);
      onSuccess();
      onHide();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to share file');
      setSharing(false);
    }
  };

  const handleClose = () => {
    if (!sharing) {
      setUserId('');
      setSearchTerm('');
      setPermission(PermissionLevel.READ_ONLY);
      setError('');
      setShowUserList(false);
      onHide();
    }
  };

  return (
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton={!sharing}>
        <Modal.Title>Share File</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="danger" onClose={() => setError('')} dismissible>
            {error}
          </Alert>
        )}

        {file && (
          <div className="alert alert-info mb-3">
            <strong>File:</strong> {file.fileName}
            <br />
            <small className="text-muted">
              Type: {file.contentType || 'Unknown'} | Size: {formatFileSize(file.size || 0)}
            </small>
          </div>
        )}

        <Form onSubmit={handleSubmit}>
          <Form.Group className="mb-3">
            <Form.Label>Select User</Form.Label>
            {loadingUsers ? (
              <div className="text-center py-2">
                <Spinner animation="border" size="sm" />
                <span className="ms-2">Loading users...</span>
              </div>
            ) : (
              <>
                <Form.Control
                  type="text"
                  placeholder="Search users by name, username or email..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value);
                    setShowUserList(true);
                    // Clear userId when user starts typing again
                    if (userId) {
                      setUserId('');
                    }
                  }}
                  onFocus={() => setShowUserList(true)}
                  onBlur={() => {
                    // Delay to allow click on list item
                    setTimeout(() => setShowUserList(false), 200);
                  }}
                  disabled={sharing}
                  autoComplete="off"
                  className={userId ? 'is-valid' : ''}
                />
                {showUserList && searchTerm && (
                  <ListGroup className="mt-1 position-absolute" style={{ zIndex: 1000, maxHeight: '200px', overflowY: 'auto', width: 'calc(100% - 30px)' }}>
                    {filteredUsers.length > 0 ? (
                      filteredUsers.slice(0, 10).map((user) => (
                        <ListGroup.Item
                          key={user.id}
                          action
                          onClick={() => handleUserSelect(user)}
                          style={{ cursor: 'pointer' }}
                        >
                          <div>
                            <strong>{user.username || user.email}</strong>
                            {user.fullName && (
                              <span className="text-muted ms-2">
                                ({user.fullName})
                              </span>
                            )}
                            {!user.fullName && (user.firstName || user.lastName) && (
                              <span className="text-muted ms-2">
                                ({user.firstName} {user.lastName})
                              </span>
                            )}
                          </div>
                          <small className="text-muted">{user.email}</small>
                        </ListGroup.Item>
                      ))
                    ) : (
                      <ListGroup.Item disabled>
                        <span className="text-muted">No users found matching "{searchTerm}"</span>
                      </ListGroup.Item>
                    )}
                  </ListGroup>
                )}
                {userId && (
                  <Form.Text className="text-muted d-block mt-1">
                    Selected User ID: <code>{userId}</code>
                  </Form.Text>
                )}
              </>
            )}
            <Form.Text className="text-muted">
              Search and select the user you want to share this file with
            </Form.Text>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Permission Level</Form.Label>
            <Form.Select
              value={permission}
              onChange={(e) => setPermission(e.target.value as PermissionLevel)}
              disabled={sharing}
              required
            >
              <option value={PermissionLevel.READ_ONLY}>Read Only - User can view and download</option>
              <option value={PermissionLevel.READ_WRITE}>Read Write - User can view, download, and modify</option>
            </Form.Select>
            <Form.Text className="text-muted">
              Choose what the user can do with this file
            </Form.Text>
          </Form.Group>

          <div className="d-flex justify-content-end gap-2">
            <Button variant="secondary" onClick={handleClose} disabled={sharing}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={sharing || !userId.trim()}>
              {sharing ? (
                <>
                  <Spinner animation="border" size="sm" className="me-2" />
                  Sharing...
                </>
              ) : (
                'Share File'
              )}
            </Button>
          </div>
        </Form>
      </Modal.Body>
    </Modal>
  );
};

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}
