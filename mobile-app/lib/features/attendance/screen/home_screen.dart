import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../model/attendance_model.dart';
import '../provider/attendance_provider.dart';
import '../provider/location_provider.dart';
import '../../auth/provider/auth_provider.dart';
import '../../notification/provider/notification_provider.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../workplace/widget/kakao_map_view.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/storage/secure_storage.dart';
import '../../../core/utils/kst.dart';

/// 실시간 위치 지도 갱신 주기. 배터리 소모와 반응성의 절충으로 10초를 택했다(진짜 연속
/// 스트림 대신 화면이 켜져 있는 동안만 폴링).
const _kLocationPollInterval = Duration(seconds: 10);

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  bool _isActionLoading = false;
  String? _userName;
  Timer? _pollTimer;
  Timer? _locationPollTimer;

  @override
  void initState() {
    super.initState();
    _loadUserName();
    Future.microtask(() => ref.read(liveLocationProvider.notifier).refresh());
    // 관리자가 근태 보정(변경 요청 없이 직접 수정)을 하면 앱은 이를 알 수 있는 별도
    // 채널이 없으므로, 화면이 떠 있는 동안 주기적으로 재조회해 지각 배지 등이
    // 늦게 갱신되는 문제를 방지한다.
    _pollTimer = Timer.periodic(const Duration(hours: 1), (_) {
      ref.read(todayAttendanceProvider.notifier).load(silent: true);
    });
    // 출근하기 버튼 아래 실시간 위치 지도용 — 배정 근무지 목록은 캐시를 재사용하고
    // GPS 위치만 다시 잰다(refreshPositionOnly). 화면을 벗어나면 dispose()에서 멈춘다.
    _locationPollTimer = Timer.periodic(_kLocationPollInterval, (_) {
      ref.read(liveLocationProvider.notifier).refreshPositionOnly();
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _locationPollTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadUserName() async {
    final info = await SecureStorage.getUserInfo();
    if (mounted) setState(() => _userName = info['name']);
  }

  Future<void> _doAction(Future<void> Function() action) async {
    if (_isActionLoading) return;
    setState(() => _isActionLoading = true);
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) _showError(e.message);
    } catch (e) {
      if (mounted) _showError(e.toString());
    } finally {
      if (mounted) setState(() => _isActionLoading = false);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final attendanceAsync = ref.watch(todayAttendanceProvider);
    final today = DateFormat('yyyy년 M월 d일 EEEE', 'ko').format(DateTime.now());

    final unreadCount = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;

    // 위치를 아직 모르는 동안(로딩·오류)에는 판단할 수 없으므로 null로 두고
    // 기본 색으로 표시한다. 최종 지오펜스 판정은 서버가 하며, 이 값은
    // 출근하기/퇴근하기 버튼 색상 표시용 참고 정보일 뿐이다.
    final location = ref.watch(liveLocationProvider).valueOrNull;
    final withinGeofenceRange =
        location == null ? null : location.distanceMeters <= location.workplace.radiusMeters;

    return Scaffold(
      drawer: const AppMenuDrawer(),
      appBar: AppBar(
        leading: const AppMenuLeadingButton(),
        title: const Text('출퇴근 관리'),
        actions: [
          IconButton(
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(Icons.notifications_outlined),
                if (unreadCount > 0)
                  Positioned(
                    right: -2,
                    top: -2,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      constraints: const BoxConstraints(minWidth: 14, minHeight: 14),
                      decoration: const BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                      ),
                      child: Text(
                        unreadCount > 9 ? '9+' : '$unreadCount',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.white, fontSize: 9),
                      ),
                    ),
                  ),
              ],
            ),
            tooltip: '알림',
            onPressed: () => context.push('/notifications'),
          ),
          IconButton(
            icon: const Icon(Icons.person_outline),
            tooltip: '내 정보',
            onPressed: () => context.push('/profile'),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: '로그아웃',
            onPressed: () async {
              final confirmed = await showDialog<bool>(
                context: context,
                builder: (ctx) => AlertDialog(
                  title: const Text('로그아웃'),
                  content: const Text('로그아웃하시겠습니까?'),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(ctx, false),
                      child: const Text('취소'),
                    ),
                    FilledButton(
                      onPressed: () => Navigator.pop(ctx, true),
                      child: const Text('로그아웃'),
                    ),
                  ],
                ),
              );
              if (confirmed == true && mounted) {
                await ref.read(authNotifierProvider.notifier).logout();
              }
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => Future.wait([
          ref.read(todayAttendanceProvider.notifier).load(),
          ref.read(liveLocationProvider.notifier).refresh(),
        ]),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // 날짜 + 인사
            _GreetingCard(userName: _userName, today: today),
            const SizedBox(height: 16),

            // 근무지 거리 / GPS 정확도 (위치 새로고침)
            const _LocationCard(),
            const SizedBox(height: 16),

            // 근태 상태 카드
            attendanceAsync.when(
              loading: () => const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: CircularProgressIndicator(),
                ),
              ),
              error: (e, _) => _ErrorCard(
                message: e.toString(),
                onRetry: () =>
                    ref.read(todayAttendanceProvider.notifier).load(),
              ),
              data: (attendance) => attendance == null
                  ? const _NoDataCard()
                  : Column(
                      children: [
                        _StatusCard(attendance: attendance),
                        const SizedBox(height: 16),
                        _ActionButtons(
                          attendance: attendance,
                          isLoading: _isActionLoading,
                          withinGeofenceRange: withinGeofenceRange,
                          onCheckIn: () => _doAction(
                            () => ref
                                .read(todayAttendanceProvider.notifier)
                                .checkIn(),
                          ),
                          onCheckOut: () => _doAction(
                            () => ref
                                .read(todayAttendanceProvider.notifier)
                                .checkOut(),
                          ),
                          onStartBreak: () => _doAction(
                            () => ref
                                .read(todayAttendanceProvider.notifier)
                                .startBreak(),
                          ),
                          onEndBreak: () => _doAction(
                            () => ref
                                .read(todayAttendanceProvider.notifier)
                                .endBreak(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        const _LiveMapCard(),
                        if (attendance.checkInAt != null) ...[
                          const SizedBox(height: 16),
                          _TimelineCard(attendance: attendance),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _GreetingCard extends StatelessWidget {
  const _GreetingCard({this.userName, required this.today});

  final String? userName;
  final String today;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hour = DateTime.now().hour;
    final greeting = hour < 12
        ? '좋은 아침이에요'
        : hour < 18
            ? '안녕하세요'
            : '수고하셨습니다';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(today, style: theme.textTheme.bodySmall),
            const SizedBox(height: 4),
            Text(
              '$greeting${userName != null ? ', ${userName!}님' : ''}!',
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LocationCard extends ConsumerWidget {
  const _LocationCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final locationAsync = ref.watch(liveLocationProvider);

    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(Icons.my_location, size: 20, color: theme.colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(
              child: locationAsync.when(
                loading: () => const Text('현재 위치 확인 중...'),
                error: (e, _) => Text(
                  e.toString().replaceFirst('Exception: ', ''),
                  style: TextStyle(color: theme.colorScheme.error, fontSize: 13),
                ),
                data: (info) => info == null
                    ? const Text('위치 정보가 없습니다.')
                    : Text(
                        '${info.workplace.name}까지 ${info.distanceMeters.round()}m'
                        ' · 정확도 ${info.accuracyMeters.round()}m',
                        style: theme.textTheme.bodyMedium,
                      ),
              ),
            ),
            IconButton(
              icon: locationAsync.isLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.refresh),
              tooltip: '위치 새로고침',
              onPressed: locationAsync.isLoading
                  ? null
                  : () => ref.read(liveLocationProvider.notifier).refresh(),
            ),
          ],
        ),
      ),
    );
  }
}

/// "출근하기" 버튼 아래 실시간 위치 지도. 근무지관리 화면과 같은 [KakaoMapView]를 재사용하되,
/// 배정 근무지(고정 좌표, 마커+지오펜스 원)와 내 현재 위치(움직이는 파란 점)를 함께 보여준다.
/// 지도(WebView) 자체는 배정 근무지가 바뀌지 않는 한 다시 만들지 않고, 위치 갱신은
/// [KakaoMapViewState.setDevicePosition]으로만 반영해 매번 다시 로드되어 깜빡이는 것을 막는다.
class _LiveMapCard extends ConsumerStatefulWidget {
  const _LiveMapCard();

  @override
  ConsumerState<_LiveMapCard> createState() => _LiveMapCardState();
}

class _LiveMapCardState extends ConsumerState<_LiveMapCard> {
  var _mapKey = GlobalKey<KakaoMapViewState>();
  // 마지막으로 성공한 위치 정보를 보관한다. liveLocationProvider는 10초 폴링마다 잠깐
  // AsyncLoading으로 바뀌는데(값이 없는 순수 loading 상태), 그때마다 지도를 placeholder로
  // 바꿔치기하면 KakaoMapView(WebView)가 매번 사라졌다 다시 생성되어 계속 새로 로드된다.
  // 그래서 로딩/에러 tick은 무시하고 이 값을 계속 화면에 유지한다.
  LiveLocationInfo? _lastInfo;

  @override
  Widget build(BuildContext context) {
    ref.listen<AsyncValue<LiveLocationInfo?>>(liveLocationProvider, (previous, next) {
      final info = next.valueOrNull;
      if (info == null) return; // 로딩 중이거나 이번 tick이 에러 — 마지막 위치를 그대로 유지.
      if (info.workplace.id != _lastInfo?.workplace.id) {
        // 배정 근무지가 바뀐 경우(드묾)에만 새 GlobalKey로 지도를 다시 만들어 지오펜스 원
        // 좌표를 갱신한다. setState로 build()를 다시 태워야 새 KakaoMapView가 반영된다.
        setState(() {
          _lastInfo = info;
          _mapKey = GlobalKey<KakaoMapViewState>();
        });
      } else {
        _lastInfo = info;
        _mapKey.currentState?.setDevicePosition(info.latitude, info.longitude);
      }
    });

    final info = _lastInfo ?? ref.read(liveLocationProvider).valueOrNull;
    if (info == null) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Text('위치를 확인하면 지도가 표시됩니다.', style: TextStyle(fontSize: 13)),
        ),
      );
    }
    _lastInfo = info;

    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: KakaoMapView(
        key: _mapKey,
        latitude: info.workplace.latitude,
        longitude: info.workplace.longitude,
        radiusMeters: info.workplace.radiusMeters,
        deviceLatitude: info.latitude,
        deviceLongitude: info.longitude,
        height: 220,
      ),
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.attendance});

  final TodayAttendance attendance;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = attendance.status;

    Color statusColor;
    switch (status.colorKey) {
      case 'primary':
        statusColor = theme.colorScheme.primary;
      case 'error':
        statusColor = theme.colorScheme.error;
      case 'secondary':
        statusColor = theme.colorScheme.secondary;
      case 'tertiary':
        statusColor = theme.colorScheme.tertiary;
      default:
        statusColor = theme.colorScheme.outline;
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: statusColor,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  '오늘 근태 현황',
                  style: theme.textTheme.titleSmall,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              status.displayName,
              style: theme.textTheme.headlineMedium?.copyWith(
                color: statusColor,
                fontWeight: FontWeight.bold,
              ),
            ),
            if (attendance.workplaceName != null) ...[
              const SizedBox(height: 4),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.location_on, size: 14,
                      color: theme.colorScheme.onSurfaceVariant),
                  const SizedBox(width: 4),
                  Text(
                    attendance.workplaceName!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ],
            if (attendance.workMinutes != null &&
                attendance.workMinutes! > 0) ...[
              const SizedBox(height: 8),
              Text(
                '근무 ${attendance.workMinutes! ~/ 60}시간 '
                '${attendance.workMinutes! % 60}분',
                style: theme.textTheme.bodyMedium,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ActionButtons extends StatelessWidget {
  const _ActionButtons({
    required this.attendance,
    required this.isLoading,
    required this.onCheckIn,
    required this.onCheckOut,
    required this.onStartBreak,
    required this.onEndBreak,
    this.withinGeofenceRange,
  });

  final TodayAttendance attendance;
  final bool isLoading;
  final VoidCallback onCheckIn;
  final VoidCallback onCheckOut;
  final VoidCallback onStartBreak;
  final VoidCallback onEndBreak;
  final bool? withinGeofenceRange;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (attendance.canCheckIn)
          _BigActionButton(
            icon: Icons.login_rounded,
            label: '출근하기',
            color: withinGeofenceRange == false ? Colors.red : Colors.green,
            onPressed: isLoading ? null : onCheckIn,
            isLoading: isLoading,
          ),
        if (attendance.canCheckOut)
          _BigActionButton(
            icon: Icons.logout_rounded,
            label: '퇴근하기',
            color: withinGeofenceRange == false ? Colors.red : Colors.blue,
            onPressed: isLoading ? null : onCheckOut,
            isLoading: isLoading,
          ),
        if (attendance.canStartBreak) ...[
          const SizedBox(height: 12),
          _BigActionButton(
            icon: Icons.free_breakfast_outlined,
            label: '휴식시작',
            color: Colors.lightGreen,
            onPressed: isLoading ? null : onStartBreak,
            isLoading: isLoading,
          ),
        ],
        if (attendance.canEndBreak) ...[
          const SizedBox(height: 12),
          _BigActionButton(
            icon: Icons.play_circle_outline,
            label: '휴식종료',
            color: Colors.teal,
            onPressed: isLoading ? null : onEndBreak,
            isLoading: isLoading,
          ),
        ],
      ],
    );
  }
}

