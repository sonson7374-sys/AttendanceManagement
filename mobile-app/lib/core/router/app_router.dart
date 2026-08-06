import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/auth/provider/auth_provider.dart';
import '../../features/auth/provider/biometric_provider.dart';
import '../../features/auth/screen/login_screen.dart';
import '../../features/auth/screen/lock_screen.dart';
import '../../features/attendance/screen/home_screen.dart';
import '../../features/attendance/screen/history_screen.dart';
import '../../features/change_request/screen/change_request_screen.dart';
import '../../features/leave_request/screen/leave_request_screen.dart';
import '../../features/notification/screen/notification_screen.dart';
import '../../features/profile/screen/profile_screen.dart';
import '../../features/profile/screen/change_password_screen.dart';
import '../../features/schedule/screen/schedules_screen.dart';
import '../../features/workplace/screen/workplaces_screen.dart';
import '../../features/work_schedule/screen/work_schedule_screen.dart';
import '../../features/employee/screen/employees_screen.dart';
import '../shell/main_shell.dart';

final routerProvider = Provider<GoRouter>((ref) {
  // authNotifierProvider 를 watch 해야 로그인/로그아웃 시 라우터가 재평가됨
  final authState = ref.watch(authNotifierProvider);
  final biometricEnabled = ref.watch(biometricEnabledProvider).valueOrNull ?? false;
  final biometricUnlocked = ref.watch(biometricUnlockProvider);

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final isLoggedIn = authState.valueOrNull ?? false;
      final isLoginRoute = state.matchedLocation == '/login';
      final isLockRoute = state.matchedLocation == '/lock';
      final needsBiometricUnlock =
          isLoggedIn && biometricEnabled && !biometricUnlocked;

      if (!isLoggedIn) return isLoginRoute ? null : '/login';
      if (needsBiometricUnlock) return isLockRoute ? null : '/lock';
      if (isLoginRoute || isLockRoute) return '/';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/lock',
        builder: (context, state) => const LockScreen(),
      ),
      GoRoute(
        path: '/notifications',
        builder: (context, state) => const NotificationScreen(),
      ),
      GoRoute(
        path: '/profile',
        builder: (context, state) => const ProfileScreen(),
        routes: [
          GoRoute(
            path: 'change-password',
            builder: (context, state) => const ChangePasswordScreen(),
          ),
        ],
      ),
      ShellRoute(
        builder: (context, state, child) => MainShell(child: child),
        routes: [
          GoRoute(
            path: '/',
            builder: (context, state) => const HomeScreen(),
          ),
          GoRoute(
            path: '/history',
            builder: (context, state) => const HistoryScreen(),
          ),
          GoRoute(
            path: '/change-request',
            builder: (context, state) => const ChangeRequestScreen(),
          ),
          GoRoute(
            path: '/leave-request',
            builder: (context, state) => const LeaveRequestScreen(),
          ),
          GoRoute(
            path: '/schedules',
            builder: (context, state) => const SchedulesScreen(),
          ),
          GoRoute(
            path: '/workplaces',
            builder: (context, state) => const WorkplacesScreen(),
          ),
          GoRoute(
            path: '/work-schedules',
            builder: (context, state) => const WorkScheduleScreen(),
          ),
          GoRoute(
            path: '/employees',
            builder: (context, state) => const EmployeesScreen(),
          ),
        ],
      ),
    ],
  );
});
