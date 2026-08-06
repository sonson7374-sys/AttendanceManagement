class Workplace {
  const Workplace({
    required this.id,
    required this.name,
    required this.latitude,
    required this.longitude,
    required this.radiusMeters,
  });

  final int id;
  final String name;
  final double latitude;
  final double longitude;
  final int radiusMeters;

  factory Workplace.fromJson(Map<String, dynamic> json) => Workplace(
        id: json['id'] as int,
        name: json['name'] as String,
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        radiusMeters: json['radiusMeters'] as int? ?? 0,
      );
}
