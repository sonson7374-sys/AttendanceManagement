import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/work_schedule_change_request_model.dart';
import '../model/work_schedule_model.dart';
import '../provider/work_schedule_provider.dart';
import '../../../core/network/dio_client.dart';

/// 근무제 변경요청 폼(관리자웹 WorkScheduleChangeRequestModal과 동일).
/// 근무지 변경요청과 달리 값을 직접 입력하지 않고, 기존에 등록된 활성 근무제 중
/// 하나를 선택해 다음 달 이후의 적용월을 지정하는 방식이다.
class WorkScheduleChangeRequestSheet extends ConsumerStatefulWidget {
  const WorkScheduleChangeRequestSheet({super.key, required this.currentSchedule});

  final WorkSchedule currentSchedule;

  @override
  ConsumerState<WorkScheduleChangeRequestSheet> createState() => _WorkScheduleChangeRequestSheetState();
}

class _WorkScheduleChangeRequestSheetState extends ConsumerState<WorkScheduleChangeRequestSheet> {
  late final TextEditingController _reasonCtrl;
  int? _targetId;
  DateTime? _effectiveMonth;
  bool _saving = false;

  DateTime get _nextMonthFirstDay {
    final now = DateTime.now();
    return DateTime(now.year, now.month + 1, 1);
  }

  @override
  void initState() {
    super.initState();
    _reasonCtrl = TextEditingController();
    _effectiveMonth = _nextMonthFirstDay;
  }

  @override
  void dispose() {
    _reasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickMonth() async {
    final firstAllowed = _nextMonthFirstDay;
    final picked = await showDatePicker(
      context: context,
      initialDate: _effectiveMonth ?? firstAllowed,
      firstDate: firstAllowed,
      lastDate: DateTime(firstAllowed.year + 2, 12, 1),
      initialDatePickerMode: DatePickerMode.year,
      helpText: '적용 예정월 선택 (일자는 무시됩니다)',
    );
    if (picked != null) setState(() => _effectiveMonth = DateTime(picked.year, picked.month, 1));
  }

  String _formatMonth(DateTime d) => '${d.year}-${d.month.toString().padLeft(2, '0')}';

  Future<void> _submit() async {
    if (_targetId == null) {
      _showError('변경할 근무제를 선택해주세요.');
      return;
    }
    if (_effectiveMonth == null) {
      _showError('적용 예정월을 선택해주세요.');
      return;
    }
    if (_reasonCtrl.text.trim().isEmpty) {
      _showError('사유를 입력해주세요.');
      return;
    }
    setState(() => _saving = true);
    try {
      final payload = WorkScheduleChangeRequestPayload(
        currentWorkScheduleId: widget.currentSchedule.id,
        targetWorkScheduleId: _targetId!,
        effectiveMonth: _formatMonth(_effectiveMonth!),
        reason: _reasonCtrl.text.trim(),
      );
      await ref.read(workScheduleChangeRequestRepositoryProvider).submit(payload);
      ref.invalidate(myWorkScheduleChangeRequestsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('근무제 변경요청이 접수되었습니다. 관리자 승인 후 반영됩니다.')),
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

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final optionsAsync = ref.watch(workScheduleOptionsProvider);

    return Padding(
      padding: EdgeInsets.only(
        left: 20, right: 20, top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('근무제 변경요청', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            Text('기존: ${widget.currentSchedule.name}', style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 16),
            optionsAsync.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => Text('근무제 목록을 불러오지 못했습니다.\n$e'),
              data: (options) {
                final selectable = options.where((o) => o.id != widget.currentSchedule.id).toList();
                return DropdownButtonFormField<int>(
                  value: _targetId,
                  decoration: const InputDecoration(labelText: '변경할 근무제', border: OutlineInputBorder()),
                  items: selectable
                      .map((o) => DropdownMenuItem(value: o.id, child: Text('${o.name} (${o.scheduleType.label})')))
                      .toList(),
                  onChanged: (v) => setState(() => _targetId = v),
                );
              },
            ),
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: _pickMonth,
              child: Text(_effectiveMonth == null ? '적용 예정월 선택' : '적용 예정월: ${_formatMonth(_effectiveMonth!)}'),
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
                    child: Text(_saving ? '신청 중...' : '근무제변경요청'),
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
