import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../model/attendance_model.dart';
import '../provider/attendance_provider.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/utils/kst.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  late int _selectedYear;
  late int _selectedMonth;
  bool _calendarView = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _selectedYear = now.year;
    _selectedMonth = now.month;
  }

  void _previousMonth() {
    setState(() {
      if (_selectedMonth == 1) {
        _selectedYear--;
        _selectedMonth = 12;
      } else {
        _selectedMonth--;
      }
    });
  }

  void _nextMonth() {
    final now = DateTime.now();
    if (_selectedYear == now.year && _selectedMonth == now.month) return;
    setState(() {
      if (_selectedMonth == 12) {
        _selectedYear++;
        _selectedMonth = 1;
      } else {
        _selectedMonth++;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final recordsAsync = ref.watch(
      monthlyRecordsProvider((_selectedYear, _selectedMonth)),
    );
    final now = DateTime.now();
    final isCurrentMonth =
        _selectedYear == now.year && _selectedMonth == now.month;

    return Scaffold(
      drawer: const AppMenuDrawer(),
      appBar: AppBar(
        leading: const AppMenuLeadingButton(),
        title: const Text('근태 내역'),
        actions: [
          IconButton(
            icon: Icon(_calendarView ? Icons.view_list : Icons.calendar_month),
            tooltip: _calendarView ? '목록 보기' : '달력 보기',
            onPressed: () => setState(() => _calendarView = !_calendarView),
          ),
        ],
      ),
      body: Column(
        children: [
          // 월 선택기
          Container(
            color: theme.colorScheme.surfaceContainerHighest,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                IconButton(
                  onPressed: _previousMonth,
                  icon: const Icon(Icons.chevron_left),
                ),
                Text(
                  '$_selectedYear년 $_selectedMonth월',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                IconButton(
                  onPressed: isCurrentMonth ? null : _nextMonth,
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
          ),

          // 근태 목록
          Expanded(
            child: recordsAsync.when(
              loading: () =>
                  const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline, size: 48),
                    const SizedBox(height: 8),
                    Text(e.toString()),
                    const SizedBox(height: 16),
                    OutlinedButton(
                      onPressed: () => ref.invalidate(
                        monthlyRecordsProvider((_selectedYear, _selectedMonth)),
                      ),
                      child: const Text('다시 시도'),
                    ),
                  ],
                ),
              ),
              data: (records) => RefreshIndicator(
                onRefresh: () async {
                  final key = monthlyRecordsProvider((_selectedYear, _selectedMonth));
                  ref.invalidate(key);
                  await ref.read(key.future);
                },
                child: records.isEmpty
                    ? ListView(
                        children: const [
                          Padding(
                            padding: EdgeInsets.all(48),
                            child: Center(child: Text('해당 월 근태 기록이 없습니다.')),
                          ),
                        ],
                      )
                    : _calendarView
                        ? _CalendarGrid(
                            records: records,
                            year: _selectedYear,
                            month: _selectedMonth,
                          )
                        : _MonthlyTable(
                            records: records,
                            year: _selectedYear,
                            month: _selectedMonth,
                          ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// 날짜 | 상태 | 출근 | 퇴근 | 휴식 | 총 근무 표(출근부) 형태로 한 달 근태를 보여준다.
// 기록이 없는 날(주말·미체크 등)도 1일부터 말일까지 전부 행으로 채워서 "-"로 표시한다.
class _MonthlyTable extends StatelessWidget {
  const _MonthlyTable({
    required this.records,
    required this.year,
    required this.month,
  });

  final List<AttendanceRecord> records;
  final int year;
  final int month;

  static const _weekdayLabels = ['일', '월', '화', '수', '목', '금', '토'];

  static String _fmtDuration(int minutes) =>
      '${(minutes ~/ 60).toString().padLeft(2, '0')}시간'
      '${(minutes % 60).toString().padLeft(2, '0')}분';

  static String _statusLabel(AttendanceRecord record) => record.status.displayName;

  @override
  Widget build(BuildContext context) {
    final recordsByDay = <int, AttendanceRecord>{
      for (final r in records) DateTime.parse(r.workDate).day: r,
    };
    final daysInMonth = DateTime(year, month + 1, 0).day;
    final timeFmt = DateFormat('HH:mm');

    var totalWork = 0, totalBreak = 0, totalOvertime = 0, totalNight = 0;
    for (final r in records) {
      totalWork += r.workMinutes ?? 0;
      totalBreak += r.breakMinutes ?? 0;
      totalOvertime += r.overtimeMinutes ?? 0;
      totalNight += r.nightMinutes ?? 0;
    }

    return Column(
      children: [
        Card(
          margin: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 14),
            child: Column(
              children: [
                _SummaryLine(entries: [
                  ('합계', _fmtDuration(totalWork)),
                  ('휴식', _fmtDuration(totalBreak)),
                ]),
                const SizedBox(height: 6),
                _SummaryLine(entries: [
                  ('잔업', _fmtDuration(totalOvertime)),
                  ('심야', _fmtDuration(totalNight)),
                ]),
              ],
            ),
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            itemCount: daysInMonth,
            itemBuilder: (context, index) {
              final day = index + 1;
              final weekday = DateTime(year, month, day).weekday % 7; // 일=0..토=6
              final record = recordsByDay[day];
              return _DayCard(
                day: day,
                weekdayLabel: _weekdayLabels[weekday],
                isSunday: weekday == 0,
                isSaturday: weekday == 6,
                record: record,
                statusLabel: record != null ? _statusLabel(record) : null,
                timeFmt: timeFmt,
              );
            },
          ),
        ),
      ],
    );
  }
}

class _SummaryLine extends StatelessWidget {
  const _SummaryLine({required this.entries});

  final List<(String, String)> entries;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (final (label, value) in entries) ...[
          Text('$label ', style: theme.textTheme.bodyMedium),
          Text(value, style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(width: 16),
        ],
      ],
    );
  }
}

Color _statusColor(ThemeData theme, String colorKey) => switch (colorKey) {
      'primary' => theme.colorScheme.primary,
      'error' => theme.colorScheme.error,
      'secondary' => theme.colorScheme.secondary,
      'tertiary' => theme.colorScheme.tertiary,
      _ => theme.colorScheme.outline,
    };

class _DayCard extends StatelessWidget {
  const _DayCard({
    required this.day,
    required this.weekdayLabel,
    required this.isSunday,
    required this.isSaturday,
    required this.record,
    required this.statusLabel,
    required this.timeFmt,
  });

  final int day;
  final String weekdayLabel;
  final bool isSunday;
  final bool isSaturday;
  final AttendanceRecord? record;
  final String? statusLabel;
  final DateFormat timeFmt;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dateColor = isSunday
        ? theme.colorScheme.error
        : isSaturday
            ? theme.colorScheme.primary
            : theme.colorScheme.onSurface;
    final hasCheckIn = record?.checkInAt != null;
    final hasCheckOut = record?.checkOutAt != null;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  '$day일 ($weekdayLabel)',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: dateColor,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                if (statusLabel != null)
                  Text(
                    statusLabel!,
                    style: TextStyle(
                      color: record != null ? _statusColor(theme, record!.status.colorKey) : theme.colorScheme.outline,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: _FieldText(
                    label: '출근',
                    value: hasCheckIn ? timeFmt.format(record!.checkInAt!.toKst()) : '-',
                    valueColor: hasCheckIn ? theme.colorScheme.primary : theme.colorScheme.outline,
                  ),
                ),
                Expanded(
                  child: _FieldText(
                    label: '퇴근',
                    value: hasCheckOut ? timeFmt.format(record!.checkOutAt!.toKst()) : '-',
                    valueColor: hasCheckOut ? theme.colorScheme.primary : theme.colorScheme.outline,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: _FieldText(
                    label: '휴식',
                    value: record?.breakMinutes != null ? _MonthlyTable._fmtDuration(record!.breakMinutes!) : '-',
                  ),
                ),
                Expanded(
                  child: _FieldText(
                    label: '총 근무',
                    value: record?.workMinutes != null ? _MonthlyTable._fmtDuration(record!.workMinutes!) : '-',
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _FieldText extends StatelessWidget {
  const _FieldText({required this.label, required this.value, this.valueColor});

  final String label;
  final String value;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Text(
          '$label ',
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
        Text(
          value,
          style: theme.textTheme.bodyMedium?.copyWith(
            fontWeight: FontWeight.w600,
            color: valueColor,
          ),
        ),
      ],
    );
  }
}

class _CalendarGrid extends StatelessWidget {
  const _CalendarGrid({
    required this.records,
    required this.year,
    required this.month,
  });

  final List<AttendanceRecord> records;
  final int year;
  final int month;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final recordsByDay = <int, AttendanceRecord>{
      for (final r in records) DateTime.parse(r.workDate).day: r,
    };
    final firstDay = DateTime(year, month, 1);
    final daysInMonth = DateTime(year, month + 1, 0).day;
    // Flutter의 weekday는 월=1 ~ 일=7. 달력 첫 줄의 빈 칸 수를 계산한다 (일요일 시작).
    final leadingBlanks = firstDay.weekday % 7;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: const ['일', '월', '화', '수', '목', '금', '토']
              .map((d) => Expanded(
                    child: Center(
                      child: Text(d, style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ))
              .toList(),
        ),
        const SizedBox(height: 8),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 7,
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
          ),
          itemCount: leadingBlanks + daysInMonth,
          itemBuilder: (context, index) {
            if (index < leadingBlanks) return const SizedBox.shrink();
            final day = index - leadingBlanks + 1;
            final record = recordsByDay[day];
            Color? dotColor;
            if (record != null) {
              switch (record.status.colorKey) {
                case 'primary':
                  dotColor = theme.colorScheme.primary;
                case 'error':
                  dotColor = theme.colorScheme.error;
                case 'secondary':
                  dotColor = theme.colorScheme.secondary;
                default:
                  dotColor = theme.colorScheme.outline;
              }
            }
            return InkWell(
              borderRadius: BorderRadius.circular(8),
              onTap: record == null
                  ? null
                  : () => _showDayDetail(context, record),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text('$day', style: theme.textTheme.bodyMedium),
                  const SizedBox(height: 2),
                  if (dotColor != null)
                    Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(color: dotColor, shape: BoxShape.circle),
                    ),
                ],
              ),
            );
          },
        ),
      ],
    );
  }

  void _showDayDetail(BuildContext context, AttendanceRecord record) {
    final timeFmt = DateFormat('HH:mm');
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
                DateFormat('yyyy년 M월 d일 (E)', 'ko').format(DateTime.parse(record.workDate)),
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              _DetailRow(label: '근태 상태', value: record.status.displayName),
              _DetailRow(
                label: '출근',
                value: record.checkInAt != null ? timeFmt.format(record.checkInAt!.toKst()) : '-',
              ),
              _DetailRow(
                label: '퇴근',
                value: record.checkOutAt != null ? timeFmt.format(record.checkOutAt!.toKst()) : '-',
              ),
              if (record.workplaceName != null)
                _DetailRow(label: '근무지', value: record.workplaceName!),
              _DetailRow(
                label: '근무시간',
                value: record.workMinutes != null
                    ? '${record.workMinutes! ~/ 60}시간 ${record.workMinutes! % 60}분'
                    : '-',
              ),
              if (record.isLate == true || record.isEarlyLeave == true)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    [
                      if (record.isLate == true) '지각',
                      if (record.isEarlyLeave == true) '조퇴',
                    ].join(' · '),
                    style: TextStyle(color: theme.colorScheme.error, fontWeight: FontWeight.bold),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(label, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Text(value, style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

