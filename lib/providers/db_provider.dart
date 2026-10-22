import 'package:isar/isar.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/code_item.dart';

// 1. Провайдер, який зберігає підключення до самої бази даних (ми запустимо її в main.dart)
final isarProvider = Provider<Isar>((ref) {
  throw UnimplementedError('База даних не ініціалізована');
});

// 2. Провайдер, який керує списком збережених кодів і реагує на зміни
final savedCodesProvider = StateNotifierProvider<SavedCodesNotifier, List<CodeItem>>((ref) {
  final isar = ref.watch(isarProvider);
  return SavedCodesNotifier(isar);
});

// Клас-менеджер, який виконує всі дії з кодами
class SavedCodesNotifier extends StateNotifier<List<CodeItem>> {
  final Isar isar;

  SavedCodesNotifier(this.isar) : super([]) {
    loadCodes(); // Одразу завантажуємо всі коди з пам'яті телефону при старті
  }

  // Завантажити всі коди (сортуємо від найновіших до старих)
  Future<void> loadCodes() async {
    state = await isar.codeItems.where().sortByCreatedAtDesc().findAll();
  }

  // Додати новий код в базу
  Future<void> addCode(String data, bool isQr) async {
    final newItem = CodeItem()
      ..data = data
      ..isQr = isQr
      ..createdAt = DateTime.now();

    await isar.writeTxn(() async {
      await isar.codeItems.put(newItem); // Записуємо
    });
    await loadCodes(); // Оновлюємо екран
  }

  // Видалити код з бази
  Future<void> deleteCode(Id id) async {
    await isar.writeTxn(() async {
      await isar.codeItems.delete(id);
    });
    await loadCodes();
  }

  // Перемикач "Додати в Обране / Видалити з Обраного"
  Future<void> toggleFavorite(Id id) async {
    final item = await isar.codeItems.get(id);
    if (item != null) {
      item.isFavorite = !item.isFavorite; // Міняємо статус на протилежний
      await isar.writeTxn(() async {
        await isar.codeItems.put(item);
      });
      await loadCodes();
    }
  }
}