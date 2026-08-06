import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/change_request_model.dart';

class ChangeRequestRepository {
  final Dio _dio = DioClient.instance;

  Future<ChangeRequest> submit(ChangeRequestSubmit request) async {
    try {
      final response = await _dio.post(
        ApiConstants.changeRequests,
        data: request.toJson(),
      );
      return ChangeRequest.fromJson(
        response.data['data'] as Map<String, dynamic>,
      );
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<List<ChangeRequest>> getMyRequests({int page = 0}) async {
    try {
      final response = await _dio.get(
        ApiConstants.myChangeRequests,
        queryParameters: {'page': page, 'size': 20, 'sort': 'createdAt,desc'},
      );
      final list = response.data['data'] is List
          ? response.data['data'] as List<dynamic>
          : (response.data['data']['content'] as List<dynamic>);
      return list
          .map((e) => ChangeRequest.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
