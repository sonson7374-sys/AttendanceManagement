import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../model/change_request_model.dart';
import '../provider/change_request_provider.dart';
import '../../attendance/provider/attendance_provider.dart';
import '../../attendance/model/attendance_model.dart';
import '../../workplace/model/workplace_model.dart';
import '../../workplace/provider/workplace_provider.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/utils/kst.dart';

class ChangeRequestScreen extends ConsumerStatefulWidget {
  const ChangeRequestScreen({super.key});

  @override
  ConsumerState<ChangeRequestScreen> createState() =>
      _ChangeRequestScreenState();
}

class _ChangeRequestScreenState extends ConsumerState<ChangeRequestScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppMenuDrawer(),
      appBar: AppBar(
        leading: const AppMenuLeadingButton(),
        title: const Text('수정 요청'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: '새 요청'),
            Tab(text: '내 요청 목록'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _SubmitTab(
            onSubmitted: () => _tabController.animateTo(1),
          ),
          const _MyRequestsTab(),
        ],
      ),
    );
  }
}

// ─── 새 요청 탭 ───────────────────────────────────────────────

class _SubmitTab extends ConsumerStatefulWidget {
  const _SubmitTab({required this.onSubmitted});

  final VoidCallback onSubmitted;

  @override
  ConsumerState<_SubmitTab> createState() => _SubmitTabState();
}

class _SubmitTabState extends ConsumerState<_SubmitTab> {
  final _formKey = GlobalKey<FormState>();
  final _reasonCtrl = TextEditingController();

  AttendanceRecord? _selectedRecord;
  String? _selectedType;
  TimeOfDay? _requestedCheckIn;
  TimeOfDay? _requestedCheckOut;
  Workplace? _selectedWorkplace;

  @override
  void dispose() {
    _reasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedRecord == null) {
      _showError('수정할 날짜를 선택해주세요.');
      return;
    }
    if (_selectedType == null) {
      _showError('요청 유형을 선택해주세요.');
      return;
    }

    final now = DateTime.now();
    DateTime? checkInDt;
    DateTime? checkOutDt;

    if (_requestedCheckIn != null) {
      checkInDt = DateTime(
        now.year,
        now.month,
        now.day,
        _requestedCheckIn!.hour,
        _requestedCheckIn!.minute,
      );
    }
    if (_requestedCheckOut != null) {
      checkOutDt = DateTime(
        now.year,
        now.month,
        now.day,
        _requestedCheckOut!.hour,
        _requestedCheckOut!.minute,
      );
    }

    final success =
        await ref.read(submitChangeRequestProvider.notifier).submit(
              ChangeRequestSubmit(
                recordId: _selectedRecord!.recordId,
                changeType: _selectedType!,
                reason: _reasonCtrl.text.trim(),
                requestedCheckIn: checkInDt,
                requestedCheckOut: checkOutDt,
                requestedWorkplaceId: _selectedWorkplace?.id,
              ),
            );

