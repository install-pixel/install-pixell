import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'features/dashboard/dashboard_screen.dart';
import 'features/receiving/receiving_screen.dart';
import 'features/new_product/new_product_screen.dart';
import 'features/generator/generator_screen.dart';
import 'features/calculator/quick_calculator_screen.dart';
import 'features/calculator/global_calculator_sheet.dart';

final bottomNavIndexProvider = StateProvider<int>((ref) => 0);

class MainLayout extends ConsumerWidget {
  const MainLayout({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentIndex = ref.watch(bottomNavIndexProvider);

    // Список усіх наших екранів
    final screens = const [
      DashboardScreen(),
      ReceivingScreen(),
      QuickCalculatorScreen(),
      NewProductScreen(), 
      GeneratorScreen(),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Sklado', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.calculate_outlined, color: Colors.greenAccent),
            tooltip: 'Глобальний калькулятор',
            onPressed: () {
              GlobalCalculatorSheet.show(context);
            },
          ),
        ],
      ),
      
      // ОСЬ ГОЛОВНА МАГІЯ: IndexedStack тримає всі екрани в пам'яті одночасно!
      body: IndexedStack(
        index: currentIndex,
        children: screens,
      ),
      
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: currentIndex,
        backgroundColor: const Color(0xFF121212),
        selectedItemColor: Colors.greenAccent,
        unselectedItemColor: Colors.white38,
        type: BottomNavigationBarType.fixed, 
        selectedFontSize: 12, 
        unselectedFontSize: 10,
        onTap: (index) {
          ref.read(bottomNavIndexProvider.notifier).state = index;
        },
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.dashboard_rounded),
            label: 'Головна',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.inventory_2_rounded),
            label: 'Прийомка',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.calculate_rounded), 
            label: 'Калькулятор',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.add_box_rounded), 
            label: 'Новий продукт',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.qr_code_scanner_rounded),
            label: 'Генератор',
          ),
        ],
      ),
    );
  }
}