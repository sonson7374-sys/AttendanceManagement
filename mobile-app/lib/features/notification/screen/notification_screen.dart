import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../model/notification_model.dart';
import '../provider/notification_provider.dart';
import '../../../core/utils/kst.dart';

class NotificationScreen extends ConsumerWidget {
  const NotificationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notificationsAsync = ref.watch(notificationListProvider);
    final repo = ref.read(notificationRepositoryProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('알림'),
        actions: [
          TextButton(
            onPressed: () async {
              await repo.markAllRead();
              ref.invalidate(notificationListProvider);
              ref.invalidate(unreadNotificationCountProvider);
            },
            child: const Text('모두 읽음'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(notificationListProvider);
          await ref.read(notificationListProvider.future);
        },
        child: notificationsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            children: [
              Padding(
                padding: const EdgeInsets.all(32),
                child: Text('알림을 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
              ),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  children: const [
                    Padding(
                      padding: EdgeInsets.all(48),
                      child: Center(child: Text('알림이 없습니다.')),
                    ),
                  ],
                )
              : ListView.separated(
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final item = items[index];
                    return _NotificationTile(
                      notification: item,
                      onTap: () async {
                        if (!item.read) {
                          await repo.markRead(item.id);
                          ref.invalidate(notificationListProvider);
                          ref.invalidate(unreadNotificationCountProvider);
                        }
                      },
                    );
                  },
                ),
        ),
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.notification, required this.onTap});

  final AppNotification notification;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fmt = DateFormat('MM/dd HH:mm');

    return ListTile(
      onTap: onTap,
      leading: Icon(
        _iconFor(notification.type),
        color: notification.read
            ? theme.colorScheme.outline
            : theme.colorScheme.primary,
      ),
      title: Text(
        notification.title,
        style: TextStyle(
          fontWeight: notification.read ? FontWeight.normal : FontWeight.bold,
        ),
      ),
      subtitle: Text(notification.message),
      trailing: Text(
        fmt.format(notification.createdAt.toKst()),
        style: theme.textTheme.labelSmall,
      ),
    );
  }

  IconData _iconFor(String type) => switch (type) {
        'CHANGE_REQUEST_APPROVED' => Icons.check_circle_outline,
        'CHANGE_REQUEST_REJECTED' => Icons.cancel_outlined,
        'CHANGE_REQUEST_SUBMITTED' => Icons.send_outlined,
        'ATTENDANCE_CORRECTED' => Icons.edit_calendar_outlined,
        'ATTENDANCE_CLOSED' => Icons.lock_outline,
        'ATTENDANCE_REOPENED' => Icons.lock_open_outlined,
        _ => Icons.notifications_outlined,
      };
}
