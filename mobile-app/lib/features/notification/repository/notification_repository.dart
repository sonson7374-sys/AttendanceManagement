import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/notification_model.dart';

class NotificationRepository {
  final Dio _dio = DioClient.instance;

  Future<List<AppNotification>> getMyNotifications({
    int page = 0,
    int size = 30,
  }) async {
    try {
      final response = await _dio.get(
        ApiConstants.notifications,
        queryParameters: {'page': page, 'size': size, 'sort': 'createdAt,desc'},
      );
      final content = response.data['data']['content'] as List<dynamic>;
      return content
          .map((e) => AppNotification.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<int> getUnreadCount() async {
    try {
      final response = await _dio.get(ApiConstants.notificationsUnreadCount);
      return response.data['data'] as int;
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> markRead(int id) async {
    try {
      await _dio.patch('${ApiConstants.notifications}/$id/read');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> markAllRead() async {
    try {
      await _dio.patch('${ApiConstants.notifications}/read-all');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
