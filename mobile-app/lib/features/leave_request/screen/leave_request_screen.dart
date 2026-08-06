import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../model/leave_request_model.dart';
import '../provider/leave_request_provider.dart';
import '../../change_request/model/change_request_model.dart' show ChangeRequestStatusExt;
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/utils/kst.dart';

class LeaveRequestScreen extends ConsumerStatefulWidget {
  const LeaveRequestScreen({super.key});

  @override
  ConsumerState<LeaveRequestScreen> createState() => _LeaveRequestScreenState();
}

class _LeaveRequestScreenState extends ConsumerState<LeaveRequestScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
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
        title: const Text('휴가·외근·출장·재택 신청'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: '내 신청 정보'),
            Tab(text: '새 신청'),
            Tab(text: '내 신청 목록'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          const _CalendarTab(),
          _SubmitTab(onSubmitted: () => _tabController.animateTo(2)),
          const _MyRequestsTab(),
        ],
      ),
    );
  }
}

// ─── 캘린더 탭 ────────────────────────────────────────────────
// 로그인한 본인의 휴가·외근·출장·재택 신청을 월별 달력에 표기한다. 상태(검토 중/승인/반려)에
// 따라 배지 색을 다르게 보여주고, 날짜를 누르면 그날 걸린 신청 전체를 시트로 보여준다.

class _CalendarTab extends ConsumerStatefulWidget {
  const _CalendarTab();

  @override
  ConsumerState<_CalendarTab> createState() => _CalendarTabState();
}

class _CalendarTabState extends ConsumerState<_CalendarTab> {
  static const _weekdayLabels = ['일', '월', '화', '수', '목', '금', '토'];
  static const _maxBadgesPerCell = 2;

