import 'package:flutter/material.dart';

/// 관리자웹 Layout.tsx의 NAV_ITEMS 중 모바일에 노출할 항목만 미러링한다.
/// 대시보드·감사 로그·권한관리는 관리자웹 전용 화면이라 모바일 메뉴에서 제외한다.
/// route가 없는 항목은 아직 모바일 화면이 구현되지 않았다는 뜻이며, 탭하면 준비 중 안내만 표시한다.
class AppMenuItem {
  const AppMenuItem({
    required this.menuKey,
    required this.label,
    required this.icon,
    this.route,
  });

  final String menuKey;
  final String label;
  final IconData icon;
  final String? route;
}

const List<AppMenuItem> kAppMenuItems = [
  AppMenuItem(menuKey: 'my-attendance', label: '출근부', icon: Icons.access_time_outlined, route: '/'),
  AppMenuItem(menuKey: 'attendance', label: '근태 조회', icon: Icons.fact_check_outlined, route: '/history'),
  AppMenuItem(menuKey: 'approvals', label: '승인함', icon: Icons.check_circle_outline, route: '/change-request'),
  AppMenuItem(menuKey: 'schedules', label: '일정관리', icon: Icons.calendar_month_outlined, route: '/schedules'),
  AppMenuItem(menuKey: 'employees', label: '직원 관리', icon: Icons.people_outline, route: '/employees'),
  AppMenuItem(menuKey: 'workplaces', label: '근무지 관리', icon: Icons.location_on_outlined, route: '/workplaces'),
  AppMenuItem(menuKey: 'organizations', label: '부서 관리', icon: Icons.account_tree_outlined),
  AppMenuItem(menuKey: 'work-schedules', label: '근무제 관리', icon: Icons.schedule_outlined, route: '/work-schedules'),
  AppMenuItem(menuKey: 'holidays', label: '휴일/휴가 관리', icon: Icons.event_outlined, route: '/leave-request'),
];
