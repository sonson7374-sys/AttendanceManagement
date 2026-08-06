import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../provider/auth_provider.dart';
import '../provider/biometric_provider.dart';

/// 생체인증 로그인이 활성화된 경우, 앱 시작 시 홈 화면 진입 전에 표시되는 잠금 화면.
class LockScreen extends ConsumerStatefulWidget {
  const LockScreen({super.key});

  @override
  ConsumerState<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends ConsumerState<LockScreen> {
  bool _authenticating = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _authenticate());
  }

  Future<void> _authenticate() async {
    if (_authenticating) return;
    setState(() {
      _authenticating = true;
      _error = null;
    });
    final service = ref.read(biometricServiceProvider);
    final success = await service.authenticate(reason: '출퇴근 관리 앱 잠금 해제');
    if (!mounted) return;
    if (success) {
      ref.read(biometricUnlockProvider.notifier).unlock();
    } else {
      setState(() => _error = '생체인증에 실패했습니다. 다시 시도해주세요.');
    }
    setState(() => _authenticating = false);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.fingerprint, size: 72, color: theme.colorScheme.primary),
              const SizedBox(height: 16),
              Text('생체인증으로 잠금을 해제해주세요', style: theme.textTheme.titleMedium),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
              ],
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: _authenticating ? null : _authenticate,
                icon: const Icon(Icons.fingerprint),
                label: Text(_authenticating ? '인증 중...' : '다시 시도'),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () async {
                  await ref.read(authNotifierProvider.notifier).logout();
                },
                child: const Text('다른 계정으로 로그인'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
