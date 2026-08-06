import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../model/calendar_event_model.dart';
import '../provider/calendar_event_provider.dart';
import '../../../core/network/dio_client.dart';

/// 일정 등록/수정/상세 화면. 관리자웹 EventModal과 동일한 권한 규칙을 따른다:
/// - canManage가 false면(본인 소유가 아닌 일정) 전 필드 읽기전용, 저장/삭제 버튼 없음.
/// - isScheduleAdmin이 아니면 공개범위는 "개인"으로 고정된다(선택 자체가 잠긴다).
/// 서버(CalendarEventService)가 동일 기준으로 최종 검증하므로, 여기서의 잠금은 UX 편의일 뿐이다.
class EventFormDialog extends ConsumerStatefulWidget {
  const EventFormDialog({
    super.key,
    this.event,
    this.initialDay,
    required this.isScheduleAdmin,
    required this.canManage,
  });

  final CalendarEvent? event;
  final DateTime? initialDay;
  final bool isScheduleAdmin;
  final bool canManage;

  @override
  ConsumerState<EventFormDialog> createState() => _EventFormDialogState();
}

class _EventFormDialogState extends ConsumerState<EventFormDialog> {
  late final TextEditingController _titleCtrl;
  late final TextEditingController _locationCtrl;
  late final TextEditingController _descriptionCtrl;
  late DateTime _startAt;
  late DateTime _endAt;
  late bool _allDay;
  late CalendarEventCategory _category;
  late CalendarEventVisibility _visibility;
  bool _saving = false;
  bool _deleting = false;

  bool get _isNew => widget.event == null;
  bool get _readOnly => !widget.canManage;
  bool get _canChooseVisibility => widget.isScheduleAdmin && !_readOnly;

  @override
  void initState() {
    super.initState();
    final e = widget.event;
    final day = widget.initialDay ?? DateTime.now();
    _titleCtrl = TextEditingController(text: e?.title ?? '');
    _locationCtrl = TextEditingController(text: e?.location ?? '');
    _descriptionCtrl = TextEditingController(text: e?.description ?? '');
    _startAt = e?.startAt.toLocal() ?? DateTime(day.year, day.month, day.day, 9);
    _endAt = e?.endAt.toLocal() ?? DateTime(day.year, day.month, day.day, 10);
    _allDay = e?.allDay ?? false;
    _category = e?.category ?? CalendarEventCategory.meeting;
    _visibility = e?.visibility ?? (widget.isScheduleAdmin ? CalendarEventVisibility.all : CalendarEventVisibility.personal);
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _locationCtrl.dispose();
    _descriptionCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDate(bool isStart) async {
    final initial = isStart ? _startAt : _endAt;
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(initial.year - 2),
      lastDate: DateTime(initial.year + 2),
    );
    if (date == null || !mounted) return;
    if (_allDay) {
      setState(() {
        if (isStart) {
          _startAt = DateTime(date.year, date.month, date.day);
        } else {
          _endAt = DateTime(date.year, date.month, date.day, 23, 59);
        }
      });
      return;
    }
    final time = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(initial));
    if (time == null) return;
    setState(() {
      final picked = DateTime(date.year, date.month, date.day, time.hour, time.minute);
      if (isStart) {
        _startAt = picked;
      } else {
        _endAt = picked;
      }
    });
  }

  Future<void> _save() async {
    if (_titleCtrl.text.trim().isEmpty) {
      _showError('제목을 입력해주세요.');
      return;
    }
    if (_endAt.isBefore(_startAt)) {
      _showError('종료 일시는 시작 일시보다 빠를 수 없습니다.');
      return;
    }
    setState(() => _saving = true);
    try {
      final payload = CalendarEventPayload(
        title: _titleCtrl.text.trim(),
        startAt: _startAt,
        endAt: _endAt,
        allDay: _allDay,
        description: _descriptionCtrl.text.trim(),
        location: _locationCtrl.text.trim(),
        color: null,
        category: _category,
        visibility: _visibility,
      );
      final repo = ref.read(calendarEventRepositoryProvider);
      if (_isNew) {
        await repo.create(payload);
      } else {
        await repo.update(widget.event!.id, payload);
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

  Future<void> _delete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('일정 삭제'),
        content: const Text('삭제하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('삭제')),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _deleting = true);
    try {
      await ref.read(calendarEventRepositoryProvider).delete(widget.event!.id);
      if (mounted) Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final fmt = DateFormat('yyyy-MM-dd HH:mm');
    final visibilityOptions = _canChooseVisibility
        ? CalendarEventVisibility.values
        : [CalendarEventVisibility.personal];

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
              _readOnly ? '일정 상세' : (_isNew ? '일정 등록' : '일정 수정'),
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _titleCtrl,
              enabled: !_readOnly,
              decoration: const InputDecoration(labelText: '제목', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: DropdownButtonFormField<CalendarEventCategory>(
                    initialValue: _category,
                    decoration: const InputDecoration(labelText: '구분', border: OutlineInputBorder()),
                    items: CalendarEventCategory.values
                        .map((c) => DropdownMenuItem(value: c, child: Text(c.label)))
                        .toList(),
                    onChanged: _readOnly ? null : (v) => setState(() => _category = v!),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: DropdownButtonFormField<CalendarEventVisibility>(
                    initialValue: _visibility,
                    decoration: const InputDecoration(labelText: '공개범위', border: OutlineInputBorder()),
                    items: visibilityOptions
                        .map((v) => DropdownMenuItem(value: v, child: Text(v.label)))
                        .toList(),
                    onChanged: (!_canChooseVisibility) ? null : (v) => setState(() => _visibility = v!),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            CheckboxListTile(
              value: _allDay,
              onChanged: _readOnly ? null : (v) => setState(() => _allDay = v ?? false),
              title: const Text('종일'),
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
            ),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _readOnly ? null : () => _pickDate(true),
                    child: Text('시작: ${fmt.format(_startAt)}'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: _readOnly ? null : () => _pickDate(false),
                    child: Text('종료: ${fmt.format(_endAt)}'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _locationCtrl,
              enabled: !_readOnly,
              decoration: const InputDecoration(labelText: '장소', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _descriptionCtrl,
              enabled: !_readOnly,
              maxLines: 3,
              decoration: const InputDecoration(labelText: '설명', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 20),
            if (_readOnly)
              FilledButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('닫기'),
              )
            else
              Row(
                children: [
                  if (!_isNew)
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _deleting ? null : _delete,
                        style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                        child: Text(_deleting ? '삭제 중...' : '삭제'),
                      ),
                    ),
                  if (!_isNew) const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton(
                      onPressed: _saving ? null : _save,
                      child: Text(_saving ? '저장 중...' : '저장'),
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