class _BigActionButton extends StatelessWidget {
  const _BigActionButton({
    required this.icon,
    required this.label,
    required this.color,
    this.onPressed,
    required this.isLoading,
  });

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback? onPressed;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      onPressed: onPressed,
      style: FilledButton.styleFrom(
        backgroundColor: color,
        minimumSize: const Size.fromHeight(56),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      icon: isLoading
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Colors.white,
              ),
            )
          : Icon(icon),
      label: Text(label, style: const TextStyle(fontSize: 16)),
    );
  }
}

class _TimelineCard extends StatelessWidget {
  const _TimelineCard({required this.attendance});

  final TodayAttendance attendance;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fmt = DateFormat('HH:mm');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('시간 기록', style: theme.textTheme.titleSmall),
            const SizedBox(height: 12),
            _TimeRow(
              icon: Icons.login,
              label: '출근',
              time: attendance.checkInAt != null
                  ? fmt.format(attendance.checkInAt!.toKst())
                  : '-',
              badge: attendance.isLate == true ? '지각' : null,
              badgeColor: theme.colorScheme.error,
            ),
            if (attendance.checkOutAt != null) ...[
              const SizedBox(height: 8),
              _TimeRow(
                icon: Icons.logout,
                label: '퇴근',
                time: fmt.format(attendance.checkOutAt!.toKst()),
                badge: attendance.isEarlyLeave == true ? '조퇴' : null,
                badgeColor: theme.colorScheme.error,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _TimeRow extends StatelessWidget {
  const _TimeRow({
    required this.icon,
    required this.label,
    required this.time,
    this.badge,
    this.badgeColor,
  });

  final IconData icon;
  final String label;
  final String time;
  final String? badge;
  final Color? badgeColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(icon, size: 18, color: theme.colorScheme.primary),
        const SizedBox(width: 8),
        Text(label, style: theme.textTheme.bodyMedium),
        const SizedBox(width: 8),
        Text(
          time,
          style: theme.textTheme.bodyMedium?.copyWith(
            fontWeight: FontWeight.bold,
          ),
        ),
        if (badge != null) ...[
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              color: badgeColor,
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              badge!,
              style: theme.textTheme.labelSmall?.copyWith(color: Colors.white),
            ),
          ),
        ],
      ],
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            const Icon(Icons.error_outline, size: 48),
            const SizedBox(height: 8),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton(onPressed: onRetry, child: const Text('다시 시도')),
          ],
        ),
      ),
    );
  }
}

class _NoDataCard extends StatelessWidget {
  const _NoDataCard();

  @override
  Widget build(BuildContext context) {
    return const Card(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Column(
          children: [
            Icon(Icons.inbox_outlined, size: 48),
            SizedBox(height: 8),
            Text('오늘 근태 정보가 없습니다.'),
          ],
        ),
      ),
    );
  }
}
