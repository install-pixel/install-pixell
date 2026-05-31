import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:isar/isar.dart';
import '../models/general_note.dart';
import 'db_provider.dart';

// Стрім, який повертає ВСІ нотатки, відсортовані від найновіших до старіших
final generalNotesListProvider = StreamProvider.autoDispose<List<GeneralNote>>((ref) {
  final isar = ref.watch(isarProvider);
  return isar.generalNotes.where().sortByCreatedAtDesc().watch(fireImmediately: true);
});

class GeneralNoteController {
  final Isar isar;
  GeneralNoteController(this.isar);

  // Створення нової окремої нотатки
  Future<void> saveNote(String text) async {
    if (text.trim().isEmpty) return;
    
    await isar.writeTxn(() async {
      final note = GeneralNote()
        ..textContent = text
        ..createdAt = DateTime.now();
        
      await isar.generalNotes.put(note);
    });
  }

  // Видалення конкретної нотатки за її ID
  Future<void> deleteNote(int id) async {
    await isar.writeTxn(() async {
      await isar.generalNotes.delete(id);
    });
  }
}

final generalNoteControllerProvider = Provider((ref) => GeneralNoteController(ref.watch(isarProvider)));