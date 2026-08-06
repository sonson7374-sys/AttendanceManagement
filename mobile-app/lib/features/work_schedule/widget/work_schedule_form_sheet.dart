import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/work_schedule_model.dart';
import '../provider/work_schedule_provider.dart';
import '../../../core/network/dio_client.dart';

/// 근무제 등록/수정 폼(관리자웹 ScheduleModal과 동일한 필드 구성).
class WorkScheduleFormSheet extends ConsumerStatefulWidget {
  const WorkScheduleFormSheet({super.key, this.schedule});

  final WorkSchedule? schedule;

  @override
  ConsumerState<WorkScheduleFormSheet> createState() => _WorkScheduleFormSheetState();
}

class _WorkScheduleFormSheetState extends ConsumerState<WorkScheduleFormSheet> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _requiredWorkMinutesCtrl;
  late final TextEditingController _overtimeThresholdCtrl;
  late final TextEditingController _lateThresholdCtrl;
  late final TextEditingController _earlyLeaveThresholdCtrl;
  late final TextEditingController _breakMinutesCtrl;
  late final TextEditingController _holidayWorkThresholdCtrl;

  late WorkScheduleType _type;
  late TimeOfDay _workStart;
  late TimeOfDay _workEnd;
  TimeOfDay? _nightShiftStart;
  TimeOfDay? _nightShiftEnd;
  late bool _defaultSchedule;
  bool _saving = false;

  bool get _isEdit => widget.schedule != null;

  @override
  void initState() {
    super.initState();
    final s = widget.schedule;
    _nameCtrl = TextEditingController(text: s?.name ?? '');
    _requiredWorkMinutesCtrl = TextEditingController(text: (s?.requiredWorkMinutes ?? 480).toString());
    _overtimeThresholdCtrl = TextEditingController(text: (s?.overtimeThresholdMin ?? 480).toString());
    _lateThresholdCtrl = TextEditingController(text: (s?.lateThresholdMinutes ?? 0).toString());
    _earlyLeaveThresholdCtrl = TextEditingController(text: (s?.earlyLeaveThresholdMinutes ?? 0).toString());
    _breakMinutesCtrl = TextEditingController(text: (s?.breakMinutes ?? 60).toString());
    _holidayWorkThresholdCtrl = TextEditingController(text: (s?.holidayWorkThresholdMinutes ?? 0).toString());
    _type = s?.scheduleType ?? WorkScheduleType.fixed;
    _workStart = _parseTime(s?.workStartTime ?? '09:00');
    _workEnd = _parseTime(s?.workEndTime ?? '18:00');
    _nightShiftStart = s?.nightShiftStart != null ? _parseTime(s!.nightShiftStart!) : null;
    _nightShiftEnd = s?.nightShiftEnd != null ? _parseTime(s!.nightShiftEnd!) : null;
    _defaultSchedule = s?.defaultSchedule ?? false;
  }

  TimeOfDay _parseTime(String hhmm) {
    final parts = hhmm.split(':');
    return TimeOfDay(hour: int.parse(parts[0]), minute: int.parse(parts[1]));
  }

  String _formatTime(TimeOfDay t) => '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  @override
  void dispose() {
    _nameCtrl.dispose();
    _requiredWorkMinutesCtrl.dispose();
    _overtimeThresholdCtrl.dispose();
    _lateThresholdCtrl.dispose();
    _earlyLeaveThresholdCtrl.dispose();
    _breakMinutesCtrl.dispose();
    _holidayWorkThresholdCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickTime(TimeOfDay initial, ValueChanged<TimeOfDay> onPicked) async {
    final picked = await showTimePicker(context: context, initialTime: initial);
    if (picked != null) onPicked(picked);
  }

  int? _validatedInt(String text, int min, int max, String label) {
    final v = int.tryParse(text.trim());
    if (v == null || v < min || v > max) {
      _showError('$label은(는) $min~$max 사이 숫자로 입력해주세요.');
      return null;
    }
    return v;
  }

  Future<void> _submit() async {
    if (_nameCtrl.text.trim().isEmpty) {
      _showError('근무제명을 입력해주세요.');
      return;
    }
    final workStartMinutes = _workStart.hour * 60 + _workStart.minute;
    final workEndMinutes = _workEnd.hour * 60 + _workEnd.minute;
    if (workEndMinutes <= workStartMinutes) {
      _showError('퇴근 시간은 출근 시간 이후여야 합니다.');
      return;
    }
    final requiredWorkMinutes = _validatedInt(_requiredWorkMinutesCtrl.text, 60, 720, '소정 근무시간(분)');
    if (requiredWorkMinutes == null) return;
    final overtimeThresholdMin = _validatedInt(_overtimeThresholdCtrl.text, 60, 720, '연장근무 기준(분)');
    if (overtimeThresholdMin == null) return;
    final lateThresholdMinutes = _validatedInt(_lateThresholdCtrl.text, 0, 120, '지각 기준(분)');
    if (lateThresholdMinutes == null) return;
    final earlyLeaveThresholdMinutes = _validatedInt(_earlyLeaveThresholdCtrl.text, 0, 120, '조퇴 기준(분)');
    if (earlyLeaveThresholdMinutes == null) return;
    final breakMinutes = _validatedInt(_breakMinutesCtrl.text, 0, 480, '휴게시간(분)');
    if (breakMinutes == null) return;
    final holidayWorkThresholdMinutes = _validatedInt(_holidayWorkThresholdCtrl.text, 0, 720, '휴일근무 기준(분)');
    if (holidayWorkThresholdMinutes == null) return;

    setState(() => _saving = true);
    try {
      final payload = WorkSchedulePayload(
        name: _nameCtrl.text.trim(),
        workStartTime: _formatTime(_workStart),
        workEndTime: _formatTime(_workEnd),
        requiredWorkMinutes: requiredWorkMinutes,
        overtimeThresholdMin: overtimeThresholdMin,
        defaultSchedule: _defaultSchedule,
        scheduleType: _type,
        lateThresholdMinutes: lateThresholdMinutes,
        earlyLeaveThresholdMinutes: earlyLeaveThresholdMinutes,
        breakMinutes: breakMinutes,
        nightShiftStart: _nightShiftStart != null ? _formatTime(_nightShiftStart!) : null,
        nightShiftEnd: _nightShiftEnd != null ? _formatTime(_nightShiftEnd!) : null,
        holidayWorkThresholdMinutes: holidayWorkThresholdMinutes,
      );
      final repo = ref.read(workScheduleRepositoryProvider);
      if (_isEdit) {
        await repo.updateWorkSchedule(widget.schedule!.id, payload);
      } else {
        await repo.createWorkSchedule(payload);
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
            Text(_isEdit ? '근무제 수정' : '근무제 등록',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 16),
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: '근무제명', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<WorkScheduleType>(
              value: _type,
              decoration: const InputDecoration(labelText: '근무제 유형', border: OutlineInputBorder()),
              items: kWorkScheduleTypeOptions
                  .map((t) => DropdownMenuItem(value: t, child: Text(t.label)))
                  .toList(),
              onChanged: (v) => setState(() => _type = v ?? _type),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => _pickTime(_workStart, (t) => setState(() => _workStart = t)),
                    child: Text('출근 ${_formatTime(_workStart)}'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => _pickTime(_workEnd, (t) => setState(() => _workEnd = t)),
                    child: Text('퇴근 ${_formatTime(_workEnd)}'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _requiredWorkMinutesCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '소정 근무시간(분)', border: OutlineInputBorder()),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _overtimeThresholdCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '연장근무 기준(분)', border: OutlineInputBorder()),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _lateThresholdCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '지각 기준(분)', border: OutlineInputBorder()),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _earlyLeaveThresholdCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '조퇴 기준(분)', border: OutlineInputBorder()),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _breakMinutesCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '휴게시간(분)', border: OutlineInputBorder()),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _holidayWorkThresholdCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '휴일근무 기준(분)', border: OutlineInputBorder()),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => _pickTime(_nightShiftStart ?? const TimeOfDay(hour: 22, minute: 0),
                        (t) => setState(() => _nightShiftStart = t)),
                    child: Text(_nightShiftStart != null ? '야간 시작 ${_formatTime(_nightShiftStart!)}' : '야간 시작(선택)'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => _pickTime(_nightShiftEnd ?? const TimeOfDay(hour: 6, minute: 0),
                        (t) => setState(() => _nightShiftEnd = t)),
                    child: Text(_nightShiftEnd != null ? '야간 종료 ${_formatTime(_nightShiftEnd!)}' : '야간 종료(선택)'),
                  ),
                ),
                if (_nightShiftStart != null || _nightShiftEnd != null)
                  IconButton(
                    icon: const Icon(Icons.clear, size: 18),
                    tooltip: '야간근무 설정 해제',
                    onPressed: () => setState(() {
                      _nightShiftStart = null;
                      _nightShiftEnd = null;
                    }),
                  ),
              ],
            ),
            const SizedBox(height: 4),
            CheckboxListTile(
              value: _defaultSchedule,
              onChanged: (v) => setState(() => _defaultSchedule = v ?? false),
              title: const Text('기본 근무제로 설정'),
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
            ),
            const SizedBox(height: 12),
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
