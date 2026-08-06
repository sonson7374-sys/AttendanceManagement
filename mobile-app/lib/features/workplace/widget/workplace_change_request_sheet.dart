import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/workplace_change_request_model.dart';
import '../model/workplace_detail_model.dart';
import '../provider/workplace_management_provider.dart';
import '../../../core/network/dio_client.dart';

/// 근무지 변경요청 폼(직원용). 관리자웹 WorkplaceChangeRequestModal과 동일하게, 근무지 유형·반경·
/// 허용 정확도는 관리자만 지정 가능한 값이라 신청 화면에서는 받지 않고 기존 값을 그대로 이어받는다.
class WorkplaceChangeRequestSheet extends ConsumerStatefulWidget {
  const WorkplaceChangeRequestSheet({super.key, required this.currentWorkplace});

  final WorkplaceDetail currentWorkplace;

  @override
  ConsumerState<WorkplaceChangeRequestSheet> createState() => _WorkplaceChangeRequestSheetState();
}

class _WorkplaceChangeRequestSheetState extends ConsumerState<WorkplaceChangeRequestSheet> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _addressCtrl;
  late final TextEditingController _detailAddressCtrl;
  late final TextEditingController _latCtrl;
  late final TextEditingController _lngCtrl;
  late final TextEditingController _reasonCtrl;
  late bool _checkInAllowed;
  late bool _checkOutAllowed;
  DateTime? _effectiveDate;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final w = widget.currentWorkplace;
    _nameCtrl = TextEditingController(text: w.name);
    _addressCtrl = TextEditingController(text: w.address ?? '');
    _detailAddressCtrl = TextEditingController(text: w.detailAddress ?? '');
    _latCtrl = TextEditingController(text: w.latitude.toString());
    _lngCtrl = TextEditingController(text: w.longitude.toString());
    _reasonCtrl = TextEditingController();
    _checkInAllowed = w.checkInAllowed;
    _checkOutAllowed = w.checkOutAllowed;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _addressCtrl.dispose();
    _detailAddressCtrl.dispose();
    _latCtrl.dispose();
    _lngCtrl.dispose();
    _reasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _effectiveDate ?? now,
      firstDate: now,
      lastDate: now.add(const Duration(days: 365)),
    );
    if (picked != null) setState(() => _effectiveDate = picked);
  }

  Future<void> _submit() async {
    final lat = double.tryParse(_latCtrl.text.trim());
    final lng = double.tryParse(_lngCtrl.text.trim());
    if (_nameCtrl.text.trim().isEmpty) {
      _showError('근무지명을 입력해주세요.');
      return;
    }
    if (lat == null || lng == null) {
      _showError('위도·경도를 올바르게 입력해주세요.');
      return;
    }
    if (_effectiveDate == null) {
      _showError('적용 예정일을 선택해주세요.');
      return;
    }
    if (_reasonCtrl.text.trim().isEmpty) {
      _showError('사유를 입력해주세요.');
      return;
    }
    setState(() => _saving = true);
    try {
      final w = widget.currentWorkplace;
      final payload = WorkplaceChangeRequestPayload(
        currentWorkplaceId: w.id,
        name: _nameCtrl.text.trim(),
        address: _addressCtrl.text.trim(),
        detailAddress: _detailAddressCtrl.text.trim(),
        type: w.type.toApi(),
        latitude: lat,
        longitude: lng,
        radiusMeters: w.radiusMeters,
        maxAccuracyMeters: w.maxAccuracyMeters,
        checkInAllowed: _checkInAllowed,
        checkOutAllowed: _checkOutAllowed,
        effectiveDate: _formatDate(_effectiveDate!),
        reason: _reasonCtrl.text.trim(),
      );
      await ref.read(workplaceChangeRequestRepositoryProvider).submit(payload);
      ref.invalidate(myWorkplaceChangeRequestsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('근무지 변경요청이 접수되었습니다. 관리자 승인 후 반영됩니다.')),
        );
        Navigator.of(context).pop(true);
      }
    } on ApiException catch (e) {
      _showError(e.message);
    } catch (e) {
      _showError(e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String _formatDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20, right: 20, top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('근무지 변경요청', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            Text('기존: ${widget.currentWorkplace.name}', style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 16),
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: '근무지명', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _addressCtrl,
              decoration: const InputDecoration(labelText: '주소', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _detailAddressCtrl,
              decoration: const InputDecoration(labelText: '상세 주소', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _latCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                    decoration: const InputDecoration(labelText: '위도', border: OutlineInputBorder()),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _lngCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                    decoration: const InputDecoration(labelText: '경도', border: OutlineInputBorder()),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(
              '근무지 유형·허용 반경·허용 정확도는 관리자 승인 시 기존 설정값이 그대로 적용됩니다.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: CheckboxListTile(
                    value: _checkInAllowed,
                    onChanged: (v) => setState(() => _checkInAllowed = v ?? true),
                    title: const Text('출근 허용'),
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                  ),
                ),
                Expanded(
                  child: CheckboxListTile(
                    value: _checkOutAllowed,
                    onChanged: (v) => setState(() => _checkOutAllowed = v ?? true),
                    title: const Text('퇴근 허용'),
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                  ),
                ),
              ],
            ),
            OutlinedButton(
              onPressed: _pickDate,
              child: Text(_effectiveDate == null ? '적용 예정일 선택' : '적용 예정일: ${_formatDate(_effectiveDate!)}'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _reasonCtrl,
              maxLines: 3,
              decoration: const InputDecoration(labelText: '사유', border: OutlineInputBorder()),
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
                    child: Text(_saving ? '신청 중...' : '근무지변경요청'),
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
