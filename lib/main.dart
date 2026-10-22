import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:isar/isar.dart';
import 'package:path_provider/path_provider.dart';
import 'models/delivery_session.dart';
import 'models/general_note.dart';

import 'main_layout.dart';
import 'models/code_item.dart';
import 'providers/db_provider.dart';
import 'models/calculation_history.dart';

void main() async {
  // Цей рядок обов'язковий, якщо ми робимо щось важке до запуску інтерфейсу
  WidgetsFlutterBinding.ensureInitialized();
  
  // Знаходимо дозволену папку на телефоні для збереження даних
  final dir = await getApplicationDocumentsDirectory();
  
  // Відкриваємо базу даних Isar
  final isar = await Isar.open(
    [CodeItemSchema, DeliverySessionSchema, CalculationHistorySchema, DocumentPhotoSchema, PalletCalculationSchema, GeneralNoteSchema], // Передаємо схему нашої таблиці
    directory: dir.path,
  );

  runApp(
    ProviderScope(
      // Передаємо відкриту базу даних у наш провайдер, щоб інші файли мали до неї доступ
      overrides: [
        isarProvider.overrideWithValue(isar),
      ],
      child: const WmsApp(),
    ),
  );
}

class WmsApp extends StatelessWidget {
  const WmsApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WMS Core',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Colors.black,
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.black,
          elevation: 0,
          centerTitle: true,
        ),
        colorScheme: const ColorScheme.dark(
          primary: Colors.white,
          secondary: Colors.greenAccent,
          error: Colors.redAccent,
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            minimumSize: const Size.fromHeight(56),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        ),
      ),
      home: const MainLayout(),
    );
  }
}