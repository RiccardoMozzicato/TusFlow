import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useAppDispatch } from '@/app/hooks'
import { loadCurrentUser } from '@/features/auth/authSlice'
import ProtectedRoute from '@/components/ProtectedRoute'
import RequireRole from '@/components/RequireRole'
import AppLayout from '@/components/AppLayout'
import DashboardPage from '@/pages/DashboardPage'
import DocumentLibraryPage from '@/pages/DocumentLibraryPage'
import DocumentDetailsPage from '@/pages/DocumentDetailsPage'
import UploadPage from '@/pages/UploadPage'
import ProfilePage from '@/pages/ProfilePage'
import AdminPage from '@/pages/AdminPage'
import { ROLES } from '@/features/profile/roles'

export default function App() {
  const dispatch = useAppDispatch()

  // Bootstrap auth once on mount: ask the gateway who the user is.
  useEffect(() => {
    void dispatch(loadCurrentUser())
  }, [dispatch])

  return (
    // basename matches Vite's base ('/ui/'): the app is mounted under /ui by
    // the gateway, so all routes are relative to it.
    <BrowserRouter basename="/ui">
      <Routes>
        <Route
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="documents" element={<DocumentLibraryPage />} />
          <Route path="documents/:id" element={<DocumentDetailsPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route
            path="admin"
            element={
              <RequireRole role={ROLES.admin}>
                <AdminPage />
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
