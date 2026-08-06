import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/calendar_event_model.dart';
import '../model/holiday_model.dart';
import '../repository/calendar_event_repository.dart';
import '../repository/holiday_repository.dart';

final calendarEventRepositoryProvider =
    Provider<CalendarEventRepository>((_) => CalendarEventRepository());

final holidayRepositoryProvider = Provider<HolidayRepository>((_) => HolidayRepository());

/// (year, month) 단위로 해당 월 1일~말일 일정을 조회한다(관리자웹 SchedulesPage의 기본 조회 기간과 동일).
final calendarEventListProvider =
    FutureProvider.autoDispose.family<List<CalendarEvent>, (int, int)>((ref, ym) {
  final (year, month) = ym;
  final from = DateTime(year, month, 1);
  final to = DateTime(year, month + 1, 0);
  final repo = ref.read(calendarEventRepositoryProvider);
  return repo.getEvents(from, to);
});

/// 휴일은 기간과 무관하게 전체를 한 번만 불러온다.
final holidayListProvider = FutureProvider.autoDispose<List<Holiday>>((ref) {
  final repo = ref.read(holidayRepositoryProvider);
  return repo.getHolidays();
});
