class MenuPermissionOverride {
  const MenuPermissionOverride({
    required this.menuKey,
    required this.actionKey,
    required this.enabled,
  });

  final String menuKey;
  final String actionKey;
  final bool enabled;

  factory MenuPermissionOverride.fromJson(Map<String, dynamic> json) =>
      MenuPermissionOverride(
        menuKey: json['menuKey'] as String,
        actionKey: json['actionKey'] as String,
        enabled: json['enabled'] as bool,
      );
}
