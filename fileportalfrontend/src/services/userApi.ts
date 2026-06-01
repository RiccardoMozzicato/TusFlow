/**
 * User API Service
 */
import {getCsrfToken} from "../utils.ts";

// User interface for displaying data from DB
export interface User {
  id: string;
  fullName: string;
  email: string;
  organization: string;
  department?: string;
  role?: 'admin' | 'manager' | 'staff';
  storageQuota?: number;
  storageUsed?: number;
}

// User interface for creating new users
export interface UserCreate {
  id: string;
  username: string;
  email: string;
  organization: string;
  department?: string;
  role?: 'admin' | 'manager' | 'staff';
  storageQuota?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

const API_BASE_URL = '/api/v1/users';

/**
 * Convert frontend role format to backend format
 * e.g., "admin" -> "ROLE_admin"
 */
const toBackendRole = (role?: string): string | undefined => {
  if (!role) return undefined;
  // Se inizia già con ROLE_, restituiscilo così com'è
  if (role.startsWith('ROLE_')) return role;
  // Altrimenti aggiungi il prefisso
  return `ROLE_${role}`;
};

/**
 * Convert backend role format to frontend format
 * e.g., "ROLE_admin" -> "admin"
 */
const toFrontendRole = (role?: string): 'admin' | 'manager' | 'staff' | undefined => {
  if (!role) return undefined;
  // Rimuove il prefisso ROLE_ se presente
  const cleanRole = role.replace(/^ROLE_/, '').toLowerCase();
  return cleanRole as 'admin' | 'manager' | 'staff';
};

/**
 * Fetch all users with pagination
 */
export const getUsers = async (page: number = 0, size: number = 10): Promise<PageResponse<User>> => {
  const response = await fetch(`${API_BASE_URL}?page=${page}&size=${size}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch users');
  }

  const data: PageResponse<User> = await response.json();
  // Convert backend roles to frontend format
  data.content = data.content.map(user => ({
    ...user,
    role: toFrontendRole(user.role as string),
  }));

  return data;
};

/**
 * Fetch a single user by ID
 */
export const getUser = async (id: string): Promise<User> => {
  const response = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch user');
  }

  const user: User = await response.json();
  // Convert backend role to frontend format
  return {
    ...user,
    role: toFrontendRole(user.role as string),
  };
};

/**
 * Create a new user
 */
export const createUser = async (user: UserCreate): Promise<void> => {
  const csrfToken = getCsrfToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
  
  // Generate UUID for new user
  const userWithId = {
    ...user,
      role: toBackendRole(user.role as string),
  };



  const response = await fetch(API_BASE_URL, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(userWithId),
  });

  if (!response.ok) {
    const error = await response.text();
    console.error('Create user error:', error);
    console.error('Request body:', JSON.stringify(userWithId, null, 2));
    throw new Error(error || 'Failed to create user');
  }
};

/**
 * Update an existing user
 */
export const updateUser = async (id: string, user: User): Promise<void> => {
    const csrfToken = getCsrfToken();
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    };
    if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
    }

  // Convert role to backend format
  const userWithBackendRole = {
    ...user,
    role: toBackendRole(user.role),
  };

  const response = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'PUT',
    credentials: 'include',
    headers,
    body: JSON.stringify(userWithBackendRole),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || 'Failed to update user');
  }
};

/**
 * Delete a user
 */
export const deleteUser = async (id: string): Promise<void> => {
  const csrfToken = getCsrfToken();
  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
  
  const response = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    throw new Error('Failed to delete user');
  }
};
