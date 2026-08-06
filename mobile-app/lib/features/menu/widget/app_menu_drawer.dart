import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/storage/secure_storage.dart';
import '../model/app_menu_item.dart';
import '../provider/menu_permission_provider.dart';

/// 관리자웹 사이드바(Layout.tsx)와 동일한 메뉴 목록을 로그인 계정의 권한레벨에 맞춰
/// 필터링해 보여주는 드로어. 아직 모바일 화면이 없는 항목은 탭하면 준비 중 안내만 표시한다.
class AppMenuDrawer extends ConsumerWidget {
  const AppMenuDrawer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overridesAsync = ref.watch(menuPermissionListProvider);
    final currentLocation = GoRouterState.of(context).matchedLocation;

    return Drawer(
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const _DrawerHeader(),
            const Divider(height: 1),
            Expanded(
              child: overridesAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text('메뉴를 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
                  ),
                ),
                data: (overrides) {
                  final visibleItems = kAppMenuItems
                      .where((item) => isMenuVisible(overrides, item.menuKey))
                      .toList();
                  return ListView(
                    padding: EdgeInsets.zero,
                    children: visibleItems.map((item) {
                      final isActive = item.route != null && item.route == currentLocation;
                      return ListTile(
                        leading: Icon(item.icon),
                        title: Text(item.label),
                        selected: isActive,
                        onTap: () => _handleTap(context, item),
                      );
                    }).toList(),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _handleTap(BuildContext context, AppMenuItem item) {
    final messenger = ScaffoldMessenger.of(context);
    Navigator.of(context).pop();
    if (item.route != null) {
      context.go(item.route!);
    } else {
      messenger.showSnackBar(
        SnackBar(content: Text('${item.label}은(는) 모바일 앱에서 준비 중입니다.')),
      );
    }
  }
}

class _DrawerHeader extends StatefulWidget {
  const _DrawerHeader();

  @override
  State<_DrawerHeader> createState() => _DrawerHeaderState();
}

class _DrawerHeaderState extends State<_DrawerHeader> {
  String? _name;
  String? _role;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final info = await SecureStorage.getUserInfo();
    if (mounted) {
      setState(() {
        _name = info['name'];
        _role = info['role'];
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
      color: theme.colorScheme.primaryContainer,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _name ?? '',
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          if (_role != null) ...[
            const SizedBox(height: 4),
            Text(_role!, style: theme.textTheme.bodySmall),
          ],
        ],
      ),
    );
  }
}
