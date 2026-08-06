import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import ProtectedRoute from '@/components/ProtectedRoute'
import LoginPage from '@/pages/LoginPage'
import DashboardPage from '@/pages/DashboardPage'
import AttendancePage from '@/pages/AttendancePage'
import ApprovalsPage from '@/pages/ApprovalsPage'
import EmployeesPage from '@/pages/EmployeesPage'
import WorkplacesPage from '@/pages/WorkplacesPage'
import OrganizationsPage from '@/pages/OrganizationsPage'
import WorkSchedulesPage from '@/pages/WorkSchedulesPage'
import HolidaysPage from '@/pages/HolidaysPage'
import AuditLogPage from '@/pages/AuditLogPage'
import PermissionsPage from '@/pages/PermissionsPage'
import MyAttendancePage from '@/pages/MyAttendancePage'
import SchedulesPage from '@/pages/SchedulesPage'
import { usePermissions } from '@/hooks/usePermissions'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

// "/" 진입 시 대시보드 메뉴 권한이 있는 계정만 대시보드를 보고, 없는 계정은 출근부로 보낸다.
// 권한 조회가 끝나기 전에는(로그인 직후 등) 잘못된 화면이 잠깐이라도 보이지 않도록 로딩만 표시한다.
function DashboardGate() {
  const { isMenuVisible, isLoading } = usePermissions()
  if (isLoading) return null
  if (!isMenuVisible('dashboard')) return <Navigate to="/my-attendance" replace />
  return <DashboardPage />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={
            <ProtectedRoute><DashboardGate /></ProtectedRoute>
          } />
          <Route path="/my-attendance" element={
            <ProtectedRoute><MyAttendancePage /></ProtectedRoute>
          } />
          <Route path="/attendance" element={
            <ProtectedRoute><AttendancePage /></ProtectedRoute>
          } />
          <Route path="/approvals" element={
            <ProtectedRoute><ApprovalsPage /></ProtectedRoute>
          } />
          <Route path="/schedules" element={
            <ProtectedRoute><SchedulesPage /></ProtectedRoute>
          } />
          <Route path="/employees" element={
            <ProtectedRoute><EmployeesPage /></ProtectedRoute>
          } />
          <Route path="/workplaces" element={
            <ProtectedRoute><WorkplacesPage /></ProtectedRoute>
          } />
          <Route path="/organizations" element={
            <ProtectedRoute><OrganizationsPage /></ProtectedRoute>
          } />
          <Route path="/work-schedules" element={
            <ProtectedRoute><WorkSchedulesPage /></ProtectedRoute>
          } />
          <Route path="/holidays" element={
            <ProtectedRoute><HolidaysPage /></ProtectedRoute>
          } />
          <Route path="/audit-logs" element={
            <ProtectedRoute><AuditLogPage /></ProtectedRoute>
          } />
          <Route path="/permissions" element={
            <ProtectedRoute requiredRole="SYSTEM_ADMIN"><PermissionsPage /></ProtectedRoute>
          } />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
      <Toaster position="top-right" />
    </QueryClientProvider>
  )
}
