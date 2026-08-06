import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/notification_model.dart';
import '../repository/notification_repository.dart';

final notificationRepositoryProvider =
    Provider<NotificationRepository>((_) => NotificationRepository());

final notificationListProvider =
    FutureProvider.autoDispose<List<AppNotification>>((ref) {
  final repo = ref.read(notificationRepositoryProvider);
  return repo.getMyNotifications();
});

final unreadNotificationCountProvider = FutureProvider.autoDispose<int>((ref) {
  final repo = ref.read(notificationRepositoryProvider);
  return repo.getUnreadCount();
});
