import 'package:flutter/material.dart';

class QuickCalculatorScreen extends StatefulWidget {
  const QuickCalculatorScreen({super.key});

  @override
  State<QuickCalculatorScreen> createState() => _QuickCalculatorScreenState();
}

class _QuickCalculatorScreenState extends State<QuickCalculatorScreen> {
  final _totalUnitsCtrl = TextEditingController();
  final _unitsPerBoxCtrl = TextEditingController();
  final _boxesPerPalletCtrl = TextEditingController();

  int _fullPallets = 0;
  int _remainderBoxes = 0;
  int _remainderUnits = 0;
  int _looseUnits = 0;
  int _totalPalletsPlaces = 0;
  int _remainderTotalUnits = 0;
  bool _isCalculated = false;

  void _calculate() {
    final int totalUnits = int.tryParse(_totalUnitsCtrl.text) ?? 0;
    final int unitsPerBox = int.tryParse(_unitsPerBoxCtrl.text) ?? 0;
    final int boxesPerPallet = int.tryParse(_boxesPerPalletCtrl.text) ?? 0;

    if (totalUnits <= 0 || unitsPerBox <= 0 || boxesPerPallet <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Введіть значення більше 0')));
      return;
    }

    _looseUnits = totalUnits % unitsPerBox;
    final int totalBoxes = totalUnits ~/ unitsPerBox;
    _fullPallets = totalBoxes ~/ boxesPerPallet;
    _remainderBoxes = totalBoxes % boxesPerPallet;
    _remainderUnits = _remainderBoxes * unitsPerBox;

    // Рахуємо загальну кількість одиниць на неповній палеті
    _remainderTotalUnits = _remainderUnits + _looseUnits;

    // Рахуємо палетомісця (повні + 1, якщо є хоч якась остача)
    _totalPalletsPlaces =
        _fullPallets + ((_remainderBoxes > 0 || _looseUnits > 0) ? 1 : 0);

    setState(() {
      _isCalculated = true;
    });
    FocusScope.of(context).unfocus();
  }

  Widget _buildInputField(String label, TextEditingController controller) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: controller,
        keyboardType: TextInputType.number,
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: Colors.white54, fontSize: 14),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
          filled: true,
          fillColor: const Color(0xFF1E1E1E),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Colors.greenAccent, width: 1),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final int bPerPallet = int.tryParse(_boxesPerPalletCtrl.text) ?? 0;
    final int totalUnitsOnFullPallet =
        bPerPallet * (int.tryParse(_unitsPerBoxCtrl.text) ?? 0);

    return Scaffold(
      appBar: AppBar(title: const Text('Швидкий калькулятор')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _buildInputField(
                'Загальна кількість товару (шт.)', _totalUnitsCtrl),
            Row(
              children: [
                Expanded(
                    child: _buildInputField(
                        'Одиниць у коробці', _unitsPerBoxCtrl)),
                const SizedBox(width: 12),
                Expanded(
                    child: _buildInputField(
                        'Коробок на палеті', _boxesPerPalletCtrl)),
              ],
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _calculate,
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.white12,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
              child: const Text('Порахувати розкладку',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            ),
            if (_isCalculated) ...[
              const SizedBox(height: 24),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.green.withOpacity(0.05),
                  border: Border.all(
                      color: Colors.greenAccent.withOpacity(0.4), width: 1),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text('РЕЗУЛЬТАТ ОБЧИСЛЕННЯ',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                            color: Colors.white38,
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                            letterSpacing: 1)),
                    const SizedBox(height: 14),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          vertical: 12, horizontal: 16),
                      decoration: BoxDecoration(
                        color: Colors.blueAccent.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                            color: Colors.blueAccent.withOpacity(0.3)),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Всього палет:',
                              style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white)),
                          Text('$_totalPalletsPlaces',
                              style: const TextStyle(
                                  fontSize: 26,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.blueAccent)),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text('З них повних:',
                            style:
                                TextStyle(fontSize: 16, color: Colors.white70)),
                        Text('$_fullPallets',
                            style: const TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.greenAccent)),
                      ],
                    ),
                    if (_fullPallets > 0) ...[
                      const SizedBox(height: 4),
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text(
                          '($_fullPallets палет по $totalUnitsOnFullPallet од. / $bPerPallet кор.)',
                          style: const TextStyle(
                              color: Colors.white38, fontSize: 13),
                        ),
                      ),
                    ],
                    if (_remainderBoxes > 0 || _looseUnits > 0) ...[
                      const Divider(height: 24, color: Colors.white12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Неповна палета:',
                              style: TextStyle(
                                  fontSize: 16, color: Colors.yellowAccent)),
                          const Text('1 шт',
                              style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.yellowAccent)),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Всього одиниць на ній:',
                              style: TextStyle(
                                  fontSize: 14, color: Colors.white54)),
                          Text('$_remainderTotalUnits од.',
                              style: const TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white)),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Align(
                        alignment: Alignment.centerRight,
                        child: Text(
                          '(${_remainderBoxes > 0 ? '$_remainderBoxes кор.' : ''}${_remainderBoxes > 0 && _looseUnits > 0 ? ' + ' : ''}${_looseUnits > 0 ? '$_looseUnits од. розсипом' : ''})',
                          style: const TextStyle(
                              color: Colors.white38, fontSize: 13),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ]
          ],
        ),
      ),
    );
  }
}
