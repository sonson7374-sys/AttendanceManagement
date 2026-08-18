class ApiConstants {
  // Android 에뮬레이터에서 호스트 localhost를 가리키는 IP
  // 실제 기기 사용 시 .env 또는 --dart-define 로 덮어씀
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1',
  );

  // 근무지관리 화면의 카카오맵이 로드할 관리자웹(React) 오리진. 개발환경은 Vite 개발서버(포트 3000),
  // 운영환경은 nginx가 admin-web과 API를 같은 오리진에서 서빙하므로 배포 도메인으로 덮어쓴다.
  static const String webAppBaseUrl = String.fromEnvironment(
    'WEB_APP_BASE_URL',
    defaultValue: 'http://10.0.2.2:3000',
  );

  // 인증이 필요 없는 순수 지도 페이지(관리자웹 KakaoMap 재사용) — 쿼리파라미터로 좌표/반경/편집여부를 전달.
  static const String kakaoMapEmbedPath = '/embed/kakao-map';

  static const Duration connectTimeout = Duration(seconds: 10);
  static const Duration receiveTimeout = Duration(seconds: 20);

  // Auth
  static const String login = '/auth/login';
  static const String refresh = '/auth/refresh';
  static const String logout = '/auth/logout';
  static const String me = '/auth/me';

  // Attendance
  static const String checkIn = '/attendance/check-in';
  static const String checkOut = '/attendance/check-out';
  static const String breakStart = '/attendance/break-start';
  static const String breakEnd = '/attendance/break-end';
  static const String today = '/attendance/today';
  // 근태 이력 조회: GET /attendance?from=&to= (백엔드 AttendanceController 기준)
  static const String records = '/attendance';

  // Change Requests (백엔드는 AttendanceController 하위에 매핑됨)
  static const String changeRequests = '/attendance/change-requests';
  static const String myChangeRequests = '/attendance/change-requests/my';

  // 휴가 신청
  static const String leaveRequests = '/leave-requests';
  static const String myLeaveRequests = '/leave-requests/my';

  // 외근·출장·재택근무 신청
  static const String outsideWorkRequests = '/outside-work-requests';
  static const String myOutsideWorkRequests = '/outside-work-requests/my';

  // Workplaces (관리자웹 WorkplacesPage와 동일한 API)
  static const String workplaces = '/workplaces';
  static const String assignedWorkplaces = '/workplaces/assigned';

  // 근무지 변경요청 (관리자웹과 동일한 API)
  static const String workplaceChangeRequests = '/workplace-change-requests';

  // Users (근무지 직원 배정 관리용 목록 조회)
  static const String users = '/users';

  // Devices
  static const String myDevices = '/users/me/devices';

  // Profile
  static const String changePassword = '/users/me/password';

  // Notifications
  static const String notifications = '/notifications';
  static const String notificationsUnreadCount = '/notifications/unread-count';

  // Menu permissions (관리자웹과 동일한 권한레벨 기반 메뉴 표시 여부 조회)
  static const String menuPermissionsMy = '/menu-permissions/my';

  // 일정관리 (관리자웹 SchedulesPage와 동일한 API)
  static const String calendarEvents = '/calendar-events';
  static const String holidays = '/admin/holidays';

  // 근무제 관리 (관리자웹 WorkSchedulesPage와 동일한 API)
  static const String workSchedules = '/work-schedules';
  static const String assignedWorkSchedule = '/work-schedules/assigned';
  static const String workScheduleOptions = '/work-schedules/options';

  // 근무제 변경요청 (관리자웹과 동일한 API)
  static const String workScheduleChangeRequests = '/work-schedule-change-requests';

  // 조직 (직원 등록/수정 폼의 소속 조직 선택용, 관리자웹과 동일한 API)
  static const String organizations = '/organizations';

  // 로그인 화면용 시스템 공용 로고 (인증 없이 조회 가능, 관리자웹과 동일한 API).
  // 관리자웹 사이드바용 로고와는 별개로 관리되는 슬롯이다.
  static const String loginLogo = '/logo/login';
}

class StorageKeys {
  static const String accessToken = 'access_token';
  static const String refreshToken = 'refresh_token';
  static const String userId = 'user_id';
  static const String userEmail = 'user_email';
  static const String userName = 'user_name';
  static const String userRole = 'user_role';
  static const String userLevel = 'user_level';
  static const String deviceId = 'device_id';
  static const String biometricEnabled = 'biometric_enabled';
  static const String locationConsentGiven = 'location_consent_given';
}
