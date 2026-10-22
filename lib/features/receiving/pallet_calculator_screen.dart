import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:isar/isar.dart';
import '../../providers/db_provider.dart';
import '../../models/delivery_session.dart';

class PalletCalculatorScreen extends ConsumerStatefulWidget {
  final int sessionId;
  final int photoId;
  final String sku;

  const PalletCalculatorScreen({
    super.key,
    required this.sessionId,
    required this.photoId,
    required this.sku,
  });

  @override
  ConsumerState<PalletCalculatorScreen> createState() => _PalletCalculatorScreenState();
}

class _PalletCalculatorScreenState extends ConsumerState<PalletCalculatorScreen> {
  // Контролери
  final _totalUnitsCtrl = TextEditingController(); 
  final _unitsPerBoxCtrl = TextEditingController(); 
  final _boxesPerPalletCtrl = TextEditingController(); 
  final _expectedUnitsCtrl = TextEditingController();
  final _acceptedUnitsCtrl = TextEditingController();

  // Стейт АВТО
  int _fullPallets = 0;
  int _remainderBoxes = 0;
  int _remainderUnits = 0;
  int _looseUnits = 0; 
  int _totalPalletsPlaces = 0; 
  int _remainderTotalUnits = 0; 
  
  // Стейт РУЧНИЙ + НАКОПИЧЕННЯ
  bool _isManualMode = false;
  String _carrierType = 'pallet';
  int _previouslyAccepted = 0; // Скільки ВЖЕ прийнято по цьому SKU
  int _globalExpected = 0; // Загальна очікувана кількість
  int _diff = 0; // Глобальна різниця (Прийнято всього - Очікувано)
  
  bool _isCalculated = false;
  int? _editingCalcId;
  List<PalletCalculation> _localHistory = [];

  @override
  void initState() {
    super.initState();
    _loadLocalHistory();
  }

  @override
  void dispose() {
    _totalUnitsCtrl.dispose();
    _unitsPerBoxCtrl.dispose();
    _boxesPerPalletCtrl.dispose();
    _expectedUnitsCtrl.dispose();
    _acceptedUnitsCtrl.dispose();
    super.dispose();
  }

  void _loadLocalHistory() {
    final isar = ref.read(isarProvider);
    isar.palletCalculations
        .filter()
        .photoIdEqualTo(widget.photoId)
        .sortByCreatedAtDesc()
        .findAll()
        .then((list) {
      setState(() {
        _localHistory = list;
        
        // Вираховуємо накопичену статистику САМЕ ДЛЯ ЦЬОГО SKU
        int prevAcc = 0;
        int globExp = 0;
        
        for (var c in list) {
          if (c.sku == widget.sku && c.id != _editingCalcId) {
            if (c.isManual) {
              prevAcc += (c.acceptedUnits ?? 0);
              if ((c.expectedUnits ?? 0) > globExp) globExp = c.expectedUnits ?? 0;
            } else {
              prevAcc += (c.totalUnits ?? 0);
            }
          }
        }
        
        _previouslyAccepted = prevAcc;
        _globalExpected = globExp;

        // Автоматично підставляємо "Очікувану" кількість, якщо вона вже вводилася раніше
        if (_globalExpected > 0 && _expectedUnitsCtrl.text.isEmpty) {
          _expectedUnitsCtrl.text = _globalExpected.toString();
        }
      });
    });
  }

