import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:table_calendar/table_calendar.dart';
import '../model/calendar_event_model.dart';
import '../model/holiday_model.dart';
import '../provider/calendar_event_provider.dart';
import '../widget/event_form_dialog.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/storage/secure_storage.dart';
import '../../../core/utils/kst.dart';

// 등록/수정/삭제는 권한레벨이 이 값에 속한 계정만 전체(ALL) 일정을 다룰 수 있다.
// 서버(CalendarEventService)도 동일 기준으로 검증하므로 이 목록은 화면 표시(UX)용일 뿐이다.
const _scheduleAdminLevels = {'SYSADMIN', 'HRADMIN', 'PRESIDENT'};

bool _isSameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;

Color _categoryColor(CalendarEventCategory c) => switch (c) {
      CalendarEventCategory.meeting => Colors.blue,
      CalendarEventCategory.event => Colors.green,
      CalendarEventCategory.notice => Colors.orange,
      CalendarEventCategory.etc => Colors.blueGrey,
    };

class SchedulesScreen extends ConsumerStatefulWidget {
  const SchedulesScreen({super.key});

  @override
  ConsumerState<SchedulesScreen> createState() => _SchedulesScreenState();
}

class _SchedulesScreenState extends ConsumerState<SchedulesScreen> {
  DateTime _focusedDay = DateTime.now();
  DateTime _selectedDay = DateTime.now();
  bool _loadingUser = true;
  bool _isScheduleAdmin = false;
  int? _userId;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    final info = await SecureStorage.getUserInfo();
    final level = info['level'];
    if (!mounted) return;
    setState(() {
      _isScheduleAdmin = level != null && _scheduleAdminLevels.contains(level);
      _userId = int.tryParse(info['userId'] ?? '');
      _loadingUser = false;
    });
  }

  bool _canManage(CalendarEvent e) {
    if (_isScheduleAdmin) return true;
    return e.visibility == CalendarEventVisibility.personal && e.targetUserId == _userId;
  }

  List<CalendarEvent> _eventsForDay(List<CalendarEvent> events, DateTime day) {
    final dayStart = DateTime(day.year, day.month, day.day);
    final dayEnd = dayStart.add(const Duration(days: 1));
    return events.where((e) {
      final s = e.startAt.toKst();
      final en = e.endAt.toKst();
      return s.isBefore(dayEnd) && en.isAfter(dayStart);
    }).toList();
  }

  List<Holiday> _holidaysForDay(List<Holiday> holidays, DateTime day) =>
      holidays.where((h) => _isSameDay(h.holidayDate, day) && h.holidayType != HolidayType.weekend).toList();

  @override
  Widget build(BuildContext context) {
    final eventsAsync = ref.watch(calendarEventListProvider((_focusedDay.year, _focusedDay.month)));
    final holidaysAsync = ref.watch(holidayListProvider);
    final holidays = holidaysAsync.valueOrNull ?? [];

    return Scaffold(
      drawer: const AppMenuDrawer(),
      appBar: AppBar(
        leading: const AppMenuLeadingButton(),
        title: const Text('일정관리'),
      ),
      body: eventsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text('일정을 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
          ),
        ),
        data: (events) => Column(
          children: [
            TableCalendar<Object>(
              locale: 'ko_KR',
              firstDay: DateTime.utc(2015, 1, 1),
              lastDay: DateTime.utc(2035, 12, 31),
              focusedDay: _focusedDay,
              selectedDayPredicate: (day) => _isSameDay(day, _selectedDay),
              calendarFormat: CalendarFormat.month,
              availableCalendarFormats: const {CalendarFormat.month: '월'},
              eventLoader: (day) => [
                ..._eventsForDay(events, day),
                ..._holidaysForDay(holidays, day),
              ],
              onDaySelected: (selected, focused) {
                setState(() {
                  _selectedDay = selected;
                  _focusedDay = focused;
                });
              },
              onPageChanged: (focused) => setState(() => _focusedDay = focused),
              daysOfWeekStyle: const DaysOfWeekStyle(),
              calendarBuilders: CalendarBuilders(
                dowBuilder: (context, day) {
                  Color? color;
                  if (day.weekday == DateTime.sunday) color = Colors.red;
                  if (day.weekday == DateTime.saturday) color = Colors.blue;
                  return Center(
                    child: Text(
                      DateFormat.E('ko').format(day),
                      style: TextStyle(color: color, fontWeight: FontWeight.w600),
                    ),
                  );
                },
                defaultBuilder: (context, day, focusedDay) {
                  final hasHoliday = _holidaysForDay(holidays, day).isNotEmpty;
                  Color? color;
                  if (hasHoliday || day.weekday == DateTime.sunday) {
                    color = Colors.red;
                  } else if (day.weekday == DateTime.saturday) {
                    color = Colors.blue;
                  }
                  if (color == null) return null;
                  return Center(child: Text('${day.day}', style: TextStyle(color: color)));
                },
                markerBuilder: (context, day, dayEvents) {
                  if (dayEvents.isEmpty) return null;
                  return Positioned(
                    bottom: 4,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: dayEvents.take(4).map((e) {
                        final color = e is Holiday ? Colors.red : _categoryColor((e as CalendarEvent).category);
                        return Container(
                          width: 6,
                          height: 6,
                          margin: const EdgeInsets.symmetric(horizontal: 1),
                          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
                        );
                      }).toList(),
                    ),
                  );
                },
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _DayDetailList(
                day: _selectedDay,
                events: _eventsForDay(events, _selectedDay),
                holidays: _holidaysForDay(holidays, _selectedDay),
                canManage: _canManage,
                onTapEvent: (e) => _openEventForm(event: e),
                onTapHoliday: (h) => ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('${h.name} (${h.holidayType.label})')),
                ),
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: _loadingUser
          ? null
          : FloatingActionButton(
              onPressed: () => _openEventForm(),
              tooltip: '일정 등록',
              child: const Icon(Icons.add),
            ),
    );
  }

  Future<void> _openEventForm({CalendarEvent? event}) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => EventFormDialog(
        event: event,
        initialDay: _selectedDay,
        isScheduleAdmin: _isScheduleAdmin,
        canManage: event == null ? true : _canManage(event),
      ),
    );
    if (changed == true) {
      ref.invalidate(calendarEventListProvider((_focusedDay.year, _focusedDay.month)));
    }
  }
}

