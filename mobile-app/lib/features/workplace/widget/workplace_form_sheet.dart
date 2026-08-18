import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/workplace_detail_model.dart';
import '../provider/workplace_management_provider.dart';
import '../widget/kakao_map_view.dart';
import '../../../core/network/dio_client.dart';

const List<(WorkplaceType, String)> kWorkplaceTypeOptions = [
  (WorkplaceType.office, '일반 사무실'),
  (WorkplaceType.largeSite, '대형 사업장'),
  (WorkplaceType.constructionSite, '건설 현장'),
  (WorkplaceType.indoor, '지하·실내'),
  (WorkplaceType.other, '기타'),
];

/// 근무지 등록/수정 폼. 관리자웹 CreateWorkplaceModal/EditWorkplaceModal과 동일한 필드 구성이며,
/// 지도(카카오맵)로 클릭·드래그·주소검색으로 좌표를 지정할 수 있다. 위도/경도 숫자 입력도 그대로
/// 유지해 지도가 로드되지 않는 경우에도 등록할 수 있게 한다.
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
  bool _searchingAddress = false;
  final _mapKey = GlobalKey<KakaoMapViewState>();

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

  Future<void> _searchAddress() async {
    final address = _addressCtrl.text.trim();
    if (address.isEmpty) {
      _showError('주소를 입력해주세요.');
      return;
    }
    setState(() => _searchingAddress = true);
    _mapKey.currentState?.searchAddress(address);
  }

  void _onAddressResolved(double latitude, double longitude, String addressName) {
    if (!mounted) return;
    setState(() {
      _searchingAddress = false;
      _latCtrl.text = latitude.toStringAsFixed(6);
      _lngCtrl.text = longitude.toStringAsFixed(6);
    });
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('좌표를 찾았습니다: $addressName')));
  }

  void _onMapError(String message) {
    if (!mounted) return;
    setState(() => _searchingAddress = false);
    _showError(message);
  }

  void _onMapPositionChanged(double latitude, double longitude) {
    if (!mounted) return;
    setState(() {
      _latCtrl.text = latitude.toStringAsFixed(6);
      _lngCtrl.text = longitude.toStringAsFixed(6);
    });
  }

  void _syncMapPosition() {
    final lat = double.tryParse(_latCtrl.text.trim());
    final lng = double.tryParse(_lngCtrl.text.trim());
    if (lat != null && lng != null) {
      _mapKey.currentState?.setPosition(lat, lng);
    }
  }

  void _syncMapRadius() {
    final radius = int.tryParse(_radiusCtrl.text.trim());
    if (radius != null) {
      _mapKey.currentState?.updateRadius(radius);
    }
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
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: TextField(
                    controller: _addressCtrl,
                    decoration: const InputDecoration(labelText: '주소', border: OutlineInputBorder()),
                    onSubmitted: (_) => _searchAddress(),
                  ),
                ),
                const SizedBox(width: 8),
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: OutlinedButton(
                    onPressed: _searchingAddress ? null : _searchAddress,
                    child: Text(_searchingAddress ? '검색 중...' : '주소 검색'),
                  ),
                ),
              ],
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
                    onChanged: (_) => _syncMapPosition(),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _lngCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
                    decoration: const InputDecoration(labelText: '경도', border: OutlineInputBorder()),
                    onChanged: (_) => _syncMapPosition(),
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
                    onChanged: (_) => _syncMapRadius(),
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
            Text(
              '지도를 클릭하거나 마커를 드래그해 좌표를 지정할 수 있습니다',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
            ),
            const SizedBox(height: 6),
            KakaoMapView(
              key: _mapKey,
              latitude: double.tryParse(_latCtrl.text.trim()) ?? 37.5665,
              longitude: double.tryParse(_lngCtrl.text.trim()) ?? 126.9780,
              radiusMeters: int.tryParse(_radiusCtrl.text.trim()) ?? 100,
              editable: true,
              height: 200,
              onPositionChanged: _onMapPositionChanged,
              onAddressResolved: _onAddressResolved,
              onError: _onMapError,
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
