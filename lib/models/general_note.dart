import 'package:isar/isar.dart';

part 'general_note.g.dart';

@collection
class GeneralNote {
  Id id = Isar.autoIncrement;
  
  String textContent = '';
  DateTime createdAt = DateTime.now();
}