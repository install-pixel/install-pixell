import 'package:isar/isar.dart';
import 'package:path_provider/path_provider.dart';
import '../models/calculation_history.dart';

class DatabaseService {
  late Isar isar;

  // 1. Відкриття (ініціалізація) бази даних
  Future<void> init() async {
    final dir = await getApplicationDocumentsDirectory();
    isar = await Isar.open(
      [CalculationHistorySchema], // Сюди ми передаємо наш згенерований шаблон
      directory: dir.path,
    );
  }

  // 2. Збереження нового обчислення в історію
  Future<void> saveCalculation(CalculationHistory item) async {
    await isar.writeTxn(() async {
      await isar.calculationHistorys.put(item);
    });
  }

  // 3. Отримання всієї історії (від нових записів до старих)
  Future<List<CalculationHistory>> getHistory() async {
    return await isar.calculationHistorys
        .where()
        .sortByCreatedAtDesc()
        .findAll();
  }

  // 4. Видалення одного запису (будемо викликати при Swipe-to-delete)
  Future<void> deleteCalculation(int id) async {
    await isar.writeTxn(() async {
      await isar.calculationHistorys.delete(id);
    });
  }

  // 5. Повне очищення історії (для кнопки "Видалити все")
  Future<void> clearAllHistory() async {
    await isar.writeTxn(() async {
      await isar.calculationHistorys.clear();
    });
  }
}