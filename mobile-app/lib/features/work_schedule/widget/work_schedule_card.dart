import 'package:flutter/material.dart';
import '../model/work_schedule_change_request_model.dart';
import '../model/work_schedule_model.dart';

String _fmtMin(int minutes) {
  final h = minutes ~/ 60;
  final m = minutes % 60;
  return '${h}h ${m}m';
}

/// 관리자웹 WorkSchedulesPage 카드와 동일한 정보(근무시간/소정근무/연장·지각·조퇴 기준/휴게시간)를
/// 보여주고, 권한에 따라 수정·삭제 또는 변경요청 버튼을 노출한다.
class WorkScheduleCard extends StatelessWidget {
  const WorkScheduleCard({
    super.key,
    required this.schedule,
    required this.canEdit,
    required this.canDelete,
    this.canRequestChange = false,
    this.pendingChangeRequest,
    this.latestChangeRequest,
    this.onEdit,
    this.onDelete,
    this.onRequestChange,
  });

  final WorkSchedule schedule;
  final bool canEdit;
  final bool canDelete;
  final bool canRequestChange;
  final WorkScheduleChangeRequest? pendingChangeRequest;
  final WorkScheduleChangeRequest? latestChangeRequest;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;
  final VoidCallback? onRequestChange;

  @override
  Widget build(BuildContext context) {
    final isPending = latestChangeRequest?.status == 'PENDING';
    final isApproved = latestChangeRequest?.status == 'APPROVED';
    final isRejected = latestChangeRequest?.status == 'REJECTED';

    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: schedule.defaultSchedule ? Colors.blue : Colors.grey.shade300, width: 3),
      ),
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
                      Row(
                        children: [
                          Flexible(
                            child: Text(schedule.name,
                                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                          ),
                        ],
                      ),
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(schedule.scheduleType.label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    if (schedule.defaultSchedule) const _Badge(text: '기본', color: Colors.blue),
                    if (isPending) const _Badge(text: '변경요청 검토중', color: Colors.orange),
                    if (isApproved) const _Badge(text: '변경요청 승인됨', color: Colors.blue),
                    if (isRejected) const _Badge(text: '변경요청 반려됨', color: Colors.red),
                  ],
                ),
              ],
            ),
            if (isPending && latestChangeRequest != null) ...[
              const SizedBox(height: 8),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration:
                    BoxDecoration(color: Colors.orange.shade50, borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.orange.shade100)),
                child: Text(
                  '${latestChangeRequest!.effectiveMonth} · ${latestChangeRequest!.targetWorkScheduleName ?? ''}(으)로 변경 요청됨 · 관리자 승인 대기중',
                  style: TextStyle(fontSize: 13, color: Colors.orange.shade800),
                ),
              ),
            ],
            if (isApproved && latestChangeRequest != null) ...[
              const SizedBox(height: 8),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: Colors.blue.shade50, borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.blue.shade100)),
                child: Text(
                  '${latestChangeRequest!.effectiveMonth}부터 ${latestChangeRequest!.targetWorkScheduleName ?? ''}(으)로 근무제가 변경됩니다.',
                  style: TextStyle(fontSize: 13, color: Colors.blue.shade800),
                ),
              ),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _InfoBox(label: '근무 시간', value: '${schedule.workStartTime} ~ ${schedule.workEndTime}'),
                _InfoBox(label: '소정 근무', value: _fmtMin(schedule.requiredWorkMinutes)),
                _InfoBox(label: '연장 기준', value: _fmtMin(schedule.overtimeThresholdMin)),
                _InfoBox(label: '지각 기준', value: '${schedule.lateThresholdMinutes}분'),
                _InfoBox(label: '조퇴 기준', value: '${schedule.earlyLeaveThresholdMinutes}분'),
                _InfoBox(label: '휴게시간', value: '${schedule.breakMinutes}분'),
              ],
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                if (canEdit)
                  Expanded(
                    child: OutlinedButton(onPressed: onEdit, child: const Text('수정')),
                  ),
                if (canEdit && canDelete) const SizedBox(width: 8),
                if (canDelete)
                  Expanded(
                    child: OutlinedButton(
                      onPressed: schedule.defaultSchedule ? null : onDelete,
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                      child: const Text('삭제'),
                    ),
                  ),
                if (canRequestChange)
                  Expanded(
                    child: OutlinedButton(
                      onPressed: pendingChangeRequest != null ? null : onRequestChange,
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.orange),
                      child: Text(pendingChangeRequest != null ? '검토중' : '변경요청'),
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

class _Badge extends StatelessWidget {
  const _Badge({required this.text, required this.color});
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 4),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(20)),
      child: Text(text, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: color)),
    );
  }
}

class _InfoBox extends StatelessWidget {
  const _InfoBox({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(color: Colors.grey.shade100, borderRadius: BorderRadius.circular(6)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
          Text(value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}
