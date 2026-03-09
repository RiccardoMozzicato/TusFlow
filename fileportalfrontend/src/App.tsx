import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
//import { Login } from './components/Login';
import { Home } from './components/Home';
import { AdminDashboard } from './components/AdminDashboard';
import { ManagerDashboard } from './components/ManagerDashboard';
import { StaffDashboard } from './components/StaffDashboard';
import { Unauthorized } from './components/Unauthorized';
import './App.css';

function App() {
  return (
    <Router basename="/ui">
      <AuthProvider>
        <Routes>
            {/*<Route path="/login" element={<Login />} />*/}
          <Route path="/unauthorized" element={<Unauthorized />} />
          
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Home />
              </ProtectedRoute>
            }
          />
          
          <Route
            path="/admin"
            element={
              <ProtectedRoute requiredRoles={['admin']}>
                <AdminDashboard />
              </ProtectedRoute>
            }
          />
          
          <Route
            path="/manager"
            element={
              <ProtectedRoute requiredRoles={['manager']}>
                <ManagerDashboard />
              </ProtectedRoute>
            }
          />
          
          <Route
            path="/staff"
            element={
              <ProtectedRoute requiredRoles={['staff']}>
                <StaffDashboard />
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </Router>
  );
}

export default App;
