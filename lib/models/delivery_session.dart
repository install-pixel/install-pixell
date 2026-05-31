import 'package:isar/isar.dart';

part 'delivery_session.g.dart';

@collection
class DeliverySession {
  Id id = Isar.autoIncrement;

  String? name;
  int? rampNumber;
  String? deliveryType;

  int totalCalculatedPallets = 0;
  bool isArchived = false;

  bool isCompleted = false;
  DateTime? startTime;
  DateTime? endTime;

  DateTime createdAt = DateTime.now();
}

@collection
class DocumentPhoto {
  Id id = Isar.autoIncrement;
  int? sessionId;
  String? imagePath;
  @Index(type: IndexType.value)
  List<String>? recognizedText;
  bool isDone = false;
  String? photoType;
  String? comment;
  DateTime addedAt = DateTime.now();
}

@collection
class PalletCalculation {
  Id id = Isar.autoIncrement;
  int? sessionId;
  int? photoId;

  String? sku;
  
  // --- ПОЛЯ ДЛЯ АВТОМАТИЧНОГО РОЗРАХУНКУ ---
  int? totalUnits;
  int? unitsPerBox;
  int? boxesPerPallet;
  int? fullPallets;
  int? remainderBoxes;
  int? looseUnits;

  // --- ПОЛЯ ДЛЯ РУЧНОГО РОЗРАХУНКУ (НОВІ) ---
  bool isManual = false;
  int? expectedUnits;
  int? acceptedUnits;
  String? carrierType; // 'pallet' (палета) або 'wozek' (вузек)

  DateTime createdAt = DateTime.now();
}