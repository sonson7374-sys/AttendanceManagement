import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/attendance_model.dart';

const _uuid = Uuid();

class AttendanceRepository {
  final Dio _dio = DioClient.instance;

  Future<TodayAttendance> getToday() async {
    try {
      final response = await _dio.get(ApiConstants.today);
      return TodayAttendance.fromJson(
        response.data['data'] as Map<String, dynamic>,
      );
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<TodayAttendance> checkIn(CheckInRequest request) async {
    try {
      await _dio.post(
        ApiConstants.checkIn,
        data: request.toJson(),
        options: Options(headers: {'Idempotency-Key': _uuid.v4()}),
      );
      // check-in 응답(AttendanceResponse)은 today 응답과 필드 구성이 달라
      // (workDate 없음 등) 최신 상태를 다시 조회해서 반환한다.
      return getToday();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<TodayAttendance> checkOut(CheckOutRequest request) async {
    try {
      await _dio.post(
        ApiConstants.checkOut,
        data: request.toJson(),
        options: Options(headers: {'Idempotency-Key': _uuid.v4()}),
      );
      return getToday();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<TodayAttendance> startBreak() async {
    try {
      await _dio.post(ApiConstants.breakStart);
      return getToday();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<TodayAttendance> endBreak() async {
    try {
      await _dio.post(ApiConstants.breakEnd);
      return getToday();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<List<AttendanceRecord>> getRecords({
    required int year,
    required int month,
  }) async {
    try {
      final from = DateTime(year, month, 1);
      final to = DateTime(year, month + 1, 0); // 해당 월 마지막 날
      final response = await _dio.get(
        ApiConstants.records,
        queryParameters: {
          'from': _isoDate(from),
          'to': _isoDate(to),
        },
      );
      final list = response.data['data'] as List<dynamic>;
      return list
          .map((e) => AttendanceRecord.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  String _isoDate(DateTime date) =>
      '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';
}
