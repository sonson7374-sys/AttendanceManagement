import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../model/profile_model.dart';
import '../provider/profile_provider.dart';

const _roleLabels = {
  'EMPLOYEE': '직원',
  'MANAGER': '관리자',
  'HR_ADMIN': 'HR 관리자',
  'SYSTEM_ADMIN': '시스템 관리자',
};

const _statusLabels = {
  'ACTIVE': '활성',
  'INACTIVE': '비활성',
  'LOCKED': '잠금',
};

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(myProfileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('내 정보')),
      body: profileAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Text('내 정보를 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
          ),
        ),
        data: (profile) => _ProfileBody(profile: profile),
      ),
    );
  }
}

class _ProfileBody extends StatelessWidget {
  const _ProfileBody({required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  profile.name,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${_roleLabels[profile.role] ?? profile.role} · ${_statusLabels[profile.status] ?? profile.status}',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Column(
            children: [
              _InfoTile(icon: Icons.email_outlined, label: '이메일', value: profile.email),
              if (profile.employeeNumber != null)
                _InfoTile(icon: Icons.badge_outlined, label: '사번', value: profile.employeeNumber!),
              if (profile.phone != null)
                _InfoTile(icon: Icons.phone_outlined, label: '휴대전화', value: profile.phone!),
              if (profile.jobTitle != null)
                _InfoTile(icon: Icons.work_outline, label: '직급', value: profile.jobTitle!),
              if (profile.employmentType != null)
                _InfoTile(icon: Icons.badge_outlined, label: '고용형태', value: profile.employmentType!),
              if (profile.hireDate != null)
                _InfoTile(icon: Icons.event_outlined, label: '입사일', value: profile.hireDate!),
            ],
          ),
        ),
        const SizedBox(height: 20),
        FilledButton.tonalIcon(
          onPressed: () => context.push('/profile/change-password'),
          icon: const Icon(Icons.lock_reset_outlined),
          label: const Text('비밀번호 변경'),
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(48),
          ),
        ),
      ],
    );
  }
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
      title: Text(label, style: Theme.of(context).textTheme.labelMedium),
      subtitle: Text(value, style: Theme.of(context).textTheme.bodyLarge),
    );
  }
}
