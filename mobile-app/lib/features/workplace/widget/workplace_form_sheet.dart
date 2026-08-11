import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/workplace_detail_model.dart';
import '../provider/workplace_management_provider.dart';
import '../../../core/network/dio_client.dart';

const List<(WorkplaceType, String)> kWorkplaceTypeOptions = [
  (WorkplaceType.office, '일반 사무실'),
  (WorkplaceType.largeSite, '대형 사업장'),
  (WorkplaceType.constructionSite, '건설 현장'),
  (WorkplaceType.indoor, '지하·실내'),
  (WorkplaceType.other, '기타'),
];

/// 근무지 등록/수정 폼. 관리자웹 CreateWorkplaceModal/EditWorkplaceModal과 동일한 필드 구성이며,
/// 지도 미리보기 대신 좌표를 직접 입력한다(카카오맵 JS 키는 웹 전용이라 모바일에서 재사용할 수 없음).
class WorkplaceFormSheet extends ConsumerStatefulWidget {
  const WorkplaceFormSheet({super.key, this.workplace});

  final WorkplaceDetail? workplace;

  @override
  ConsumerState<WorkplaceFormSheet> createState() => _WorkplaceFormSheetState();
}

class _WorkplaceFormSheetState extends ConsumerState<WorkplaceFormSheet> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _addressCtrl;
  late final TextEditingController _detailAddressCtrl;
  late final TextEditingController _latCtrl;
  late final TextEditingController _lngCtrl;
  late final TextEditingController _radiusCtrl;
  late final TextEditingController _accuracyCtrl;
  late WorkplaceType _type;
  late bool _checkInAllowed;
  late bool _checkOutAllowed;
  bool _saving = false;

  bool get _isNew => widget.workplace == null;

  @override
  void initState() {
    super.initState();
    final w = widget.workplace;
    _nameCtrl = TextEditingController(text: w?.name ?? '');
    _addressCtrl = TextEditingController(text: w?.address ?? '');
    _detailAddressCtrl = TextEditingController(text: w?.detailAddress ?? '');
    _latCtrl = TextEditingController(text: w?.latitude.toString() ?? '37.5665');
    _lngCtrl = TextEditingController(text: w?.longitude.toString() ?? '126.9780');
    _radiusCtrl = TextEditingController(text: w?.radiusMeters.toString() ?? '100');
    _accuracyCtrl = TextEditingController(text: w?.maxAccuracyMeters?.toString() ?? '');
    _type = w?.type ?? WorkplaceType.office;
    _checkInAllowed = w?.checkInAllowed ?? true;
    _checkOutAllowed = w?.checkOutAllowed ?? true;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _addressCtrl.dispose();
    _detailAddressCtrl.dispose();
    _latCtrl.dispose();
    _lngCtrl.dispose();
    _radiusCtrl.dispose();
    _accuracyCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_nameCtrl.text.trim().isEmpty) {
      _showError('근무지명을 입력해주세요.');
      return;
    }
    final lat = double.tryParse(_latCtrl.text.trim());
    final lng = double.tryParse(_lngCtrl.text.trim());
    final radius = int.tryParse(_radiusCtrl.text.trim());
    if (lat == null || lng == null) {
      _showError('위도·경도를 올바르게 입력해주세요.');
      return;
    }
    if (radius == null || radius < 10) {
      _showError('반경은 10m 이상 숫자로 입력해주세요.');
      return;
    }
    setState(() => _saving = true);
    try {
      final payload = WorkplaceDetailPayload(
        name: _nameCtrl.text.trim(),
        address: _addressCtrl.text.trim(),
        detailAddress: _detailAddressCtrl.text.trim(),
        type: _type,
        latitude: lat,
        longitude: lng,
        radiusMeters: radius,
        maxAccuracyMeters: int.tryParse(_accuracyCtrl.text.trim()),
        checkInAllowed: _checkInAllowed,
        checkOutAllowed: _checkOutAllowed,
      );
      final repo = ref.read(workplaceManagementRepositoryProvider);
      if (_isNew) {
        await repo.createWorkplace(payload);
      } else {
        await repo.updateWorkplace(widget.workplace!.id, payload);
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
    return Padding(
      padding: EdgeInsets.only(
        left: 20, right: 20, top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              _isNew ? '근무지 등록' : '근무지 수정',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: '근무지명', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<WorkplaceType>(
              initialValue: _type,
              decoration: const InputDecoration(labelText: '근무지 유형', border: OutlineInputBorder()),
              items: kWorkplaceTypeOptions
                  .map((o) => DropdownMenuItem(value: o.$1, child: Text(o.$2)))
                  .toList(),
              onChanged: (v) => setState(() => _type = v!),
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
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _radiusCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '반경(m)', border: OutlineInputBorder()),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _accuracyCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '허용 정확도(m)', hintText: '기본값', border: OutlineInputBorder()),
                  ),
                ),
              ],
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
            const SizedBox(height: 8),
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
                    onPressed: _saving ? null : _save,
                    child: Text(_saving ? '저장 중...' : (_isNew ? '등록' : '수정')),
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
