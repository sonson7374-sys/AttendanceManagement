enum CalendarEventCategory { meeting, event, notice, etc }

extension CalendarEventCategoryX on CalendarEventCategory {
  static CalendarEventCategory fromApi(String value) => switch (value) {
        'MEETING' => CalendarEventCategory.meeting,
        'EVENT' => CalendarEventCategory.event,
        'NOTICE' => CalendarEventCategory.notice,
        _ => CalendarEventCategory.etc,
      };

  String toApi() => switch (this) {
        CalendarEventCategory.meeting => 'MEETING',
        CalendarEventCategory.event => 'EVENT',
        CalendarEventCategory.notice => 'NOTICE',
        CalendarEventCategory.etc => 'ETC',
      };

  String get label => switch (this) {
        CalendarEventCategory.meeting => '회의',
        CalendarEventCategory.event => '행사',
        CalendarEventCategory.notice => '공지',
        CalendarEventCategory.etc => '기타',
      };
}

enum CalendarEventVisibility { all, personal }

extension CalendarEventVisibilityX on CalendarEventVisibility {
  static CalendarEventVisibility fromApi(String value) =>
      value == 'PERSONAL' ? CalendarEventVisibility.personal : CalendarEventVisibility.all;

  String toApi() => this == CalendarEventVisibility.personal ? 'PERSONAL' : 'ALL';

  String get label => this == CalendarEventVisibility.personal ? '개인' : '전체';
}

class CalendarEvent {
  const CalendarEvent({
    required this.id,
    required this.title,
    required this.startAt,
    required this.endAt,
    required this.allDay,
    this.description,
    this.location,
    this.color,
    required this.category,
    required this.visibility,
    this.targetUserId,
    this.targetUserName,
    required this.createdBy,
    this.createdByName,
    required this.createdAt,
    required this.updatedAt,
  });

  final int id;
  final String title;
  final DateTime startAt;
  final DateTime endAt;
  final bool allDay;
  final String? description;
  final String? location;
  final String? color;
  final CalendarEventCategory category;
  final CalendarEventVisibility visibility;
  final int? targetUserId;
  final String? targetUserName;
  final int createdBy;
  final String? createdByName;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory CalendarEvent.fromJson(Map<String, dynamic> json) => CalendarEvent(
        id: json['id'] as int,
        title: json['title'] as String,
        startAt: DateTime.parse(json['startAt'] as String),
        endAt: DateTime.parse(json['endAt'] as String),
        allDay: json['allDay'] as bool,
        description: json['description'] as String?,
        location: json['location'] as String?,
        color: json['color'] as String?,
        category: CalendarEventCategoryX.fromApi(json['category'] as String),
        visibility: CalendarEventVisibilityX.fromApi(json['visibility'] as String),
        targetUserId: json['targetUserId'] as int?,
        targetUserName: json['targetUserName'] as String?,
        createdBy: json['createdBy'] as int,
        createdByName: json['createdByName'] as String?,
        createdAt: DateTime.parse(json['createdAt'] as String),
        updatedAt: DateTime.parse(json['updatedAt'] as String),
      );
}

class CalendarEventPayload {
  const CalendarEventPayload({
    required this.title,
    required this.startAt,
    required this.endAt,
    required this.allDay,
    this.description,
    this.location,
    this.color,
    required this.category,
    required this.visibility,
  });

  final String title;
  final DateTime startAt;
  final DateTime endAt;
  final bool allDay;
  final String? description;
  final String? location;
  final String? color;
  final CalendarEventCategory category;
  final CalendarEventVisibility visibility;

  Map<String, dynamic> toJson() => {
        'title': title,
        'startAt': startAt.toUtc().toIso8601String(),
        'endAt': endAt.toUtc().toIso8601String(),
        'allDay': allDay,
        if (description != null && description!.isNotEmpty) 'description': description,
        if (location != null && location!.isNotEmpty) 'location': location,
        'color': color,
        'category': category.toApi(),
        'visibility': visibility.toApi(),
      };
}
