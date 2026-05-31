import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:barcode_widget/barcode_widget.dart';

import '../../providers/db_provider.dart';
import '../../providers/delivery_provider.dart';
import '../../models/delivery_session.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final allCodes = ref.watch(savedCodesProvider);
    final favoriteCodes = allCodes.where((code) => code.isFavorite).toList();
    final sessionsAsync = ref.watch(deliverySessionsProvider);
    final statsAsync = ref.watch(dashboardStatsProvider);

    // Логіка визначення поточної зміни
    final now = DateTime.now();
    final currentHour = now.hour;
    String shiftName = 'Поза зміною / Ніч';
    int shiftStart = 0;
    int shiftEnd = 24;

    if (currentHour >= 6 && currentHour < 14) {
      shiftName = '1-ша зміна (06:00 - 14:00)';
      shiftStart = 6;
      shiftEnd = 14;
    } else if (currentHour >= 14 && currentHour < 22) {
      shiftName = '2-га зміна (14:00 - 22:00)';
      shiftStart = 14;
      shiftEnd = 22;
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text(' '),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // --- БЛОК 1: ПОТОЧНА ЗМІНА ---
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Сьогодні',
                    style: TextStyle(fontSize: 14, color: Colors.white54, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.orangeAccent.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.orangeAccent.withOpacity(0.5)),
                    ),
                    child: Text(
                      shiftName,
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.orangeAccent),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              
              sessionsAsync.when(
                data: (sessions) {
                  // Фільтрація загальної активності
                  final activeSessions = sessions.where((s) => !s.isArchived).toList();
                  int activePallets = 0;
                  for (var s in activeSessions) {
                    activePallets += s.totalCalculatedPallets;
                  }

                  // Фільтрація продуктивності за поточну зміну
                  int shiftPallets = 0;
                  int shiftArchivedBases = 0;

                  for (var s in sessions) {
                    // Перевіряємо, чи база створена сьогодні
                    if (s.createdAt.year == now.year && s.createdAt.month == now.month && s.createdAt.day == now.day) {
                      // Перевіряємо, чи потрапляє в години поточної зміни
                      if (s.createdAt.hour >= shiftStart && s.createdAt.hour < shiftEnd) {
                        shiftPallets += s.totalCalculatedPallets;
                        if (s.isArchived) shiftArchivedBases++;
                      }
                    }
                  }

                  return Column(
                    children: [
                      // Картки поточної зміни (Продуктивність)
                      Row(
                        children: [
                          Expanded(
                            child: _buildStatCard(
                              'Палет за\nзміну', 
                              '$shiftPallets', 
                              Colors.orangeAccent,
                              icon: Icons.inventory_2_outlined,
                            )
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: _buildStatCard(
                              'Завершено\nдоставок', 
                              '$shiftArchivedBases', 
                              Colors.orangeAccent,
                              icon: Icons.check_circle_outline,
                            )
                          ),
                        ],
                      ),
                      const SizedBox(height: 32),
                      
                      // --- БЛОК 2: ГЛОБАЛЬНА СТАТИСТИКА СКЛАДУ ---
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'ВСЬОГО В РОБОТІ',
                          style: TextStyle(fontSize: 14, color: Colors.white54, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                        ),
                      ),
                      const SizedBox(height: 16),
                      
                      Row(
                        children: [
                          Expanded(child: _buildStatCard('Активних\nдоставок', '${activeSessions.length}', Colors.blueAccent)),
                          const SizedBox(width: 16),
                          Expanded(child: _buildStatCard('Палет в\nпроцесі', '$activePallets', Colors.greenAccent)),
                        ],
                      ),
                      const SizedBox(height: 16),
                      
                      // Кнопка глобального очищення пам'яті
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.redAccent.withOpacity(0.05),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.redAccent.withOpacity(0.3)),
                        ),
                        child: Column(
                          children: [
                            const Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(Icons.cleaning_services_rounded, color: Colors.redAccent, size: 24),
                                SizedBox(width: 8),
                                Text(
                                  'Очищення пам\'яті', 
                                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'Видалити всі бази приймання, сфотографовані накладні та історію розрахунків палет.', 
                              textAlign: TextAlign.center,
                              style: TextStyle(color: Colors.white54, fontSize: 13),
                            ),
                            const SizedBox(height: 16),
                            ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.redAccent,
                                foregroundColor: Colors.white,
                                minimumSize: const Size.fromHeight(45),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                              ),
                              onPressed: sessions.isEmpty ? null : () => _showClearConfirmation(context, ref, sessions),
                              child: const Text('Видалити всі дані', style: TextStyle(fontWeight: FontWeight.bold)),
                            )
                          ],
                        ),
                      ),
                    ],
                  );
                },
                loading: () => const Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
                error: (e, _) => Center(child: Text('Помилка: $e', style: const TextStyle(color: Colors.redAccent))),
              ),
              
              const SizedBox(height: 32),

              // --- БЛОК: РЕКОРДИ СКЛАДУ ---
              statsAsync.when(
                data: (stats) => _buildRecordsWidget(stats),
                loading: () => const Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
                error: (err, stack) => Text('Помилка: $err', style: const TextStyle(color: Colors.redAccent)),
              ),

              const SizedBox(height: 32),
              
              // --- БЛОК 3: ВИБРАНІ КОДИ ---
              const Text(
                'ВИБРАНІ КОДИ',
                style: TextStyle(fontSize: 14, color: Colors.white54, fontWeight: FontWeight.bold, letterSpacing: 1.2),
              ),
              const SizedBox(height: 16),
              
              if (favoriteCodes.isEmpty)
                const Center(
                  child: Padding(
                    padding: EdgeInsets.all(16.0),
                    child: Text(
                      'Немає вибраних кодів.\nПерейдіть у Генератор і натисніть зірочку біля потрібного коду.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white54, fontSize: 16),
                    ),
                  ),
                )
              else
                GridView.builder(
                  shrinkWrap: true, 
                  physics: const NeverScrollableScrollPhysics(), 
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2, 
                    crossAxisSpacing: 16,
                    mainAxisSpacing: 16,
                    childAspectRatio: 0.9, 
                  ),
                  itemCount: favoriteCodes.length,
                  itemBuilder: (context, index) {
                    final item = favoriteCodes[index];
                    return InkWell(
                      borderRadius: BorderRadius.circular(16),
                      onTap: () => _showCodeDialog(context, item.data, item.isQr),
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: const Color(0xFF1E1E1E), 
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.white12),
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: Colors.white,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: item.isQr
                                  ? QrImageView(data: item.data, size: 80)
                                  : BarcodeWidget(
                                      barcode: Barcode.code128(),
                                      data: item.data,
                                      width: 100,
                                      height: 40,
                                      drawText: false, 
                                    ),
                            ),
                            const SizedBox(height: 12),
                            Text(
                              item.data,
                              style: const TextStyle(fontWeight: FontWeight.bold),
                              textAlign: TextAlign.center,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }
  Widget _buildRecordsWidget(Map<String, dynamic> stats) {
    final fastest = stats['fastest'] as DeliverySession?;
    final longest = stats['longest'] as DeliverySession?;
    final maxPallets = stats['maxPallets'] as DeliverySession?;
    final maxSkus = stats['maxSkus'] as DeliverySession?;
    final maxSkuCount = stats['maxSkuCount'] as int? ?? 0;

    String formatDuration(Duration? d) {
      if (d == null) return '--';
      final hours = d.inHours;
      final minutes = d.inMinutes.remainder(60);
      if (hours > 0) return '${hours}г ${minutes}хв';
      return '${minutes}хв';
    }

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E1E1E),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.greenAccent.withOpacity(0.5)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.emoji_events, color: Colors.amber),
              SizedBox(width: 8),
              Text('Рекорди складу', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
            ],
          ),
          const Divider(color: Colors.white24, height: 24),
          _buildRecordRow('⚡ Найшвидша вигрузка', fastest != null ? '${fastest.name} (${formatDuration(fastest.endTime!.difference(fastest.startTime!))})' : 'Немає даних', Colors.greenAccent),
          _buildRecordRow('🐢 Найдовша вигрузка', longest != null ? '${longest.name} (${formatDuration(longest.endTime!.difference(longest.startTime!))})' : 'Немає даних', Colors.redAccent),
          const SizedBox(height: 12),
          _buildRecordRow('📦 Максимум палет', maxPallets != null ? '${maxPallets.name} (${maxPallets.totalCalculatedPallets} шт)' : 'Немає даних', Colors.blueAccent),
          _buildRecordRow('📋 Максимум SKU', maxSkus != null ? '${maxSkus.name} ($maxSkuCount SKU)' : 'Немає даних', Colors.orangeAccent),
        ],
      ),
    );
  }

  Widget _buildRecordRow(String title, String value, Color iconColor) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: const TextStyle(color: Colors.white70, fontSize: 14)),
          Text(value, style: TextStyle(color: iconColor, fontSize: 14, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
  Widget _buildStatCard(String title, String value, Color color, {IconData? icon}) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        children: [
          if (icon != null) ...[
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 8),
          ],
          Text(value, style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: color)),
          const SizedBox(height: 8),
          Text(title, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70, height: 1.2)),
        ],
      ),
    );
  }

  void _showClearConfirmation(BuildContext context, WidgetRef ref, List sessions) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        title: const Text('Увага!', style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
        content: const Text('Ви впевнені, що хочете безповоротно видалити всі збережені бази, фотографії та розрахунки?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Скасувати', style: TextStyle(color: Colors.white54)),
          ),
          TextButton(
            onPressed: () {
              for (var session in sessions) {
                ref.read(deliveryControllerProvider).deleteSession(session.id);
              }
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Усі дані успішно очищено'), backgroundColor: Colors.greenAccent),
              );
            },
            child: const Text('Так, видалити', style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  void _showCodeDialog(BuildContext context, String data, bool isQr) {
    showDialog(
      context: context,
      builder: (context) {
        return Dialog(
          backgroundColor: Colors.white, 
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
          child: Padding(
            padding: const EdgeInsets.all(32.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                isQr
                    ? QrImageView(data: data, size: 250)
                    : BarcodeWidget(
                        barcode: Barcode.code128(),
                        data: data,
                        width: 250,
                        height: 100,
                      ),
                const SizedBox(height: 32),
                Text(
                  data,
                  style: const TextStyle(
                    fontSize: 24, 
                    fontWeight: FontWeight.bold, 
                    color: Colors.black 
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.black, 
                  ),
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Закрити', style: TextStyle(color: Colors.white)),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}