    if (!mounted) return;

    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('수정 요청이 제출되었습니다.')),
      );
      ref.invalidate(myChangeRequestsProvider);
      _reset();
      widget.onSubmitted();
    } else {
      final error = ref.read(submitChangeRequestProvider).error;
      _showError(error is ApiException ? error.message : '요청 제출 실패');
    }
  }

  void _reset() {
    _formKey.currentState?.reset();
    _reasonCtrl.clear();
    setState(() {
      _selectedRecord = null;
      _selectedType = null;
      _requestedCheckIn = null;
      _requestedCheckOut = null;
      _selectedWorkplace = null;
    });
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  bool get _needsTime =>
      _selectedType == 'CHECK_IN_TIME' || _selectedType == 'CHECK_OUT_TIME';

  bool get _needsWorkplace => _selectedType == 'WORKPLACE_CHANGE';

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final recordsAsync = ref.watch(
      monthlyRecordsProvider((now.year, now.month)),
    );
    final isLoading = ref.watch(submitChangeRequestProvider).isLoading;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 날짜(근태 기록) 선택
            Text('수정할 날짜', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            recordsAsync.when(
              loading: () => const LinearProgressIndicator(),
              error: (_, __) => const Text('근태 내역을 불러올 수 없습니다.'),
              data: (records) => DropdownButtonFormField<AttendanceRecord>(
                value: _selectedRecord,
                isExpanded: true,
                hint: const Text('날짜 선택'),
                items: records.map((r) {
                  final date =
                      DateFormat('M/d (E)', 'ko').format(DateTime.parse(r.workDate));
                  // AttendanceStatusExt와 ChangeRequestStatusExt가 둘 다 String에
                  // displayName을 정의하므로 명시적으로 지정해 모호함을 해소한다.
                  final statusLabel = AttendanceStatusExt(r.status).displayName;
                  return DropdownMenuItem(
                    value: r,
                    child: Text('$date — $statusLabel'),
                  );
                }).toList(),
                onChanged: (v) => setState(() => _selectedRecord = v),
                validator: (v) => v == null ? '날짜를 선택해주세요.' : null,
              ),
            ),

            const SizedBox(height: 16),

            // 요청 유형
            Text('요청 유형', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: _selectedType,
              isExpanded: true,
              hint: const Text('유형 선택'),
              items: changeRequestTypes
                  .map((t) => DropdownMenuItem(
                        value: t.$1,
                        child: Text(t.$2),
                      ))
                  .toList(),
              onChanged: (v) => setState(() {
                _selectedType = v;
                _selectedWorkplace = null;
              }),
              validator: (v) => v == null ? '요청 유형을 선택해주세요.' : null,
            ),

            // 근무지 선택 (근무지 변경 유형일 때만)
            if (_needsWorkplace) ...[
              const SizedBox(height: 16),
              Text('변경할 근무지', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              Consumer(builder: (context, ref, _) {
                final workplacesAsync = ref.watch(assignedWorkplacesProvider);
                return workplacesAsync.when(
                  loading: () => const LinearProgressIndicator(),
                  error: (_, __) => const Text('근무지 목록을 불러올 수 없습니다.'),
                  data: (workplaces) => DropdownButtonFormField<Workplace>(
                    value: _selectedWorkplace,
                    isExpanded: true,
                    hint: const Text('근무지 선택'),
                    items: workplaces
                        .map((w) => DropdownMenuItem(value: w, child: Text(w.name)))
                        .toList(),
                    onChanged: (v) => setState(() => _selectedWorkplace = v),
                    validator: (v) => v == null ? '근무지를 선택해주세요.' : null,
                  ),
                );
              }),
            ],

            // 시간 입력 (시간 수정 유형일 때만)
            if (_needsTime) ...[
              const SizedBox(height: 16),
              Text('시간 입력', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: _TimePicker(
                      label: '출근 시간',
                      value: _requestedCheckIn,
                      onChanged: (v) =>
                          setState(() => _requestedCheckIn = v),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _TimePicker(
                      label: '퇴근 시간',
                      value: _requestedCheckOut,
                      onChanged: (v) =>
                          setState(() => _requestedCheckOut = v),
                    ),
                  ),
                ],
              ),
            ],

            const SizedBox(height: 16),

            // 사유
            Text('사유', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            TextFormField(
              controller: _reasonCtrl,
              maxLines: 4,
              maxLength: 500,
              decoration: const InputDecoration(
                hintText: '수정 사유를 상세히 입력해주세요.',
                border: OutlineInputBorder(),
              ),
              validator: (v) {
                if (v == null || v.trim().isEmpty) return '사유를 입력해주세요.';
                if (v.trim().length < 5) return '사유를 5자 이상 입력해주세요.';
                return null;
              },
            ),

            const SizedBox(height: 16),

            FilledButton(
              onPressed: isLoading ? null : _submit,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              child: isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('수정 요청 제출'),
            ),
          ],
        ),
      ),
    );
  }
}

class _TimePicker extends StatelessWidget {
  const _TimePicker({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final TimeOfDay? value;
  final ValueChanged<TimeOfDay?> onChanged;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      onPressed: () async {
        final picked = await showTimePicker(
          context: context,
          initialTime: value ?? TimeOfDay.now(),
        );
        onChanged(picked);
      },
      child: Text(
        value != null
            ? '${value!.hour.toString().padLeft(2, '0')}:${value!.minute.toString().padLeft(2, '0')}'
            : label,
      ),
    );
  }
}

