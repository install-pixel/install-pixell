import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'dart:math';

// Виправлені шляхи до твоїх папок
import '../../providers/db_provider.dart'; 
import '../../models/calculation_history.dart'; 
import 'history_screen.dart';

class NewProductScreen extends ConsumerStatefulWidget {
  const NewProductScreen({super.key});

  @override
  ConsumerState<NewProductScreen> createState() => _NewProductScreenState();
}

class _NewProductScreenState extends ConsumerState<NewProductScreen> {
  final _skuCtrl = TextEditingController(); 
  final _boxLengthCtrl = TextEditingController();
  final _boxWidthCtrl = TextEditingController();
  final _boxHeightCtrl = TextEditingController();
  final _boxWeightCtrl = TextEditingController();
  final _unitsPerBoxCtrl = TextEditingController(); 

  // --- НОВІ ЗМІННІ ДЛЯ РУЧНОГО ВВОДУ ШАРУ ---
  bool _isManualLayer = false;
  final _manualLayerCtrl = TextEditingController();
  // ------------------------------------------

  int _maxHeightLimit = 180;
  final double _maxWeightLimit = 700.0;

  int _boxesPerLayer = 0;
  int _finalTotalBoxes = 0;
  int _finalLayers = 0;
  
  int _unitsPerLayer = 0;
  int _finalTotalUnits = 0;

  double _totalCalculatedWeight = 0.0;
  double _totalCalculatedHeightMeters = 0.0; 
  String _weightWarning = '';

  @override
  void dispose() {
    _skuCtrl.dispose();
    _boxLengthCtrl.dispose();
    _boxWidthCtrl.dispose();
    _boxHeightCtrl.dispose();
    _boxWeightCtrl.dispose();
    _unitsPerBoxCtrl.dispose();
    _manualLayerCtrl.dispose(); // Додано очищення нового контролера
    super.dispose();
  }

  void _calculateTiHi() {
    final double boxLMeters = double.tryParse(_boxLengthCtrl.text.replaceAll(',', '.')) ?? 0;
    final double boxWMeters = double.tryParse(_boxWidthCtrl.text.replaceAll(',', '.')) ?? 0;
    final double boxHMeters = double.tryParse(_boxHeightCtrl.text.replaceAll(',', '.')) ?? 0;
    final double boxWeight = double.tryParse(_boxWeightCtrl.text.replaceAll(',', '.')) ?? 0;
    final int unitsPerBox = int.tryParse(_unitsPerBoxCtrl.text) ?? 0;

    if (boxLMeters <= 0 || boxWMeters <= 0 || boxHMeters <= 0 || boxWeight <= 0 || unitsPerBox <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Заповніть коректно всі поля (> 0)!'), backgroundColor: Colors.redAccent),
      );
      return;
    }

    final double boxL = boxLMeters * 100;
    final double boxW = boxWMeters * 100;
    final double boxH = boxHMeters * 100;

    final double palL = 120.0;
    final double palW = 80.0;
    final double woodenPalletHeight = 15.0;

    final double maxBoxSide = max(boxL, boxW);
    final double minBoxSide = min(boxL, boxW);

