import React, { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
// Use real Keycloak API
import { getUserInfo, initiateLogin, logout as apiLogout } from '../services/api';
import type { UserInfo } from '../services/api';
import { AuthContext } from './AuthContextDefinition';
import type { AuthContextType } from './AuthContextDefinition';

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const userInfo = await getUserInfo();
      setUser(userInfo);
      console.log('User info fetched:', userInfo);
    } catch {
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  };

  const login = () => {
    initiateLogin();
  };

  const logout = () => {
    // This will trigger a full page navigation to the logout endpoint
    // which then redirects to Keycloak for OIDC logout
    // The page will reload after logout, so no need to setUser(null) here
    apiLogout();
  };

  const hasRole = (role: string): boolean => {
    return user?.roles?.includes(role) ?? false;
  };

  const hasAnyRole = (roles: string[]): boolean => {
    return roles.some(role => hasRole(role));
  };

  const value: AuthContextType = {
    user,
    isAuthenticated: !!user,
    isLoading,
    login,
    logout,
    hasRole,
    hasAnyRole,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
