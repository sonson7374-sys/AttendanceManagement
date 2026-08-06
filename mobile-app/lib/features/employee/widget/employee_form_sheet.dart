import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/employee_model.dart';
import '../provider/employee_provider.dart';
import '../../../core/network/dio_client.dart';

/// 직원 등록/수정 폼. 관리자웹의 CreateModal/EditModal을 하나의 시트로 통합했다.
/// 등록 시: 이메일·비밀번호·이름·사번·권한레벨·역할·소속조직(선택)만 입력한다(관리자웹과 동일하게
/// 전화번호·직급 등은 등록 후 수정에서 채운다).
/// 수정 시: 이메일은 변경 불가(읽기전용 캡션), 역할은 canChangeRole(SYSTEM_ADMIN)일 때만 활성화된다.
class EmployeeFormSheet extends ConsumerStatefulWidget {
  const EmployeeFormSheet({super.key, this.employee, required this.canChangeRole});

  final Employee? employee;
  final bool canChangeRole;

  @override
  ConsumerState<EmployeeFormSheet> createState() => _EmployeeFormSheetState();
}

class _EmployeeFormSheetState extends ConsumerState<EmployeeFormSheet> {
  late final TextEditingController _emailCtrl;
  late final TextEditingController _passwordCtrl;
  late final TextEditingController _nameCtrl;
  late final TextEditingController _employeeNumberCtrl;
  late final TextEditingController _phoneCtrl;
  late final TextEditingController _jobTitleCtrl;
  late final TextEditingController _employmentTypeCtrl;
  late final TextEditingController _levelCtrl;
  DateTime? _hireDate;
  int? _organizationId;
  late UserRole _role;
  bool _saving = false;

  bool get _isEdit => widget.employee != null;

  @override
  void initState() {
    super.initState();
    final e = widget.employee;
    _emailCtrl = TextEditingController(text: e?.email ?? '');
    _passwordCtrl = TextEditingController();
    _nameCtrl = TextEditingController(text: e?.name ?? '');
    _employeeNumberCtrl = TextEditingController(text: e?.employeeNumber ?? '');
    _phoneCtrl = TextEditingController(text: e?.phone ?? '');
    _jobTitleCtrl = TextEditingController(text: e?.jobTitle ?? '');
    _employmentTypeCtrl = TextEditingController(text: e?.employmentType ?? '');
    _levelCtrl = TextEditingController(text: e?.level ?? '');
    _hireDate = e?.hireDate != null ? DateTime.tryParse(e!.hireDate!) : null;
    _organizationId = e?.organizationId;
    _role = e?.role ?? UserRole.employee;
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    _nameCtrl.dispose();
    _employeeNumberCtrl.dispose();
    _phoneCtrl.dispose();
    _jobTitleCtrl.dispose();
    _employmentTypeCtrl.dispose();
    _levelCtrl.dispose();
    super.dispose();
  }

