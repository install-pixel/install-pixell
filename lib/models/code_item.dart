import 'package:isar/isar.dart';

// Цей рядок обов'язковий. Він каже системі, що вона має сама дописати 
// складний код для бази даних у файл code_item.g.dart
part 'code_item.g.dart'; 

@collection
class CodeItem {
  Id id = Isar.autoIncrement; // Унікальний номер запису (1, 2, 3...)

  late String data; // Сам текст коду (наприклад "DSV-123")
  
  late bool isQr; // true, якщо це QR-код, і false, якщо штрихкод
  
  bool isFavorite = false; // Чи додали ми його на Головний екран? (за замовчуванням - ні)
  
  late DateTime createdAt; // Дата та час створення, щоб сортувати історію
}