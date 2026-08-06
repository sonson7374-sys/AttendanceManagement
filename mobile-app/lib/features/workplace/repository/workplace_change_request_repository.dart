import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/workplace_change_request_model.dart';

class WorkplaceChangeRequestRepository {
  final Dio _dio = DioClient.instance;

  Future<List<WorkplaceChangeRequest>> getMyRequests() async {
    try {
      final response = await _dio.get('${ApiConstants.workplaceChangeRequests}/my');
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkplaceChangeRequest.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<WorkplaceChangeRequest> submit(WorkplaceChangeRequestPayload payload) async {
    try {
      final response = await _dio.post(ApiConstants.workplaceChangeRequests, data: payload.toJson());
      return WorkplaceChangeRequest.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
