/**
 * API Service for backend communication
 */

import { getCsrfToken } from '../utils';

const API_BASE_URL = '/api';

export interface UserInfo {
  sub: string;
  uuid?: string;
  username?: string;
  email?: string;
  email_verified?: boolean;
  preferred_username?: string;
  fullName?: string;
  department?: string;
  organization?: string;
  roles: string[];
  storageQuota?: number; // Storage quota in bytes
  storageUsed?: number;  // Storage used in bytes
}

/**
 * Initiate OAuth2 login by redirecting to the gateway's authorization endpoint
 */
export const initiateLogin = (): void => {
  window.location.href = 'http://localhost:8080/oauth2/authorization/gateway-client';
};

/**
 * Fetch user information from the gateway
 */
export const getUserInfo = async (): Promise<UserInfo> => {
  const csrfToken = getCsrfToken();
  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }

  // Get authentication info with roles from token
  const response = await fetch(`${API_BASE_URL}/auth/userinfo`, {
    method: 'GET',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    throw new Error('Failed to fetch user info');
  }

  const authData = await response.json();
  const roles = authData.roles;

  // Get additional user details (organization, department, storage quota)
  try {
    const userDetailsResponse = await fetch(`${API_BASE_URL}/v1/users/me`, {
      method: 'GET',
      credentials: 'include',
      headers,
    });
    console.log(userDetailsResponse);

    if (userDetailsResponse.ok) {
      const userDetails = await userDetailsResponse.json();

      // Merge data: roles from token, additional info from /users/me
      return {
        ...authData,
        roles, // Keep roles from token
        organization: userDetails.organization,
        department: userDetails.department,
        storageQuota: userDetails.storageQuota,
        storageUsed: userDetails.storageUsed,
      };
    }
  } catch (error) {
    console.warn('Failed to fetch additional user details:', error);
  }

  // Fallback: return only auth data if /users/me fails
  return {
    ...authData,
    roles,
  };
};

/**
 * Logout the user by navigating to the logout endpoint
 * Cannot use fetch() because logout redirects to Keycloak (CORS issues)
 * Must use window.location to perform a full browser navigation
 */
export const logout = (): void => {
  window.location.href = 'http://localhost:8080/logout';
};

/**
 * Check if user is authenticated
 */
export const checkAuth = async (): Promise<boolean> => {
  try {
    await getUserInfo();
    return true;
  } catch {
    return false;
  }
};
