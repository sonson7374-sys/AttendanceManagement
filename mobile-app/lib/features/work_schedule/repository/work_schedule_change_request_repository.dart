import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/work_schedule_change_request_model.dart';

class WorkScheduleChangeRequestRepository {
  final Dio _dio = DioClient.instance;

  Future<List<WorkScheduleChangeRequest>> getMyRequests() async {
    try {
      final response = await _dio.get('${ApiConstants.workScheduleChangeRequests}/my');
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkScheduleChangeRequest.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> submit(WorkScheduleChangeRequestPayload payload) async {
    try {
      await _dio.post(ApiConstants.workScheduleChangeRequests, data: payload.toJson());
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
