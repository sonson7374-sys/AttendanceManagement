import 'package:flutter/material.dart';
import '../model/employee_model.dart';

/// 관리자웹 EmployeesPage 테이블의 한 행에 대응하는 카드.
/// 사번/이름을 탭하면 배정관리(근무지·근무제) 시트가 열리고, 하단에 권한별 관리 버튼을 노출한다.
class EmployeeCard extends StatelessWidget {
  const EmployeeCard({
    super.key,
    required this.employee,
    required this.canEdit,
    required this.canChangeRole,
    required this.canManageEmployment,
    this.onTap,
    this.onEdit,
    this.onToggleLock,
    this.onDevices,
    this.onResetPassword,
    this.onSetPassword,
    this.onResign,
  });

  final Employee employee;
  final bool canEdit;
  final bool canChangeRole;
  final bool canManageEmployment;
  final VoidCallback? onTap;
  final VoidCallback? onEdit;
  final VoidCallback? onToggleLock;
  final VoidCallback? onDevices;
  final VoidCallback? onResetPassword;
  final VoidCallback? onSetPassword;
  final VoidCallback? onResign;

  Color get _statusColor {
    switch (employee.status) {
      case UserStatus.active:
        return const Color(0xFF10B981);
      case UserStatus.inactive:
        return const Color(0xFF94A3B8);
      case UserStatus.locked:
        return const Color(0xFFEF4444);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${employee.name} (${employee.employeeNumber})',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                        ),
                        Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Text(employee.email, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
                    decoration: BoxDecoration(color: _statusColor.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(20)),
                    child: Text(
                      employee.status == UserStatus.inactive && employee.resignDate != null
                          ? '${employee.status.label}(${employee.resignDate})'
                          : employee.status.label,
                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: _statusColor),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  _Chip(text: '권한: ${employee.level.isEmpty ? '-' : employee.level}'),
                  _Chip(text: '역할: ${employee.role.label}'),
                  if (employee.jobTitle != null && employee.jobTitle!.isNotEmpty) _Chip(text: employee.jobTitle!),
                ],
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  if (canEdit) _ActionButton(label: '수정', onPressed: onEdit),
                  _ActionButton(
                    label: employee.status == UserStatus.locked ? '잠금해제' : '잠금',
                    onPressed: onToggleLock,
                    color: employee.status == UserStatus.locked ? Colors.green : null,
                  ),
                  _ActionButton(label: '단말기', onPressed: onDevices),
                  if (canManageEmployment) _ActionButton(label: '비밀번호 초기화', onPressed: onResetPassword),
                  if (canManageEmployment && canChangeRole) _ActionButton(label: '비밀번호 변경', onPressed: onSetPassword),
                  if (canManageEmployment && employee.status != UserStatus.inactive)
                    _ActionButton(label: '퇴사 처리', onPressed: onResign, color: Colors.red),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: Colors.blue.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(6)),
      child: Text(text, style: const TextStyle(fontSize: 11, color: Colors.blue)),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.label, this.onPressed, this.color});
  final String label;
  final VoidCallback? onPressed;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: color,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        minimumSize: Size.zero,
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      ),
      child: Text(label, style: const TextStyle(fontSize: 12)),
    );
  }
}
