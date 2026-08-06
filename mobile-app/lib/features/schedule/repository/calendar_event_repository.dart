import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/calendar_event_model.dart';

class CalendarEventRepository {
  final Dio _dio = DioClient.instance;

  /// 조회는 인증만 되면 누구나 가능하다(전체 일정 + 본인 개인 일정만 서버에서 필터링돼 내려온다).
  Future<List<CalendarEvent>> getEvents(DateTime from, DateTime to) async {
    try {
      final response = await _dio.get(
        ApiConstants.calendarEvents,
        queryParameters: {
          'from': _dateStr(from),
          'to': _dateStr(to),
        },
      );
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => CalendarEvent.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 등록/수정/삭제는 서버(CalendarEventService)가 권한레벨(SYSADMIN/HRADMIN/PRESIDENT)로
  /// 최종 검증한다. 여기서는 클라이언트 UX용 사전 가드만 두고 서버 응답을 신뢰한다.
  Future<CalendarEvent> create(CalendarEventPayload payload) async {
    try {
      final response = await _dio.post(ApiConstants.calendarEvents, data: payload.toJson());
      return CalendarEvent.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<CalendarEvent> update(int id, CalendarEventPayload payload) async {
    try {
      final response = await _dio.put('${ApiConstants.calendarEvents}/$id', data: payload.toJson());
      return CalendarEvent.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> delete(int id) async {
    try {
      await _dio.delete('${ApiConstants.calendarEvents}/$id');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  String _dateStr(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}