// ─── 내 요청 목록 탭 ─────────────────────────────────────────

class _MyRequestsTab extends ConsumerStatefulWidget {
  const _MyRequestsTab();

  @override
  ConsumerState<_MyRequestsTab> createState() => _MyRequestsTabState();
}

class _MyRequestsTabState extends ConsumerState<_MyRequestsTab> {
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    // 관리자 승인/반려는 앱이 알 수 없는 별도 채널(관리자웹)에서 일어나므로,
    // 푸시 알림 없이도 검토중 상태가 반영되도록 화면이 떠 있는 동안 주기적으로 재조회한다.
    _pollTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      ref.invalidate(myChangeRequestsProvider);
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final requestsAsync = ref.watch(myChangeRequestsProvider);

    // 승인/반려로 요청 상태가 PENDING에서 벗어나면 해당 근태 기록 자체가
    // 바뀐 것이므로, 홈 화면(오늘 근태)과 근태내역(월별) 화면도 함께 갱신한다.
    ref.listen(myChangeRequestsProvider, (previous, next) {
      final prevList = previous?.valueOrNull;
      final nextList = next.valueOrNull;
      if (prevList == null || nextList == null) return;

      final prevStatusById = {for (final r in prevList) r.id: r.status};
      final resolved = nextList.any((r) =>
          prevStatusById[r.id] == 'PENDING' && r.status != 'PENDING');
      if (resolved) {
        ref.read(todayAttendanceProvider.notifier).load();
        ref.invalidate(monthlyRecordsProvider);
      }
    });

    return requestsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48),
            const SizedBox(height: 8),
            Text(e.toString()),
            const SizedBox(height: 16),
            OutlinedButton(
              onPressed: () => ref.invalidate(myChangeRequestsProvider),
              child: const Text('다시 시도'),
            ),
          ],
        ),
      ),
      data: (requests) => RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myChangeRequestsProvider);
          await ref.read(myChangeRequestsProvider.future);
        },
        child: requests.isEmpty
            ? ListView(
                children: const [
                  Padding(
                    padding: EdgeInsets.all(48),
                    child: Center(child: Text('제출한 수정 요청이 없습니다.')),
                  ),
                ],
              )
            : ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: requests.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (_, i) => _RequestTile(request: requests[i]),
              ),
      ),
    );
  }
}

class _RequestTile extends StatelessWidget {
  const _RequestTile({required this.request});

  final ChangeRequest request;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dateFmt = DateFormat('M/d', 'ko');
    final createdFmt = DateFormat('yyyy/M/d HH:mm');

    Color statusColor;
    // AttendanceStatusExt와 ChangeRequestStatusExt가 둘 다 String에
    // colorKey를 정의하므로 명시적으로 지정해 모호함을 해소한다.
    switch (ChangeRequestStatusExt(request.status).colorKey) {
      case 'primary':
        statusColor = theme.colorScheme.primary;
      case 'error':
        statusColor = theme.colorScheme.error;
      case 'tertiary':
        statusColor = theme.colorScheme.tertiary;
      default:
        statusColor = theme.colorScheme.outline;
    }

    final typeName = changeRequestTypes
        .firstWhere(
          (t) => t.$1 == request.requestType,
          orElse: () => (request.requestType, request.requestType),
        )
        .$2;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  dateFmt.format(DateTime.parse(request.workDate)),
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(width: 8),
                Text(typeName, style: theme.textTheme.bodyMedium),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: statusColor.withAlpha(30),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    ChangeRequestStatusExt(request.status).displayName,
                    style: TextStyle(
                      color: statusColor,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              request.reason,
              style: theme.textTheme.bodySmall,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            if (request.reviewerComment != null) ...[
              const SizedBox(height: 6),
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.comment_outlined,
                      size: 14,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        request.reviewerComment!,
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 4),
            Text(
              '제출: ${createdFmt.format(request.createdAt.toKst())}',
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