  late int _year;
  late int _month;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _year = now.year;
    _month = now.month;
  }

  void _previousMonth() {
    setState(() {
      if (_month == 1) {
        _year--;
        _month = 12;
      } else {
        _month--;
      }
    });
  }

  void _nextMonth() {
    setState(() {
      if (_month == 12) {
        _year++;
        _month = 1;
      } else {
        _month++;
      }
    });
  }

  String _kstDateStr(DateTime dt) => DateFormat('yyyy-MM-dd').format(dt.toKst());

  Color _statusColor(ThemeData theme, String status) => switch (ChangeRequestStatusExt(status).colorKey) {
        'primary' => theme.colorScheme.primary,
        'error' => theme.colorScheme.error,
        'tertiary' => theme.colorScheme.tertiary,
        _ => theme.colorScheme.outline,
      };

  void _showDayDetail(BuildContext context, int day, List<LeaveRequestItem> requests) {
    final fmt = DateFormat('M/d HH:mm', 'ko');
    showModalBottomSheet(
      context: context,
      builder: (ctx) {
        final theme = Theme.of(ctx);
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '$_year년 $_month월 $day일',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              ...requests.map((r) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Text(
                              leaveRequestTypeLabel(r.requestType),
                              style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: _statusColor(theme, r.status).withAlpha(30),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                ChangeRequestStatusExt(r.status).displayName,
                                style: TextStyle(color: _statusColor(theme, r.status), fontSize: 11, fontWeight: FontWeight.w600),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${fmt.format(r.startAt.toKst())} ~ ${fmt.format(r.endAt.toKst())}',
                          style: theme.textTheme.bodySmall,
                        ),
                        if (r.reason.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(r.reason, style: theme.textTheme.bodySmall),
                        ],
                      ],
                    ),
                  )),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final requestsAsync = ref.watch(myLeaveRequestsProvider);

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
              onPressed: () => ref.invalidate(myLeaveRequestsProvider),
              child: const Text('다시 시도'),
            ),
          ],
        ),
      ),
      data: (requests) {
        final daysInMonth = DateTime(_year, _month + 1, 0).day;
        final leadingBlanks = DateTime(_year, _month, 1).weekday % 7; // 일=0..토=6
        final monthPrefix = '$_year-${_month.toString().padLeft(2, '0')}';

        List<LeaveRequestItem> requestsForDay(int day) {
          final dateStr = '$monthPrefix-${day.toString().padLeft(2, '0')}';
          return requests.where((r) {
            final start = _kstDateStr(r.startAt);
            final end = _kstDateStr(r.endAt);
            return start.compareTo(dateStr) <= 0 && end.compareTo(dateStr) >= 0;
          }).toList();
        }

        final cells = <int?>[
          ...List<int?>.filled(leadingBlanks, null),
          ...List.generate(daysInMonth, (i) => i + 1),
        ];

        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  IconButton(onPressed: _previousMonth, icon: const Icon(Icons.chevron_left)),
                  Text(
                    '$_year년 $_month월',
                    style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  IconButton(onPressed: _nextMonth, icon: const Icon(Icons.chevron_right)),
                ],
              ),
            ),
            Row(
              children: _weekdayLabels
                  .map((d) => Expanded(
                        child: Center(
                          child: Text(d, style: theme.textTheme.labelSmall?.copyWith(fontWeight: FontWeight.bold)),
                        ),
                      ))
                  .toList(),
            ),
            const SizedBox(height: 4),
            Expanded(
              child: GridView.builder(
                padding: const EdgeInsets.fromLTRB(6, 0, 6, 12),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 7,
                  mainAxisSpacing: 4,
                  crossAxisSpacing: 4,
                  childAspectRatio: 0.62,
                ),
                itemCount: cells.length,
                itemBuilder: (context, index) {
                  final day = cells[index];
                  if (day == null) return const SizedBox.shrink();
                  final dayRequests = requestsForDay(day);
                  final weekday = DateTime(_year, _month, day).weekday % 7;

                  return InkWell(
                    onTap: dayRequests.isEmpty ? null : () => _showDayDetail(context, day, dayRequests),
                    borderRadius: BorderRadius.circular(6),
                    child: Container(
                      padding: const EdgeInsets.all(3),
                      decoration: BoxDecoration(
                        border: Border.all(color: theme.colorScheme.outlineVariant.withAlpha(100)),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '$day',
                            style: theme.textTheme.labelSmall?.copyWith(
                              fontWeight: FontWeight.w600,
                              color: weekday == 0
                                  ? theme.colorScheme.error
                                  : weekday == 6
                                      ? theme.colorScheme.primary
                                      : theme.colorScheme.onSurface,
                            ),
                          ),
                          for (final r in dayRequests.take(_maxBadgesPerCell))
                            Container(
                              margin: const EdgeInsets.only(top: 2),
                              width: double.infinity,
                              padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 1),
                              decoration: BoxDecoration(
                                color: _statusColor(theme, r.status).withAlpha(35),
                                borderRadius: BorderRadius.circular(3),
                              ),
                              child: Text(
                                leaveRequestTypeLabel(r.requestType),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(fontSize: 9, color: _statusColor(theme, r.status), fontWeight: FontWeight.w600),
                              ),
                            ),
                          if (dayRequests.length > _maxBadgesPerCell)
                            Text(
                              '+${dayRequests.length - _maxBadgesPerCell}',
                              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.primary, fontSize: 9),
                            ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }
}

// ─── 새 신청 탭 ───────────────────────────────────────────────

class _SubmitTab extends ConsumerStatefulWidget {
  const _SubmitTab({required this.onSubmitted});

  final VoidCallback onSubmitted;

  @override
  ConsumerState<_SubmitTab> createState() => _SubmitTabState();
}

class _SubmitTabState extends ConsumerState<_SubmitTab> {
  final _formKey = GlobalKey<FormState>();
  final _reasonCtrl = TextEditingController();
  final _destinationCtrl = TextEditingController();
  final _latCtrl = TextEditingController();
  final _lngCtrl = TextEditingController();
  final _radiusCtrl = TextEditingController();
  final _visitPurposeCtrl = TextEditingController();
  final _clientNameCtrl = TextEditingController();

  String? _selectedType;
  DateTime? _startAt;
  DateTime? _endAt;
  DateTime? _expectedReturnAt;

  @override
  void dispose() {
    _reasonCtrl.dispose();
    _destinationCtrl.dispose();
    _latCtrl.dispose();
    _lngCtrl.dispose();
    _radiusCtrl.dispose();
    _visitPurposeCtrl.dispose();
    _clientNameCtrl.dispose();
    super.dispose();
  }

  bool get _needsDestinationInfo =>
      _selectedType != null && isOutsideWorkType(_selectedType!);

  Future<DateTime?> _pickDateTime(DateTime? initial) async {
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      initialDate: initial ?? now,
      firstDate: now.subtract(const Duration(days: 365)),
      lastDate: now.add(const Duration(days: 365)),
    );
    if (date == null || !mounted) return null;
    final time = await showTimePicker(
      context: context,
      initialTime: initial != null
          ? TimeOfDay.fromDateTime(initial)
          : TimeOfDay.now(),
    );
    if (time == null) return null;
    return DateTime(date.year, date.month, date.day, time.hour, time.minute);
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedType == null) {
      _showError('신청 유형을 선택해주세요.');
      return;
    }
    if (_startAt == null || _endAt == null) {
      _showError('시작·종료 일시를 선택해주세요.');
      return;
    }
    if (_endAt!.isBefore(_startAt!)) {
      _showError('종료 일시는 시작 일시보다 늦어야 합니다.');
      return;
    }

    final success = await ref.read(submitLeaveRequestProvider.notifier).submit(
          LeaveRequestSubmit(
            requestType: _selectedType!,
            startAt: _startAt!,
            endAt: _endAt!,
            reason: _reasonCtrl.text.trim(),
            destinationAddress: _needsDestinationInfo && _destinationCtrl.text.trim().isNotEmpty
                ? _destinationCtrl.text.trim()
                : null,
            destinationLatitude: _needsDestinationInfo ? double.tryParse(_latCtrl.text) : null,
            destinationLongitude: _needsDestinationInfo ? double.tryParse(_lngCtrl.text) : null,
            tempRadiusMeters: _needsDestinationInfo ? int.tryParse(_radiusCtrl.text) : null,
            visitPurpose: _needsDestinationInfo && _visitPurposeCtrl.text.trim().isNotEmpty
                ? _visitPurposeCtrl.text.trim()
                : null,
            clientName: _needsDestinationInfo && _clientNameCtrl.text.trim().isNotEmpty
                ? _clientNameCtrl.text.trim()
                : null,
            expectedReturnAt: _needsDestinationInfo ? _expectedReturnAt : null,
          ),
        );

    if (!mounted) return;

    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('신청이 제출되었습니다.')),
      );
      ref.invalidate(myLeaveRequestsProvider);
      _reset();
      widget.onSubmitted();
    } else {
      final error = ref.read(submitLeaveRequestProvider).error;
      _showError(error is ApiException ? error.message : '신청 제출 실패');
    }
  }

  void _reset() {
    _formKey.currentState?.reset();
    _reasonCtrl.clear();
    _destinationCtrl.clear();
    _latCtrl.clear();
    _lngCtrl.clear();
    _radiusCtrl.clear();
    _visitPurposeCtrl.clear();
    _clientNameCtrl.clear();
    setState(() {
      _selectedType = null;
      _startAt = null;
      _endAt = null;
      _expectedReturnAt = null;
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

  String _fmt(DateTime? dt) =>
      dt != null ? DateFormat('yyyy/M/d HH:mm').format(dt) : '선택';

  @override
  Widget build(BuildContext context) {
    final isLoading = ref.watch(submitLeaveRequestProvider).isLoading;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('신청 유형', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: _selectedType,
              isExpanded: true,
              hint: const Text('유형 선택'),
              items: leaveRequestTypes
                  .map((t) => DropdownMenuItem(value: t.$1, child: Text(t.$2)))
                  .toList(),
              onChanged: (v) => setState(() => _selectedType = v),
              validator: (v) => v == null ? '신청 유형을 선택해주세요.' : null,
            ),

            const SizedBox(height: 16),
            Text('기간', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      final picked = await _pickDateTime(_startAt);
                      if (picked != null) setState(() => _startAt = picked);
                    },
                    child: Text('시작: ${_fmt(_startAt)}'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      final picked = await _pickDateTime(_endAt ?? _startAt);
                      if (picked != null) setState(() => _endAt = picked);
                    },
                    child: Text('종료: ${_fmt(_endAt)}'),
                  ),
                ),
              ],
            ),

            if (_needsDestinationInfo) ...[
              const SizedBox(height: 16),
              Text('목적지 정보', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              TextFormField(
                controller: _destinationCtrl,
                decoration: const InputDecoration(
                  labelText: '목적지 주소',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _latCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                      decoration: const InputDecoration(labelText: '위도', border: OutlineInputBorder()),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      controller: _lngCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                      decoration: const InputDecoration(labelText: '경도', border: OutlineInputBorder()),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _radiusCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: '임시 허용 반경(m)',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _visitPurposeCtrl,
                decoration: const InputDecoration(labelText: '방문 목적', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _clientNameCtrl,
                decoration: const InputDecoration(labelText: '고객사명', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: () async {
                  final picked = await _pickDateTime(_expectedReturnAt ?? _endAt);
                  if (picked != null) setState(() => _expectedReturnAt = picked);
                },
                child: Text('예정 복귀시간: ${_fmt(_expectedReturnAt)}'),
              ),
            ],

            const SizedBox(height: 16),
            Text('사유', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            TextFormField(
              controller: _reasonCtrl,
              maxLines: 4,
              maxLength: 500,
              decoration: const InputDecoration(
                hintText: '신청 사유를 상세히 입력해주세요.',
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
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              child: isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('신청 제출'),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── 내 신청 목록 탭 ─────────────────────────────────────────

class _MyRequestsTab extends ConsumerWidget {
  const _MyRequestsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final requestsAsync = ref.watch(myLeaveRequestsProvider);

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
              onPressed: () => ref.invalidate(myLeaveRequestsProvider),
              child: const Text('다시 시도'),
            ),
          ],
        ),
      ),
      data: (requests) {
        if (requests.isEmpty) {
          return const Center(child: Text('제출한 신청이 없습니다.'));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: requests.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (_, i) => _RequestTile(request: requests[i]),
        );
      },
    );
  }
}

class _RequestTile extends StatelessWidget {
  const _RequestTile({required this.request});

  final LeaveRequestItem request;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fmt = DateFormat('M/d HH:mm', 'ko');
    final createdFmt = DateFormat('yyyy/M/d HH:mm');

    Color statusColor;
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

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  leaveRequestTypeLabel(request.requestType),
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: statusColor.withAlpha(30),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    ChangeRequestStatusExt(request.status).displayName,
                    style: TextStyle(color: statusColor, fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              '${fmt.format(request.startAt.toKst())} ~ ${fmt.format(request.endAt.toKst())}',
              style: theme.textTheme.bodySmall,
            ),
            if (request.destinationAddress != null) ...[
              const SizedBox(height: 4),
              Text('목적지: ${request.destinationAddress}', style: theme.textTheme.bodySmall),
            ],
            if (request.clientName != null) ...[
              const SizedBox(height: 2),
              Text('고객사: ${request.clientName}', style: theme.textTheme.bodySmall),
            ],
            const SizedBox(height: 6),
            Text(
              request.reason,
              style: theme.textTheme.bodySmall,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 4),
            Text(
              '제출: ${createdFmt.format(request.createdAt.toKst())}',
              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}
