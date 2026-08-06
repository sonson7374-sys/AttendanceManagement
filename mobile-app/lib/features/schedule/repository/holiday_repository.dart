import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/holiday_model.dart';

class HolidayRepository {
  final Dio _dio = DioClient.instance;

  /// 등록된 휴일은 기간과 무관하게 전체를 한 번만 불러와 달력에 표시한다(관리자웹과 동일).
  Future<List<Holiday>> getHolidays() async {
    try {
      final response = await _dio.get(ApiConstants.holidays);
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => Holiday.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
