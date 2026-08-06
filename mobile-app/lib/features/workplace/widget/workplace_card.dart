import 'package:flutter/material.dart';
import '../model/workplace_change_request_model.dart';
import '../model/workplace_detail_model.dart';
import 'workplace_form_sheet.dart';

String _formatMonthDay(String dateStr) {
  final d = DateTime.parse('${dateStr}T00:00:00');
  return '${d.month}월 ${d.day}일';
}

/// 관리자웹 WorkplaceCard와 동일한 정보(이름/주소/유형/출퇴근 허용/좌표·반경)를 보여주고,
/// 권한에 따라 수정·직원배정관리·삭제·변경요청 버튼을 노출한다. 지도 미리보기는 좌표 텍스트로 대체한다
/// (카카오맵 JS 키는 웹 전용이라 모바일에서 재사용할 수 없음).
class WorkplaceCard extends StatelessWidget {
  const WorkplaceCard({
    super.key,
    required this.workplace,
    required this.canManage,
    required this.canEdit,
    this.canRequestChange = false,
    this.pendingChangeRequest,
    this.latestChangeRequest,
    this.onEdit,
    this.onAssign,
    this.onDelete,
    this.onRestore,
    this.onRequestChange,
  });

  final WorkplaceDetail workplace;
  final bool canManage;
  final bool canEdit;
  final bool canRequestChange;
  final WorkplaceChangeRequest? pendingChangeRequest;
  final WorkplaceChangeRequest? latestChangeRequest;
  final VoidCallback? onEdit;
  final VoidCallback? onAssign;
  final VoidCallback? onDelete;
  final VoidCallback? onRestore;
  final VoidCallback? onRequestChange;

  @override
  Widget build(BuildContext context) {
    final isPending = latestChangeRequest?.status == 'PENDING';
    final isApproved = latestChangeRequest?.status == 'APPROVED';
    final isRejected = latestChangeRequest?.status == 'REJECTED';

    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: workplace.active ? Colors.green : Colors.grey.shade300, width: 3),
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
                      Text(workplace.name, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                      if (workplace.address != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Text(
                            '${workplace.address}${workplace.detailAddress != null ? ' ${workplace.detailAddress}' : ''}',
                            style: const TextStyle(fontSize: 12, color: Colors.grey),
                          ),
                        ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    _Badge(text: workplace.active ? '활성' : '비활성', color: workplace.active ? Colors.green : Colors.grey),
                    if (isPending) const _Badge(text: '변경요청 검토중', color: Colors.orange),
                    if (isApproved) const _Badge(text: '변경요청 승인됨', color: Colors.blue),
                    if (isRejected) const _Badge(text: '변경요청 반려됨', color: Colors.red),
                  ],
                ),
              ],
            ),
            if (isApproved && latestChangeRequest != null) ...[
              const SizedBox(height: 8),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: Colors.blue.shade50, borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.blue.shade100)),
                child: Text(
                  '${_formatMonthDay(latestChangeRequest!.effectiveDate)}부터 ${latestChangeRequest!.name}(으)로 자동 전환됩니다.',
                  style: TextStyle(fontSize: 13, color: Colors.blue.shade800),
                ),
              ),
            ],
            const SizedBox(height: 10),
            Wrap(
              spacing: 6,
              children: [
                _Chip(text: kWorkplaceTypeOptions.firstWhere((o) => o.$1 == workplace.type).$2),
                if (!workplace.checkInAllowed) const _Chip(text: '출근 불가', color: Colors.red),
                if (!workplace.checkOutAllowed) const _Chip(text: '퇴근 불가', color: Colors.red),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: _InfoBox(label: '위도', value: workplace.latitude.toStringAsFixed(4))),
                const SizedBox(width: 8),
                Expanded(child: _InfoBox(label: '경도', value: workplace.longitude.toStringAsFixed(4))),
                const SizedBox(width: 8),
                Expanded(child: _InfoBox(label: '반경', value: '${workplace.radiusMeters}m')),
              ],
            ),
            const SizedBox(height: 14),
            if (workplace.active)
              Row(
                children: [
                  if (canEdit)
                    Expanded(
                      child: OutlinedButton(onPressed: onEdit, child: const Text('수정')),
                    ),
                  if (canEdit) const SizedBox(width: 8),
                  if (canEdit)
                    Expanded(
                      flex: 2,
                      child: OutlinedButton(onPressed: onAssign, child: const Text('직원 배정 관리')),
                    ),
                  if (canManage) const SizedBox(width: 8),
                  if (canManage)
                    Expanded(
                      child: OutlinedButton(
                        onPressed: onDelete,
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
              )
            else if (canManage)
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: onRestore,
                  style: OutlinedButton.styleFrom(foregroundColor: Colors.green),
                  child: const Text('복구'),
                ),
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

class _Chip extends StatelessWidget {
  const _Chip({required this.text, this.color = Colors.blue});
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(6)),
      child: Text(text, style: TextStyle(fontSize: 11, color: color)),
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
