import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/delivery_provider.dart';
import 'session_detail_screen.dart';

class ReceivingScreen extends ConsumerStatefulWidget {
  const ReceivingScreen({super.key});

  @override
  ConsumerState<ReceivingScreen> createState() => _ReceivingScreenState();
}

class _ReceivingScreenState extends ConsumerState<ReceivingScreen> {
  void _showCreateSessionDialog() {
    int selectedRamp = 150;
    String selectedType = 'Контейнер';
    final nameController = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF1E1E1E),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).viewInsets.bottom,
                left: 20, right: 20, top: 24,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Нова база приймання', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white)),
                  const SizedBox(height: 16),
                  TextField(
                    controller: nameController,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Назва ',
                      filled: true,
                      fillColor: Colors.black26,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<int>(
                    value: selectedRamp,
                    dropdownColor: const Color(0xFF1E1E1E),
                    style: const TextStyle(color: Colors.white),
                    items: List.generate(11, (i) => 150 + i).map((ramp) {
                      return DropdownMenuItem(value: ramp, child: Text('Рампа $ramp'));
                    }).toList(),
                    onChanged: (val) => setModalState(() => selectedRamp = val!),
                    decoration: InputDecoration(
                      labelText: 'Номер рампи',
                      filled: true,
                      fillColor: Colors.black26,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String>(
                    value: selectedType,
                    dropdownColor: const Color(0xFF1E1E1E),
                    style: const TextStyle(color: Colors.white),
                    items: ['Контейнер', 'Мікс', 'Сінгл', 'Мікс + Сінгл'].map((type) {
                      return DropdownMenuItem(value: type, child: Text(type));
                    }).toList(),
                    onChanged: (val) => setModalState(() => selectedType = val!),
                    decoration: InputDecoration(
                      labelText: 'Тип доставки',
                      filled: true,
                      fillColor: Colors.black26,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.greenAccent,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                      ),
                      onPressed: () {
                        ref.read(deliveryControllerProvider).createSession(
                          nameController.text.isEmpty ? 'Рампа $selectedRamp' : nameController.text,
                          selectedRamp,
                          selectedType,
                        );
                        Navigator.pop(context);
                      },
                      child: const Text('Створити', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 16)),
                    ),
                  ),
                  const SizedBox(height: 24),
                ],
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final sessionsAsync = ref.watch(deliverySessionsProvider);

    // Додаємо контролер вкладок
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('База приймання'),
          bottom: const TabBar(
            indicatorColor: Colors.greenAccent,
            labelColor: Colors.greenAccent,
            unselectedLabelColor: Colors.white38,
            tabs: [
              Tab(text: 'В процесі'),
              Tab(text: 'Архів'),
            ],
          ),
        ),
        body: sessionsAsync.when(
          data: (sessions) {
            // Фільтруємо бази для кожної вкладки
            final activeSessions = sessions.where((s) => !s.isArchived).toList();
            final archivedSessions = sessions.where((s) => s.isArchived).toList();

            return TabBarView(
              children: [
                _buildSessionList(activeSessions, isArchiveTab: false),
                _buildSessionList(archivedSessions, isArchiveTab: true),
              ],
            );
          },
          loading: () => const Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
          error: (err, stack) => Center(child: Text('Помилка: $err')),
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _showCreateSessionDialog,
          backgroundColor: Colors.greenAccent,
          icon: const Icon(Icons.add, color: Colors.black),
          label: const Text('Створити базу', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
        ),
      ),
    );
  }

  // Окремий віджет для побудови списку (щоб не дублювати код для обох вкладок)
  Widget _buildSessionList(List sessions, {required bool isArchiveTab}) {
    if (sessions.isEmpty) {
      return Center(
        child: Text(
          isArchiveTab ? 'Архів порожній' : 'Немає активних сесій',
          style: const TextStyle(color: Colors.white54),
        ),
      );
    }

    return ListView.builder(
      itemCount: sessions.length,
      padding: const EdgeInsets.only(bottom: 80, top: 8),
      itemBuilder: (context, index) {
        final session = sessions[index];
        
        return Dismissible(
          key: ValueKey(session.id),
          direction: DismissDirection.endToStart, // Свайп справа наліво
          background: Container(
            alignment: Alignment.centerRight,
            padding: const EdgeInsets.only(right: 20),
            margin: const EdgeInsets.symmetric(vertical: 8),
            color: Colors.redAccent,
            child: const Icon(Icons.delete, color: Colors.white),
          ),
          confirmDismiss: (direction) async {
            return await showDialog<bool>(
              context: context,
              builder: (ctx) => AlertDialog(
                backgroundColor: const Color(0xFF1E1E1E),
                title: const Text('Видалити базу приймання?'),
                content: Text('Ви дійсно хочете видалити "${session.name}"?\nУсі збережені фото накладних та розпізнані SKU всередині цієї сесії будуть безповоротно стерті.'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(ctx, false),
                    child: const Text('Скасувати', style: TextStyle(color: Colors.white54)),
                  ),
                  TextButton(
                    onPressed: () => Navigator.pop(ctx, true),
                    child: const Text('Видалити', style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
            );
          },
          onDismissed: (_) {
            ref.read(deliveryControllerProvider).deleteSession(session.id);
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('Сесію "${session.name}" успішно видалено')),
            );
          },
          child: Card(
            color: const Color(0xFF1E1E1E),
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: ListTile(
              title: Row(
                children: [
                  Expanded(
                    child: Text(
                      session.name ?? 'Без назви',
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                  ),
                  // БЕЙДЖ СТАТУСУ
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: isArchiveTab ? Colors.white10 : Colors.green.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: isArchiveTab ? Colors.white24 : Colors.greenAccent.withOpacity(0.5)),
                    ),
                    child: Text(
                      isArchiveTab ? 'ЗАВЕРШЕНО' : 'В ПРОЦЕСІ',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: isArchiveTab ? Colors.white54 : Colors.greenAccent,
                      ),
                    ),
                  ),
                ],
              ),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 8.0),
                child: Text(
                  '${session.deliveryType} • ${session.createdAt.day.toString().padLeft(2, '0')}.${session.createdAt.month.toString().padLeft(2, '0')} ${session.createdAt.hour.toString().padLeft(2, '0')}:${session.createdAt.minute.toString().padLeft(2, '0')}\nПалет пораховано: ${session.totalCalculatedPallets}',
                  style: const TextStyle(color: Colors.white54, fontSize: 13, height: 1.3),
                ),
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // КНОПКА АРХІВАЦІЇ
                  IconButton(
                    icon: Icon(
                      isArchiveTab ? Icons.unarchive_outlined : Icons.archive_outlined,
                      color: isArchiveTab ? Colors.white54 : Colors.white38,
                    ),
                    tooltip: isArchiveTab ? 'Повернути в роботу' : 'Перенести в архів',
                    onPressed: () {
                      ref.read(deliveryControllerProvider).toggleArchiveStatus(session.id);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(isArchiveTab ? 'Базу повернуто в роботу' : 'Базу перенесено в архів'),
                          duration: const Duration(seconds: 2),
                        ),
                      );
                    },
                  ),
                  const Icon(Icons.arrow_forward_ios, color: Colors.white38, size: 16),
                ],
              ),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => SessionDetailScreen(sessionId: session.id)),
                );
              },
            ),
          ),
        );
      },
    );
  }
}