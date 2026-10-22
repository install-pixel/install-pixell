import 'package:isar/isar.dart';

part 'calculation_history.g.dart'; // Цей рядок поки буде світитися червоним, це нормально

@collection
class CalculationHistory {
  Id id = Isar.autoIncrement; // Автоматичний унікальний номер запису

  DateTime createdAt = DateTime.now(); // Точний час збереження (щоб шукати по даті)

  String? sku; // Номер продукту

  // Вхідні дані, які ми вводили
  double? boxLength;
  double? boxWidth;
  double? boxHeight;
  double? boxWeight;
  int? unitsInBox;
  double? heightLimit; // 180 або 130

  // Результати обчислення
  int? totalBoxesOnPallet;
  int? boxesPerLayer;
  int? layersCount;
  double? totalPalletHeight;
  double? totalPalletWeight;
}