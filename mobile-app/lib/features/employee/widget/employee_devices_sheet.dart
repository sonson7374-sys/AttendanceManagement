import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/employee_model.dart';
import '../provider/employee_provider.dart';
import '../../../core/network/dio_client.dart';

/// 관리자웹 DeviceModal과 동일. 직원의 등록 단말기 목록을 보고 해제할 수 있다.
class EmployeeDevicesSheet extends ConsumerWidget {
  const EmployeeDevicesSheet({super.key, required this.employee});

  final Employee employee;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final devicesAsync = ref.watch(userDevicesProvider(employee.id));

    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      minChildSize: 0.3,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) {
        return Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('단말기 관리', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              Text('${employee.name} (${employee.employeeNumber})', style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 16),
              Expanded(
                child: devicesAsync.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Center(child: Text('단말기 목록을 불러오지 못했습니다.\n$e')),
                  data: (devices) {
                    if (devices.isEmpty) {
                      return const Center(child: Text('등록된 단말기가 없습니다.'));
                    }
                    return ListView.builder(
                      controller: scrollController,
                      itemCount: devices.length,
                      itemBuilder: (context, i) {
                        final d = devices[i];
                        return Card(
                          child: ListTile(
                            title: Text(d.deviceName?.isNotEmpty == true ? d.deviceName! : d.deviceId),
                            subtitle: Text(
                              '${d.devicePlatform ?? '-'} · ${d.active ? '활성' : '해제됨'}'
                              '${d.lastSeenAt != null ? ' · 최근 사용 ${d.lastSeenAt}' : ''}',
                            ),
                            trailing: d.active
                                ? TextButton(
                                    onPressed: () => _revoke(context, ref, d.deviceId),
                                    child: const Text('해제', style: TextStyle(color: Colors.red)),
                                  )
                                : null,
                          ),
                        );
                      },
                    );
                  },
                ),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('닫기'),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _revoke(BuildContext context, WidgetRef ref, String deviceId) async {
    try {
      await ref.read(employeeRepositoryProvider).revokeDevice(employee.id, deviceId);
      ref.invalidate(userDevicesProvider(employee.id));
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }
}
