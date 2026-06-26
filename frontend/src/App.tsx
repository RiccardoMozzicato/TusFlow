import {useEffect, useState} from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import {useAppDispatch, useAppSelector} from '@/app/hooks'
import {loadCurrentUser, selectCurrentUser} from '@/features/auth/authSlice'
import { useSessionHeartbeat } from '@/features/health/useSessionHeartbeat'
import { useJobsRealtime } from '@/features/realtime/useJobsRealtime'
import ProtectedRoute from '@/components/ProtectedRoute'
import RequireRole from '@/components/RequireRole'
import AppLayout from '@/components/AppLayout'
import DashboardPage from '@/pages/DashboardPage'
import FailedJobsPage from '@/pages/FailedJobsPage'
import DocumentLibraryPage from '@/pages/DocumentLibraryPage'
import DocumentDetailsPage from '@/pages/DocumentDetailsPage'
import UploadPage from '@/pages/UploadPage'
import SharedWithMePage from '@/pages/SharedWithMePage'
import FavoritesPage from '@/pages/FavoritesPage'
import ProfilePage from '@/pages/ProfilePage'
import AdminPage from '@/pages/AdminPage'
import UserManagementPage from '@/pages/UserManagementPage'
import { ROLES } from '@/features/profile/roles'
import {getCurrentUser, type UserDTO} from "@/features/users/usersApi.ts";

export default function App() {
  const dispatch = useAppDispatch()
  const user = useAppSelector(selectCurrentUser)
  const [userDTO, setUserDTO] = useState<UserDTO | null>(null)
  const [updateUserDTO, setUpdateUserDTO] = useState<boolean>(true)

  // Bootstrap auth once on mount: ask the gateway who the user is.
  useEffect(() => {
    void dispatch(loadCurrentUser())
  }, [dispatch])

  // Periodically probe the gateway while authenticated: detects session expiry
  // (→ redirect to login) and backend reachability (→ status indicator).
  useSessionHeartbeat()

  useEffect(() => {
    async function retrieveCurrentUser() {
        setUpdateUserDTO(false)
        return await getCurrentUser()
    }

    if(updateUserDTO) {
        retrieveCurrentUser().then(user => setUserDTO(user))
    }

  }, [updateUserDTO]);

  // Open the live jobs socket once authenticated; mirrors AI outcomes onto the
  // documents slice so the Library updates without a refetch.
  useJobsRealtime()

  return (
    // basename matches Vite's base ('/ui/'): the app is mounted under /ui by
    // the gateway, so all routes are relative to it.
    <BrowserRouter basename="/ui">
      <Routes>
        <Route
          element={
            <ProtectedRoute>
              <AppLayout displayName={userDTO?.fullName || user?.username || 'unknown user' } />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="failed-jobs" element={<FailedJobsPage />} />
          <Route path="documents" element={<DocumentLibraryPage />} />
          <Route path="documents/:id" element={<DocumentDetailsPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="shared" element={<SharedWithMePage />} />
          <Route path="favorites" element={<FavoritesPage />} />
          <Route path="profile" element={<ProfilePage updateUser={() => setUpdateUserDTO(true)} />} />
          <Route
            path="admin"
            element={
              <RequireRole role={ROLES.admin}>
                <AdminPage updateUser={() => setUpdateUserDTO(true)} />
              </RequireRole>
            }
          />
          <Route
            path="admin/users"
            element={
              <RequireRole role={ROLES.admin}>
                <UserManagementPage />
              </RequireRole>
            }
          />
          {/* future feature routes go here */}
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