    if (maxBoxSide > palL || minBoxSide > palW) {
      // Якщо ввімкнено ручний режим, ми пробачаємо розміри коробки, 
      // бо товар може виступати за межі палети, але якщо вимкнено - видаємо помилку.
      if (!_isManualLayer) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Помилка: Коробка більша за палету!'), backgroundColor: Colors.redAccent),
        );
        return;
      }
    }

    if (boxH > (_maxHeightLimit - woodenPalletHeight)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Помилка: Коробка вища за ліміт (${_maxHeightLimit} см)!'), backgroundColor: Colors.redAccent),
      );
      return;
    }

    if (boxWeight > _maxWeightLimit) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Помилка: Вага перевищує 700 кг!'), backgroundColor: Colors.redAccent),
      );
      return;
    }

    // --- ЛОГІКА РУЧНОГО/АВТОМАТИЧНОГО ВВОДУ ---
    if (_isManualLayer) {
      _boxesPerLayer = int.tryParse(_manualLayerCtrl.text) ?? 0;
      if (_boxesPerLayer <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Введіть коректну кількість коробок на шар!'), backgroundColor: Colors.orangeAccent),
        );
        return;
      }
    } else {
      int option1 = ((palL / boxL).floor() * (palW / boxW).floor());
      int option2 = ((palL / boxW).floor() * (palW / boxL).floor());
      _boxesPerLayer = max(option1, option2);
    }
    // ------------------------------------------

    final double availableHeight = _maxHeightLimit - woodenPalletHeight;
    int layersByHeight = (availableHeight / boxH).floor();

    int maxBoxesByWeight = (_maxWeightLimit / boxWeight).floor();
    int totalByHeight = _boxesPerLayer * layersByHeight;

    _weightWarning = '';
    
    if (totalByHeight > maxBoxesByWeight) {
      _finalTotalBoxes = maxBoxesByWeight - (maxBoxesByWeight % _boxesPerLayer); 
      _finalLayers = (_finalTotalBoxes / _boxesPerLayer).floor();
      _weightWarning = 'Обмеження по вазі 700 кг!\nКількість шарів зменшено з $layersByHeight до $_finalLayers';
    } else {
      _finalTotalBoxes = totalByHeight;
      _finalLayers = layersByHeight;
    }

    _unitsPerLayer = _boxesPerLayer * unitsPerBox;
    _finalTotalUnits = _finalTotalBoxes * unitsPerBox;

    _totalCalculatedWeight = _finalTotalBoxes * boxWeight;
    _totalCalculatedHeightMeters = ((_finalLayers * boxH) + woodenPalletHeight) / 100.0;

    setState(() {});
    FocusScope.of(context).unfocus();

    _saveToHistory(); 
  }

  Future<void> _saveToHistory() async {
    final isar = ref.read(isarProvider); 
    
    final newItem = CalculationHistory()
      ..sku = _skuCtrl.text.isEmpty ? 'Невідомий SKU' : _skuCtrl.text
      ..boxLength = double.tryParse(_boxLengthCtrl.text.replaceAll(',', '.'))
      ..boxWidth = double.tryParse(_boxWidthCtrl.text.replaceAll(',', '.'))
      ..boxHeight = double.tryParse(_boxHeightCtrl.text.replaceAll(',', '.'))
      ..boxWeight = double.tryParse(_boxWeightCtrl.text.replaceAll(',', '.'))
      ..unitsInBox = int.tryParse(_unitsPerBoxCtrl.text)
      ..heightLimit = _maxHeightLimit.toDouble()
      ..totalBoxesOnPallet = _finalTotalBoxes
      ..boxesPerLayer = _boxesPerLayer
      ..layersCount = _finalLayers
      ..totalPalletHeight = _totalCalculatedHeightMeters
      ..totalPalletWeight = _totalCalculatedWeight;

    await isar.writeTxn(() async {
      await isar.calculationHistorys.put(newItem);
    });

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('✅ Успішно збережено в історію!', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          backgroundColor: Colors.greenAccent,
        ),
      );
    }
  }

  Widget _buildInputField(String label, TextEditingController controller, {bool isText = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: controller,
        keyboardType: isText ? TextInputType.text : const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(
          labelText: label,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          filled: true,
          fillColor: const Color(0xFF1E1E1E),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
     title: const Text('Новий продукт '),
     actions: [
       IconButton(
         icon: const Icon(Icons.history, color: Colors.white),
         tooltip: 'Історія розрахунків',
         onPressed: () {
           Navigator.push(
             context,
             MaterialPageRoute(builder: (context) => const HistoryScreen()),
           );
         },
       ),
     ],
   ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SegmentedButton<int>(
              segments: const [
                ButtonSegment(value: 180, label: Text('Ліміт 180 см')),
                ButtonSegment(value: 130, label: Text('Ліміт 130 см')),
              ],
              selected: {_maxHeightLimit},
              onSelectionChanged: (Set<int> newSelection) {
                setState(() => _maxHeightLimit = newSelection.first);
              },
            ),
            const SizedBox(height: 16),
            
            _buildInputField(' SKU ', _skuCtrl, isText: true),
            
            Row(
              children: [
                Expanded(child: _buildInputField('Довжина (м)', _boxLengthCtrl)),
                const SizedBox(width: 8),
                Expanded(child: _buildInputField('Ширина (м)', _boxWidthCtrl)),
              ],
            ),
            Row(
              children: [
                Expanded(child: _buildInputField('Висота (м)', _boxHeightCtrl)),
                const SizedBox(width: 8),
                Expanded(child: _buildInputField('Вага 1 кор. (кг)', _boxWeightCtrl)),
              ],
            ),
            _buildInputField('Кількість одиниць в коробці (е)', _unitsPerBoxCtrl),
            
            // --- БЛОК НЕСТАНДАРТНОЇ РОЗКЛАДКИ ---
            Container(
              margin: const EdgeInsets.symmetric(vertical: 8),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFF1E1E1E),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: _isManualLayer ? Colors.orangeAccent.withOpacity(0.5) : Colors.transparent),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Row(
                        children: [
                          Icon(Icons.dashboard_customize, color: Colors.orangeAccent, size: 20),
                          SizedBox(width: 8),
                          Text('Нестандартна розкладка', style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                        ],
                      ),
                      Switch(
                        value: _isManualLayer,
                        activeColor: Colors.black,
                        activeTrackColor: Colors.orangeAccent,
                        onChanged: (val) {
                          setState(() {
                            _isManualLayer = val;
                            if (!val) _manualLayerCtrl.clear();
                          });
                        },
                      ),
                    ],
                  ),
                  if (_isManualLayer) ...[
                    const SizedBox(height: 12),
                    TextField(
                      controller: _manualLayerCtrl,
                      keyboardType: TextInputType.number,
                      style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                      decoration: InputDecoration(
                        labelText: 'Кількість коробок на шар',
                        labelStyle: const TextStyle(color: Colors.orangeAccent),
                        filled: true,
                        fillColor: Colors.black26,
                        prefixIcon: const Icon(Icons.layers, color: Colors.orangeAccent),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8),
                          borderSide: const BorderSide(color: Colors.orangeAccent),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8),
                          borderSide: const BorderSide(color: Colors.orangeAccent, width: 2),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            // ------------------------------------

            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _calculateTiHi,
              style: ElevatedButton.styleFrom(backgroundColor: Colors.greenAccent, padding: const EdgeInsets.symmetric(vertical: 14)),
              child: const Text('Розрахувати розкладку', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 16)),
            ),
            const SizedBox(height: 24),
            
            if (_finalTotalBoxes > 0)
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Colors.green.withOpacity(0.1),
                      border: Border.all(color: Colors.greenAccent.withOpacity(0.5)),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        const Text('Всього на палеті:', style: TextStyle(fontSize: 16, color: Colors.white70)),
                        const SizedBox(height: 8),
                        Text(
                          '$_finalTotalBoxes коробок',
                          style: const TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: Colors.greenAccent),
                        ),
                        Text(
                          '($_finalTotalUnits одиниць товару)',
                          style: const TextStyle(fontSize: 18, color: Colors.greenAccent),
                        ),
                        const SizedBox(height: 16),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            _buildResultParam('Коробок в шарі', '$_boxesPerLayer'),
                            _buildResultParam('Шарів ', '$_finalLayers'),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            _buildResultParam('Одиниць в шарі', '$_unitsPerLayer'),
                            const SizedBox(width: 1), 
                          ],
                        ),
                        const Divider(height: 32, color: Colors.white24),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            _buildResultParam('Вага нетто', '${_totalCalculatedWeight.toStringAsFixed(1)} кг'),
                            _buildResultParam('Висота', '${_totalCalculatedHeightMeters.toStringAsFixed(2)} м'),
                          ],
                        ),

                        if (_weightWarning.isNotEmpty) ...[
                          const SizedBox(height: 16),
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(color: Colors.redAccent.withOpacity(0.2), borderRadius: BorderRadius.circular(8)),
                            child: Text(
                              _weightWarning,
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ]
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultParam(String label, String value) {
    return Column(
      children: [
        Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white)),
        Text(label, style: const TextStyle(fontSize: 14, color: Colors.white54)),
      ],
    );
  }
}