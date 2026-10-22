import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:isar/isar.dart';

import '../../providers/db_provider.dart';
import '../../models/calculation_history.dart';

final historyStreamProvider = StreamProvider.autoDispose<List<CalculationHistory>>((ref) {
  final isar = ref.watch(isarProvider);
  return isar.calculationHistorys.where().sortByCreatedAtDesc().watch(fireImmediately: true);
});

class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final historyAsync = ref.watch(historyStreamProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Історія розрахунків'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_sweep, color: Colors.redAccent),
            tooltip: 'Очистити все',
            onPressed: () => _showClearAllDialog(context, ref),
          ),
        ],
      ),
      body: historyAsync.when(
        data: (history) {
          if (history.isEmpty) {
            return const Center(
              child: Text(
                'Історія порожня',
                style: TextStyle(color: Colors.white54, fontSize: 16),
              ),
            );
          }
          return ListView.builder(
            itemCount: history.length,
            itemBuilder: (context, index) {
              final item = history[index];
              return Dismissible(
                key: ValueKey(item.id), 
                direction: DismissDirection.endToStart, 
                background: Container(
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.only(right: 20),
                  color: Colors.redAccent,
                  child: const Icon(Icons.delete, color: Colors.white),
                ),
                onDismissed: (_) {
                  _deleteItem(ref, item.id);
                },
                child: Card(
                  color: const Color(0xFF1E1E1E),
                  margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: ListTile(
                    title: Text(
                      item.sku ?? 'Невідомий SKU',
                      style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.greenAccent, fontSize: 18),
                    ),
                    subtitle: Padding(
                      padding: const EdgeInsets.only(top: 8.0),
                      child: Text(
                        '${item.createdAt.day.toString().padLeft(2, '0')}.${item.createdAt.month.toString().padLeft(2, '0')} ${item.createdAt.hour.toString().padLeft(2, '0')}:${item.createdAt.minute.toString().padLeft(2, '0')}\n'
                        'Палета: ${item.totalBoxesOnPallet} кор. | Вага: ${item.totalPalletWeight?.toStringAsFixed(1)} кг',
                        style: const TextStyle(color: Colors.white70, height: 1.4),
                      ),
                    ),
                    trailing: const Icon(Icons.visibility, color: Colors.white38, size: 20), 
                    
                    // ДОДАНО: Дія при натисканні на запис
                    onTap: () => _showDetails(context, item),
                  ),
                ),
              );
            },
          );
        },
        loading: () => const Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
        error: (err, stack) => Center(child: Text('Помилка: $err')),
      ),
    );
  }

  // ДОДАНО: Нижня панель із детальною інформацією
  void _showDetails(BuildContext context, CalculationHistory item) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1E1E1E),
      isScrollControlled: true, // Дозволяє панелі бути високою
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        return Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Маленька риска зверху (індикатор свайпу)
              Center(
                child: Container(
                  width: 40, height: 5,
                  decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(10)),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                item.sku ?? 'Невідомий SKU',
                style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.greenAccent),
              ),
              const SizedBox(height: 8),
              Text(
                'Збережено: ${item.createdAt.day.toString().padLeft(2, '0')}.${item.createdAt.month.toString().padLeft(2, '0')}.${item.createdAt.year} о ${item.createdAt.hour.toString().padLeft(2, '0')}:${item.createdAt.minute.toString().padLeft(2, '0')}',
                style: const TextStyle(color: Colors.white54),
              ),
              const Divider(height: 32, color: Colors.white24),
              
              const Text('ВХІДНІ ДАНІ (КОРОБКА)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white38)),
              const SizedBox(height: 12),
              _buildDetailRow('Габарити (ДхШхВ):', '${item.boxLength} x ${item.boxWidth} x ${item.boxHeight} м'),
              _buildDetailRow('Вага 1 коробки:', '${item.boxWeight} кг'),
              _buildDetailRow('Одиниць у коробці:', '${item.unitsInBox} шт'),
              _buildDetailRow('Ліміт висоти палети:', '${item.heightLimit?.toInt()} см'),
              
              const Divider(height: 32, color: Colors.white24),
              
              const Text('РЕЗУЛЬТАТ РОЗКЛАДКИ ', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white38)),
              const SizedBox(height: 12),
              _buildDetailRow('Коробок у шарі :', '${item.boxesPerLayer}'),
              _buildDetailRow('Одиниць у шарі:', '${(item.boxesPerLayer ?? 0) * (item.unitsInBox ?? 0)} шт'),
              _buildDetailRow('Кількість шарів :', '${item.layersCount}'),
              _buildDetailRow('Всього коробок:', '${item.totalBoxesOnPallet}'),
              _buildDetailRow('Всього одиниць товару:', '${(item.totalBoxesOnPallet ?? 0) * (item.unitsInBox ?? 0)}'),
              _buildDetailRow('Загальна вага нетто:', '${item.totalPalletWeight?.toStringAsFixed(1)} кг'),
              _buildDetailRow('Загальна висота:', '${item.totalPalletHeight?.toStringAsFixed(2)} м'),
              
              const SizedBox(height: 32),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white10,
                    foregroundColor: Colors.white,
                    elevation: 0,
                  ),
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Закрити', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              )
            ],
          ),
        );
      }
    );
  }

  // Допоміжний віджет для красивого виводу рядків
  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white70, fontSize: 16)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 16)),
        ],
      ),
    );
  }

  void _deleteItem(WidgetRef ref, int id) async {
    final isar = ref.read(isarProvider);
    await isar.writeTxn(() async {
      await isar.calculationHistorys.delete(id);
    });
  }

  void _showClearAllDialog(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        title: const Text('Очистити історію?'),
        content: const Text('Ви дійсно хочете видалити всі збережені розрахунки? Цю дію неможливо скасувати.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Скасувати', style: TextStyle(color: Colors.white54)),
          ),
          TextButton(
            onPressed: () async {
              final isar = ref.read(isarProvider);
              await isar.writeTxn(() async {
                await isar.calculationHistorys.clear();
              });
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Видалити все', style: TextStyle(color: Colors.redAccent)),
          ),
        ],
      ),
    );
  }
}