  String _formatDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<void> _pickHireDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _hireDate ?? now,
      firstDate: DateTime(now.year - 30),
      lastDate: DateTime(now.year + 5),
    );
    if (picked != null) setState(() => _hireDate = picked);
  }

  Future<void> _submit() async {
    if (_nameCtrl.text.trim().isEmpty) {
      _showError('이름을 입력해주세요.');
      return;
    }
    if (!_isEdit) {
      if (_emailCtrl.text.trim().isEmpty || !_emailCtrl.text.contains('@')) {
        _showError('이메일을 올바르게 입력해주세요.');
        return;
      }
      if (_passwordCtrl.text.length < 8) {
        _showError('비밀번호는 8자 이상 입력해주세요.');
        return;
      }
      if (_employeeNumberCtrl.text.trim().isEmpty) {
        _showError('사번을 입력해주세요.');
        return;
      }
    }
    setState(() => _saving = true);
    try {
      final repo = ref.read(employeeRepositoryProvider);
      if (_isEdit) {
        final payload = EmployeeProfileUpdatePayload(
          name: _nameCtrl.text.trim(),
          phone: _phoneCtrl.text.trim().isEmpty ? null : _phoneCtrl.text.trim(),
          jobTitle: _jobTitleCtrl.text.trim().isEmpty ? null : _jobTitleCtrl.text.trim(),
          employeeNumber: _employeeNumberCtrl.text.trim().isEmpty ? null : _employeeNumberCtrl.text.trim(),
          organizationId: _organizationId,
          employmentType: _employmentTypeCtrl.text.trim().isEmpty ? null : _employmentTypeCtrl.text.trim(),
          hireDate: _hireDate != null ? _formatDate(_hireDate!) : null,
          level: _levelCtrl.text.trim(),
        );
        await repo.updateProfile(widget.employee!.id, payload);
        if (widget.canChangeRole && _role != widget.employee!.role) {
          await repo.changeRole(widget.employee!.id, _role);
        }
      } else {
        final payload = EmployeeCreatePayload(
          email: _emailCtrl.text.trim(),
          password: _passwordCtrl.text,
          name: _nameCtrl.text.trim(),
          employeeNumber: _employeeNumberCtrl.text.trim(),
          role: _role,
          level: _levelCtrl.text.trim().isEmpty ? 'EMPLOYEE' : _levelCtrl.text.trim(),
          organizationId: _organizationId,
        );
        await repo.createUser(payload);
      }
      if (mounted) Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      _showError(e.message);
    } catch (e) {
      _showError(e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final organizationsAsync = ref.watch(organizationsProvider);

    return Padding(
      padding: EdgeInsets.only(
        left: 20, right: 20, top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(_isEdit ? '직원 수정' : '직원 등록',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 16),
            if (_isEdit) ...[
              Text('이메일: ${widget.employee!.email}', style: const TextStyle(fontSize: 13, color: Colors.grey)),
              const Text('이메일은 변경할 수 없습니다.', style: TextStyle(fontSize: 11, color: Colors.grey)),
              const SizedBox(height: 12),
            ] else ...[
              TextField(
                controller: _emailCtrl,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: '이메일', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _passwordCtrl,
                obscureText: true,
                decoration: const InputDecoration(labelText: '비밀번호 (8자 이상)', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
            ],
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: '이름', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _employeeNumberCtrl,
              decoration: const InputDecoration(labelText: '사번', border: OutlineInputBorder()),
            ),
            if (_isEdit) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _phoneCtrl,
                      decoration: const InputDecoration(labelText: '휴대전화', border: OutlineInputBorder()),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: _jobTitleCtrl,
                      decoration: const InputDecoration(labelText: '직급', border: OutlineInputBorder()),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _employmentTypeCtrl,
                      decoration: const InputDecoration(labelText: '고용형태 (예: 정규직)', border: OutlineInputBorder()),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _pickHireDate,
                      child: Text(_hireDate == null ? '입사일 선택' : _formatDate(_hireDate!)),
                    ),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 12),
            organizationsAsync.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => Text('조직 목록을 불러오지 못했습니다.\n$e'),
              data: (orgs) => DropdownButtonFormField<int?>(
                value: _organizationId,
                decoration: const InputDecoration(labelText: '소속 조직', border: OutlineInputBorder()),
                items: [
                  const DropdownMenuItem<int?>(value: null, child: Text('선택 안 함')),
                  ...orgs.map((o) => DropdownMenuItem<int?>(value: o.id, child: Text(o.name))),
                ],
                onChanged: (v) => setState(() => _organizationId = v),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _levelCtrl,
              decoration: const InputDecoration(labelText: '권한레벨 (예: 팀장, 파트장 등)', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<UserRole>(
              value: _role,
              decoration: InputDecoration(
                labelText: _isEdit && !widget.canChangeRole ? '역할 (시스템 관리자만 변경 가능)' : '역할',
                border: const OutlineInputBorder(),
              ),
              items: kUserRoleOptions.map((r) => DropdownMenuItem(value: r, child: Text(r.label))).toList(),
              onChanged: (_isEdit && !widget.canChangeRole) ? null : (v) => setState(() => _role = v ?? _role),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    child: const Text('취소'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: FilledButton(
                    onPressed: _saving ? null : _submit,
                    child: Text(_saving ? '저장 중...' : (_isEdit ? '수정' : '등록')),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