  void _calculate() {
    if (_isManualMode) {
      final int expected = int.tryParse(_expectedUnitsCtrl.text) ?? 0;
      final int accepted = int.tryParse(_acceptedUnitsCtrl.text) ?? 0;

      if (expected <= 0 || accepted < 0) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Введіть коректну кількість!'), backgroundColor: Colors.redAccent));
        return;
      }

      // Рахуємо ГЛОБАЛЬНУ різницю: (Вже прийнято раніше + Прийнято зараз) - Очікувалося всього
      _diff = (_previouslyAccepted + accepted) - expected;
      setState(() => _isCalculated = true);

    } else {
      final int totalUnits = int.tryParse(_totalUnitsCtrl.text) ?? 0;
      final int unitsPerBox = int.tryParse(_unitsPerBoxCtrl.text) ?? 0;
      final int boxesPerPallet = int.tryParse(_boxesPerPalletCtrl.text) ?? 0;

      if (totalUnits <= 0 || unitsPerBox <= 0 || boxesPerPallet <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Заповніть усі поля значеннями більше 0!'), backgroundColor: Colors.redAccent));
        return;
      }

      _looseUnits = totalUnits % unitsPerBox; 
      final int totalBoxes = totalUnits ~/ unitsPerBox; 
      _fullPallets = totalBoxes ~/ boxesPerPallet; 
      _remainderBoxes = totalBoxes % boxesPerPallet; 
      _remainderUnits = _remainderBoxes * unitsPerBox; 
      _remainderTotalUnits = _remainderUnits + _looseUnits; 

      _totalPalletsPlaces = _fullPallets + ((_remainderBoxes > 0 || _looseUnits > 0) ? 1 : 0);
      setState(() => _isCalculated = true);
    }
    FocusScope.of(context).unfocus();
  }

  Future<void> _syncCalculationWithDb({PalletCalculation? toSave, int? toDeleteId}) async {
    final isar = ref.read(isarProvider);

    await isar.writeTxn(() async {
      if (toDeleteId != null) {
        await isar.palletCalculations.delete(toDeleteId);
      } else if (toSave != null) {
        await isar.palletCalculations.put(toSave);
      }

      final allCalcsForPhoto = await isar.palletCalculations.filter().photoIdEqualTo(widget.photoId).findAll();
      final photo = await isar.documentPhotos.get(widget.photoId);
      
      if (photo != null) {
        if (allCalcsForPhoto.isEmpty) {
          photo.comment = null;
        } else {
          // --- РОЗУМНЕ ГРУПУВАННЯ КОМЕНТАРІВ ПО SKU ---
          Map<String, List<PalletCalculation>> groupedBySku = {};
          for (var c in allCalcsForPhoto) {
            groupedBySku.putIfAbsent(c.sku ?? 'Незвісний SKU', () => []).add(c);
          }

          List<String> commentLines = [];
          
          for (var entry in groupedBySku.entries) {
            String currentSku = entry.key;
            List<PalletCalculation> calcs = entry.value;

            int totalAcc = 0;
            int maxExp = 0;
            int totalPlaces = 0;
            bool hasManual = false;
            List<String> details = [];

            for (var c in calcs) {
              if (c.isManual) {
                hasManual = true;
                totalAcc += (c.acceptedUnits ?? 0);
                if ((c.expectedUnits ?? 0) > maxExp) maxExp = c.expectedUnits ?? 0;
                if (c.carrierType == 'pallet') totalPlaces += 1;
                details.add('   ├ Ручний прийом: ${c.acceptedUnits} шт. (${c.carrierType == 'pallet' ? 'Палета' : 'Вузек'})');
              } else {
                totalAcc += (c.totalUnits ?? 0);
                int p = (c.fullPallets ?? 0) + (((c.remainderBoxes ?? 0) > 0 || (c.looseUnits ?? 0) > 0) ? 1 : 0);
                totalPlaces += p;

                if ((c.fullPallets ?? 0) > 0) {
                  int u = (c.boxesPerPallet ?? 0) * (c.unitsPerBox ?? 0);
                  details.add('   ├ ${c.fullPallets} повних палет по $u од. (${c.boxesPerPallet} кор.)');
                }
                if ((c.remainderBoxes ?? 0) > 0 || (c.looseUnits ?? 0) > 0) {
                  int remTotal = ((c.remainderBoxes ?? 0) * (c.unitsPerBox ?? 0)) + (c.looseUnits ?? 0);
                  String remD = '';
                  if ((c.remainderBoxes ?? 0) > 0) remD += '${c.remainderBoxes} кор.';
                  if ((c.remainderBoxes ?? 0) > 0 && (c.looseUnits ?? 0) > 0) remD += ' + ';
                  if ((c.looseUnits ?? 0) > 0) remD += '${c.looseUnits} од. розп.';
                  details.add('   ├ 1 неповна палета на $remTotal од. ($remD)');
                }
              }
            }

            String block = '📦 $currentSku: Всього палетомісць: $totalPlaces\n${details.join('\n')}';
            
            // Змінюємо останній ├ на └ для красивої "ялинки"
            int lastIndex = block.lastIndexOf('├');
            if (lastIndex != -1) block = block.replaceRange(lastIndex, lastIndex + 1, '└');

            // Підбиваємо загальний підсумок для цього SKU
            if (hasManual && maxExp > 0) {
              int diff = totalAcc - maxExp;
              String diffStr = diff < 0 ? '⚠️ Бракує: ${diff.abs()} шт.' : (diff > 0 ? '⚠️ Надлишок: $diff шт.' : '✅ Зійшлося точно');
              block += '\n   └ Загалом прийнято: $totalAcc з $maxExp шт. ($diffStr)';
            } else {
              block += '\n   └ Загалом прийнято товару: $totalAcc шт.';
            }
            
            commentLines.add(block);
          }
          photo.comment = commentLines.join('\n\n');
        }
        await isar.documentPhotos.put(photo);
      }

      // ПІДРАХУНОК ЗАГАЛЬНОЇ КІЛЬКОСТІ ПАЛЕТ В СЕСІЇ (Вузек не додає палету)
      final allCalcsForSession = await isar.palletCalculations.filter().sessionIdEqualTo(widget.sessionId).findAll();
      int newTotalPallets = 0;
      for (var c in allCalcsForSession) {
        if (c.isManual) {
          if (c.carrierType == 'pallet') newTotalPallets += 1;
        } else {
          newTotalPallets += (c.fullPallets ?? 0) + (((c.remainderBoxes ?? 0) > 0 || (c.looseUnits ?? 0) > 0) ? 1 : 0);
        }
      }

      final session = await isar.deliverySessions.get(widget.sessionId);
      if (session != null) {
        session.totalCalculatedPallets = newTotalPallets;
        await isar.deliverySessions.put(session);
      }
    });

    _editingCalcId = null;
    _clearFields();
    _loadLocalHistory();
  }

  void _clearFields() {
    _totalUnitsCtrl.clear();
    _unitsPerBoxCtrl.clear();
    _boxesPerPalletCtrl.clear();
    // Очищаємо "Очікувалося" ТІЛЬКИ якщо не було глобального значення
    if (_globalExpected == 0) _expectedUnitsCtrl.clear();
    _acceptedUnitsCtrl.clear();
    
    setState(() {
      _isCalculated = false;
      _fullPallets = 0;
      _remainderBoxes = 0;
      _remainderUnits = 0;
      _looseUnits = 0;
      _totalPalletsPlaces = 0;
      _remainderTotalUnits = 0;
      _diff = 0;
      _editingCalcId = null;
    });
  }

  void _populateForEditing(PalletCalculation item) {
    setState(() {
      _editingCalcId = item.id;
      _isManualMode = item.isManual;
      
      if (_isManualMode) {
        _expectedUnitsCtrl.text = item.expectedUnits?.toString() ?? '';
        _acceptedUnitsCtrl.text = item.acceptedUnits?.toString() ?? '';
        _carrierType = item.carrierType ?? 'pallet';
      } else {
        _totalUnitsCtrl.text = item.totalUnits?.toString() ?? '';
        _unitsPerBoxCtrl.text = item.unitsPerBox?.toString() ?? '';
        _boxesPerPalletCtrl.text = item.boxesPerPallet?.toString() ?? '';
      }
      _isCalculated = false;
    });
    // Перераховуємо накопичене (щоб виключити поточний запис з суми)
    _loadLocalHistory(); 
  }

  Widget _buildInput(String label, TextEditingController controller) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: TextField(
        controller: controller,
        keyboardType: TextInputType.number,
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: Colors.white54, fontSize: 14),
          filled: true,
          fillColor: const Color(0xFF1E1E1E),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12), 
            borderSide: BorderSide(color: _isManualMode ? Colors.orangeAccent : Colors.greenAccent, width: 1)
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_editingCalcId != null ? 'Корегування' : 'Розрахунок: ${widget.sku}'),
        actions: [
          if (_isCalculated || _editingCalcId != null)
            IconButton(icon: const Icon(Icons.refresh, color: Colors.white54), onPressed: _clearFields)
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // --- ПЕРЕМИКАЧ РЕЖИМІВ ---
                  Container(
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(color: const Color(0xFF1E1E1E), borderRadius: BorderRadius.circular(12)),
                    child: Row(
                      children: [
                        Expanded(
                          child: GestureDetector(
                            onTap: () => setState(() { _isManualMode = false; _clearFields(); }),
                            child: Container(
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              decoration: BoxDecoration(
                                color: !_isManualMode ? Colors.greenAccent.withOpacity(0.15) : Colors.transparent,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: !_isManualMode ? Colors.greenAccent : Colors.transparent),
                              ),
                              child: Center(child: Text('Авто (стандарт)', style: TextStyle(color: !_isManualMode ? Colors.greenAccent : Colors.white54, fontWeight: FontWeight.bold))),
                            ),
                          ),
                        ),
                        Expanded(
                          child: GestureDetector(
                            onTap: () => setState(() { _isManualMode = true; _clearFields(); }),
                            child: Container(
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              decoration: BoxDecoration(
                                color: _isManualMode ? Colors.orangeAccent.withOpacity(0.15) : Colors.transparent,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: _isManualMode ? Colors.orangeAccent : Colors.transparent),
                              ),
                              child: Center(child: Text('Ручний (нестандарт)', style: TextStyle(color: _isManualMode ? Colors.orangeAccent : Colors.white54, fontWeight: FontWeight.bold))),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  // Інформаційна плашка про вже прийнятий товар
                  if (_previouslyAccepted > 0)
                    Container(
                      margin: const EdgeInsets.only(bottom: 16),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: Colors.blueAccent.withOpacity(0.1), borderRadius: BorderRadius.circular(10), border: Border.all(color: Colors.blueAccent.withOpacity(0.3))),
                      child: Row(
                        children: [
                          const Icon(Icons.info_outline, color: Colors.blueAccent, size: 20),
                          const SizedBox(width: 8),
                          Expanded(child: Text('По цьому SKU вже є в базі: $_previouslyAccepted шт.', style: const TextStyle(color: Colors.blueAccent, fontWeight: FontWeight.bold))),
                        ],
                      ),
                    ),

                  // --- ФОРМИ ВВОДУ ---
                  if (!_isManualMode) ...[
                    _buildInput('Загальна кількість товару (шт. з інвойсу)', _totalUnitsCtrl),
                    Row(
                      children: [
                        Expanded(child: _buildInput('Одиниць у коробці', _unitsPerBoxCtrl)),
                        const SizedBox(width: 12),
                        Expanded(child: _buildInput('Коробок на палеті', _boxesPerPalletCtrl)),
                      ],
                    ),
                  ] else ...[
                    Row(
                      children: [
                        Expanded(child: _buildInput('Очікувалось всього', _expectedUnitsCtrl)),
                        const SizedBox(width: 12),
                        Expanded(child: _buildInput('Знайдено зараз', _acceptedUnitsCtrl)),
                      ],
                    ),
                    const Text('Куди прийнято товар?', style: TextStyle(color: Colors.white54, fontSize: 13)),
                    const SizedBox(height: 8),
                    Container(
                      decoration: BoxDecoration(color: const Color(0xFF1E1E1E), borderRadius: BorderRadius.circular(12)),
                      child: Row(
                        children: [
                          Expanded(
                            child: RadioListTile<String>(
                              title: const Text('Палета (+1)', style: TextStyle(color: Colors.white, fontSize: 14)),
                              value: 'pallet',
                              groupValue: _carrierType,
                              activeColor: Colors.orangeAccent,
                              onChanged: (val) => setState(() => _carrierType = val!),
                              contentPadding: EdgeInsets.zero,
                            ),
                          ),
                          Expanded(
                            child: RadioListTile<String>(
                              title: const Text('Вузек (0)', style: TextStyle(color: Colors.white, fontSize: 14)),
                              value: 'wozek',
                              groupValue: _carrierType,
                              activeColor: Colors.orangeAccent,
                              onChanged: (val) => setState(() => _carrierType = val!),
                              contentPadding: EdgeInsets.zero,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],
                  
                  ElevatedButton(
                    onPressed: _calculate,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.white12,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: Text(_editingCalcId != null ? 'Перерахувати зміни' : 'Порахувати / Зафіксувати', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                  ),
                  
                  // --- БЛОК РЕЗУЛЬТАТІВ ---
                  if (_isCalculated) ...[
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: _isManualMode ? Colors.orange.withOpacity(0.05) : Colors.green.withOpacity(0.05),
                        border: Border.all(color: _isManualMode ? Colors.orangeAccent.withOpacity(0.4) : Colors.greenAccent.withOpacity(0.4), width: 1),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: _isManualMode 
                        // РЕЗУЛЬТАТ: РУЧНИЙ РЕЖИМ (Глобальна стата)
                        ? Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              const Text('ПІДСУМОК ПО SKU (З УРАХУВАННЯМ ІСТОРІЇ)', textAlign: TextAlign.center, style: TextStyle(color: Colors.white38, fontWeight: FontWeight.bold, fontSize: 12, letterSpacing: 1)),
                              const SizedBox(height: 14),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  const Text('Місце зберігання:', style: TextStyle(fontSize: 16, color: Colors.white70)),
                                  Text(_carrierType == 'pallet' ? '+1 Палета' : '0 Палет (Вузек)', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: _carrierType == 'pallet' ? Colors.blueAccent : Colors.grey)),
                                ],
                              ),
                              const Divider(height: 24, color: Colors.white12),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  const Text('Загалом прийнято:', style: TextStyle(fontSize: 16, color: Colors.white70)),
                                  Text('${_previouslyAccepted + int.parse(_acceptedUnitsCtrl.text)} з ${_expectedUnitsCtrl.text}', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                                ],
                              ),
                              const SizedBox(height: 8),
                              if (_diff < 0)
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    const Text('Бракує товару:', style: TextStyle(fontSize: 16, color: Colors.redAccent)),
                                    Text('${_diff.abs()} шт.', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.redAccent)),
                                  ],
                                )
                              else if (_diff > 0)
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    const Text('Надлишок товару:', style: TextStyle(fontSize: 16, color: Colors.yellowAccent)),
                                    Text('+$_diff шт.', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.yellowAccent)),
                                  ],
                                )
                              else
                                const Center(child: Padding(padding: EdgeInsets.only(top: 8), child: Text('✅ Прийнято точно по інвойсу', style: TextStyle(fontSize: 16, color: Colors.greenAccent, fontWeight: FontWeight.bold)))),
                            ],
                          )
                        // РЕЗУЛЬТАТ: АВТО РЕЖИМ
                        : Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              const Text('РЕЗУЛЬТАТ ОБЧИСЛЕННЯ', textAlign: TextAlign.center, style: TextStyle(color: Colors.white38, fontWeight: FontWeight.bold, fontSize: 12, letterSpacing: 1)),
                              const SizedBox(height: 14),
                              Container(
                                padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
                                decoration: BoxDecoration(
                                  color: Colors.blueAccent.withOpacity(0.1),
                                  borderRadius: BorderRadius.circular(10),
                                  border: Border.all(color: Colors.blueAccent.withOpacity(0.3)),
                                ),
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    const Text('Всього палет:', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                                    Text('$_totalPalletsPlaces', style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: Colors.blueAccent)),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 16),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  const Text('З них повних:', style: TextStyle(fontSize: 16, color: Colors.white70)),
                                  Text('$_fullPallets', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.greenAccent)),
                                ],
                              ),
                              if (_remainderBoxes > 0 || _looseUnits > 0) ...[
                                const Divider(height: 24, color: Colors.white12),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    const Text('Неповна палета:', style: TextStyle(fontSize: 16, color: Colors.yellowAccent)),
                                    const Text('1 шт', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.yellowAccent)),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    const Text('Всього одиниць на ній:', style: TextStyle(fontSize: 14, color: Colors.white54)),
                                    Text('$_remainderTotalUnits од.', style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white)),
                                  ],
                                ),
                              ],
                            ],
                          ),
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton.icon(
                      onPressed: () async {
                        final calc = PalletCalculation()
                          ..id = _editingCalcId ?? Isar.autoIncrement
                          ..sessionId = widget.sessionId
                          ..photoId = widget.photoId
                          ..sku = widget.sku
                          ..isManual = _isManualMode;
                          
                        if (_isManualMode) {
                          calc.expectedUnits = int.parse(_expectedUnitsCtrl.text);
                          calc.acceptedUnits = int.parse(_acceptedUnitsCtrl.text);
                          calc.carrierType = _carrierType;
                        } else {
                          calc.totalUnits = int.parse(_totalUnitsCtrl.text);
                          calc.unitsPerBox = int.parse(_unitsPerBoxCtrl.text);
                          calc.boxesPerPallet = int.parse(_boxesPerPalletCtrl.text);
                          calc.fullPallets = _fullPallets;
                          calc.remainderBoxes = _remainderBoxes;
                          calc.looseUnits = _looseUnits;
                        }
                        
                        await _syncCalculationWithDb(toSave: calc);
                        
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('✅ Збережено!'), backgroundColor: _isManualMode ? Colors.orangeAccent : Colors.greenAccent),
                          );
                        }
                      },
                      icon: const Icon(Icons.done_all, color: Colors.black),
                      label: Text(_editingCalcId != null ? 'Підтвердити зміни' : 'Підтвердити та додати', style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 16)),
                      style: ElevatedButton.styleFrom(backgroundColor: _isManualMode ? Colors.orangeAccent : Colors.greenAccent, padding: const EdgeInsets.symmetric(vertical: 16)),
                    ),
                  ],
                  
                  const SizedBox(height: 24),
                  const Text('ІСТОРІЯ РОЗРАХУНКІВ ПО ЦЬОМУ ФОТО', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.white38, letterSpacing: 0.5)),
                  const SizedBox(height: 8),
                ],
              ),
            ),
          ),
          
          // Нижня динамічна стрічка локальної історії
          Container(
            height: 200,
            color: const Color(0xFF0F0F0F),
            child: _localHistory.isEmpty
                ? const Center(child: Text('Історія порожня', style: TextStyle(color: Colors.white38)))
                : ListView.builder(
                    itemCount: _localHistory.length,
                    itemBuilder: (context, index) {
                      final item = _localHistory[index];
                      
                      return Container(
                        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: const Color(0xFF1A1A1A), 
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: item.isManual ? Colors.orangeAccent.withOpacity(0.3) : Colors.transparent)
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: item.isManual 
                                ? Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text('✋ ${item.sku} [Ручний]', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.orangeAccent, fontSize: 15)),
                                      const SizedBox(height: 4),
                                      Text('Прийнято зараз: ${item.acceptedUnits} шт.', style: const TextStyle(color: Colors.white70, fontSize: 13)),
                                      Text('Місце: ${item.carrierType == 'pallet' ? 'Палета (+1)' : 'Вузек (0)'}', style: const TextStyle(color: Colors.white54, fontSize: 13)),
                                    ],
                                  )
                                : Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text('📦 ${item.sku}', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.blueAccent, fontSize: 15)),
                                      const SizedBox(height: 4),
                                      Text('Всього товару: ${item.totalUnits} шт.', style: const TextStyle(color: Colors.white70, fontSize: 13)),
                                      Text('Повних палет: ${item.fullPallets}', style: const TextStyle(color: Colors.white54, fontSize: 13)),
                                    ],
                                  ),
                            ),
                            IconButton(icon: const Icon(Icons.edit, color: Colors.white54, size: 20), onPressed: () => _populateForEditing(item)),
                            IconButton(icon: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20), onPressed: () => _syncCalculationWithDb(toDeleteId: item.id)),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}