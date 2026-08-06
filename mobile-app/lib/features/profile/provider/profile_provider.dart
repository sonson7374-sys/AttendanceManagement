import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/profile_model.dart';
import '../repository/profile_repository.dart';

final profileRepositoryProvider =
    Provider<ProfileRepository>((_) => ProfileRepository());

final myProfileProvider = FutureProvider.autoDispose<UserProfile>((ref) {
  final repo = ref.read(profileRepositoryProvider);
  return repo.getMyProfile();
});
