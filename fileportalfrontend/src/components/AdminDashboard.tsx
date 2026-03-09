import React, { useState, useEffect } from 'react';
import { Container, Table, Button, Alert, Spinner, Pagination, Badge } from 'react-bootstrap';
import { Layout } from './Layout';
import { UserFormModal } from './UserFormModal';
import { getUsers, updateUser, deleteUser, createUser } from '../services/userApi';
import type { User, UserCreate, PageResponse } from '../services/userApi';
import { roleColors } from "../utils/utils.ts";
import { useAuth } from '../hooks/useAuth';

export const AdminDashboard: React.FC = () => {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');
  const [showModal, setShowModal] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | undefined>();
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 10;

  const fetchUsers = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response: PageResponse<User> = await getUsers(page, pageSize);
      setUsers(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch users');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleEdit = (user: User) => {
    setSelectedUser(user);
    setShowModal(true);
  };

  const handleCreate = () => {
    setSelectedUser(undefined);
    setShowModal(true);
  };

  const handleSave = async (user: User | UserCreate) => {
    try {
      if (selectedUser) {
        await updateUser(selectedUser.id, user as User);
      } else {
        await createUser(user as UserCreate);
      }
      setShowModal(false);
      await fetchUsers(); // Ricarica la lista dopo l'aggiornamento
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save user');
    }
  };

  const handleDelete = async (userId: string) => {
    if (!window.confirm('Are you sure you want to delete this user?')) {
      return;
    }
    try {
      await deleteUser(userId);
      await fetchUsers(); // Ricarica la lista dopo la cancellazione
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete user');
    }
  };

  const getRoleBadgeVariant = (role?: string) => {
    switch (role) {
      case 'admin':
        return 'danger';
      case 'manager':
        return 'warning';
      case 'staff':
        return 'info';
      default:
        return 'secondary';
    }
  };

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

  return (
    <Layout>
      <Container className="mt-4">
        <div className="mb-4">
          <h1>User Management</h1>
          <p className="text-muted">
            Total Users: {totalElements}
          </p>
        </div>

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
          </div>
        ) : (
          <>
            <Table striped bordered hover responsive>
              <thead>
                <tr>
                  <th>Full Name</th>
                  <th>Email</th>
                  <th>Organization</th>
                  <th>Department</th>
                  <th>Role</th>
                  <th>Storage Quota</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center">
                      No users found
                    </td>
                  </tr>
                ) : (
                  users.map((user) => (
                    <tr key={user.id}>
                      <td>{user.fullName}</td>
                      <td>{user.email}</td>
                      <td>{user.organization}</td>
                      <td>{user.department || '-'}</td>
                      <td>
                        <Badge bg={user.role && roleColors(user.role) || getRoleBadgeVariant(user.role)}>
                          {user.role ? user.role.toUpperCase() : 'STAFF'}
                        </Badge>
                      </td>
                      <td>{formatBytes(user.storageQuota)}</td>
                      <td>
                        <Button
                          variant="outline-primary"
                          size="sm"
                          className="me-2"
                          onClick={() => handleEdit(user)}
                        >
                          Edit
                        </Button>
                        <Button
                          variant="outline-danger"
                          size="sm"
                          onClick={() => handleDelete(user.id)}
                          disabled={user.email === currentUser?.email || user.id === currentUser?.sub}
                        >
                          Delete
                        </Button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </Table>

            <div className="mt-3">
              <Button
                variant="success"
                size="sm"
                onClick={handleCreate}
              >
                + Create User
              </Button>
            </div>

            {totalPages > 1 && (
              <div className="d-flex justify-content-center mt-4">
                <Pagination>
                  <Pagination.First onClick={() => setPage(0)} disabled={page === 0} />
                  <Pagination.Prev onClick={() => setPage(page - 1)} disabled={page === 0} />
                  
                  {[...Array(totalPages)].map((_, idx) => (
                    <Pagination.Item
                      key={idx}
                      active={idx === page}
                      onClick={() => setPage(idx)}
                    >
                      {idx + 1}
                    </Pagination.Item>
                  ))}
                  
                  <Pagination.Next onClick={() => setPage(page + 1)} disabled={page === totalPages - 1} />
                  <Pagination.Last onClick={() => setPage(totalPages - 1)} disabled={page === totalPages - 1} />
                </Pagination>
              </div>
            )}
          </>
        )}

        <UserFormModal
          show={showModal}
          user={selectedUser}
          onHide={() => setShowModal(false)}
          onSave={handleSave}
        />
      </Container>
    </Layout>
  );
};