class _DayDetailList extends StatelessWidget {
  const _DayDetailList({
    required this.day,
    required this.events,
    required this.holidays,
    required this.canManage,
    required this.onTapEvent,
    required this.onTapHoliday,
  });

  final DateTime day;
  final List<CalendarEvent> events;
  final List<Holiday> holidays;
  final bool Function(CalendarEvent) canManage;
  final void Function(CalendarEvent) onTapEvent;
  final void Function(Holiday) onTapHoliday;

  @override
  Widget build(BuildContext context) {
    final fmt = DateFormat('M월 d일 (E)', 'ko');
    final timeFmt = DateFormat('HH:mm');

    if (events.isEmpty && holidays.isEmpty) {
      return Center(
        child: Text('${fmt.format(day)}\n등록된 일정이 없습니다.', textAlign: TextAlign.center),
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(fmt.format(day), style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 12),
        ...holidays.map((h) => Card(
              color: Colors.red.withValues(alpha: 0.06),
              child: ListTile(
                leading: const Icon(Icons.event_busy, color: Colors.red),
                title: Text(h.name),
                subtitle: Text(h.holidayType.label),
                onTap: () => onTapHoliday(h),
              ),
            )),
        ...events.map((e) {
          final editable = canManage(e);
          final isPersonal = e.visibility == CalendarEventVisibility.personal;
          return Card(
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: _categoryColor(e.category),
                radius: 8,
                child: const SizedBox.shrink(),
              ),
              title: Text(isPersonal ? '[개인] ${e.title}' : e.title),
              subtitle: Text(
                '${e.allDay ? '종일' : '${timeFmt.format(e.startAt.toKst())} ~ ${timeFmt.format(e.endAt.toKst())}'}'
                '${e.location != null && e.location!.isNotEmpty ? ' · ${e.location}' : ''}',
              ),
              trailing: editable ? const Icon(Icons.chevron_right) : const Icon(Icons.lock_outline, size: 18),
              onTap: () => onTapEvent(e),
            ),
          );
        }),
      ],
    );
  }
}
