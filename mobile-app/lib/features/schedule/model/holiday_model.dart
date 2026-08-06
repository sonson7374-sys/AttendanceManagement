enum HolidayType { public, substitute, company, weekend }

extension HolidayTypeX on HolidayType {
  static HolidayType fromApi(String value) => switch (value) {
        'PUBLIC' => HolidayType.public,
        'SUBSTITUTE' => HolidayType.substitute,
        'COMPANY' => HolidayType.company,
        _ => HolidayType.weekend,
      };

  String get label => switch (this) {
        HolidayType.public => '공휴일',
        HolidayType.substitute => '대체공휴일',
        HolidayType.company => '회사휴일',
        HolidayType.weekend => '주말',
      };
}

class Holiday {
  const Holiday({required this.id, required this.holidayDate, required this.name, required this.holidayType});

  final int id;
  final DateTime holidayDate;
  final String name;
  final HolidayType holidayType;

  factory Holiday.fromJson(Map<String, dynamic> json) => Holiday(
        id: json['id'] as int,
        holidayDate: DateTime.parse(json['holidayDate'] as String),
        name: json['name'] as String,
        holidayType: HolidayTypeX.fromApi(json['holidayType'] as String),
      );
}
