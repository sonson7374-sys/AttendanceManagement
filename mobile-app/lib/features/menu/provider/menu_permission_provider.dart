import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/menu_permission_model.dart';
import '../repository/menu_permission_repository.dart';

final menuPermissionRepositoryProvider =
    Provider<MenuPermissionRepository>((_) => MenuPermissionRepository());

final menuPermissionListProvider =
    FutureProvider.autoDispose<List<MenuPermissionOverride>>((ref) {
  final repo = ref.read(menuPermissionRepositoryProvider);
  return repo.getMyMenuPermissions();
});

/// 관리자웹 usePermissions()의 isMenuVisible과 동일한 규칙: 목록에 없는
/// (menuKey, 'MENU') 조합은 기본적으로 표시(true)로 간주한다.
bool isMenuVisible(List<MenuPermissionOverride> overrides, String menuKey) =>
    isActionEnabled(overrides, menuKey, 'MENU');

/// 관리자웹 usePermissions()의 isActionEnabled와 동일한 규칙: 목록에 없는
/// (menuKey, actionKey) 조합은 기본적으로 활성화(true)로 간주한다.
bool isActionEnabled(List<MenuPermissionOverride> overrides, String menuKey, String actionKey) {
  for (final o in overrides) {
    if (o.menuKey == menuKey && o.actionKey == actionKey) return o.enabled;
  }
  return true;
}
