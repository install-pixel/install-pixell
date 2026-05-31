import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import '../../utils/haptics.dart';
import '../../providers/general_note_provider.dart';
import '../../models/general_note.dart';

class GlobalCalculatorSheet extends ConsumerStatefulWidget {
  const GlobalCalculatorSheet({super.key});

  static void show(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const GlobalCalculatorSheet(),
    );
  }

  @override
  ConsumerState<GlobalCalculatorSheet> createState() => _GlobalCalculatorSheetState();
}

class _GlobalCalculatorSheetState extends ConsumerState<GlobalCalculatorSheet> {
  String _output = '0';
  double _num1 = 0;
  String _operand = '';
  bool _isNewNumber = true;

  final List<String> _historyLog = [];
  String _livePreview = '';

  void _updateLivePreview() {
    if (_operand.isEmpty || _isNewNumber) {
      _livePreview = '';
      return;
    }
    double num2 = double.tryParse(_output) ?? 0;
    double result = _calculate(_num1, num2, _operand);
    _livePreview = '= ${_formatNumber(result)}';
  }

  double _calculate(double n1, double n2, String op) {
    switch (op) {
      case '+': return n1 + n2;
      case '-': return n1 - n2;
      case '×': return n1 * n2;
      case '÷': return n2 == 0 ? 0 : n1 / n2;
      default: return n2;
    }
  }

  String _formatNumber(double num) {
    if (num == num.toInt()) return num.toInt().toString();
    String str = num.toStringAsFixed(4);
    return str.replaceAll(RegExp(r'0*$'), '').replaceAll(RegExp(r'\.$'), '');
  }

  void _showCreatorDialog() {
    AppHaptics.success();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF2A2A2A),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Row(
          children: [
            Icon(Icons.info_outline, color: Colors.greenAccent),
            SizedBox(width: 10),
            Text('Інформація', style: TextStyle(color: Colors.white)),
          ],
        ),
        content: const Text(
          'Творцем є Вадим.\n\nЦей додаток створено разом з Gemini для комфортної роботи на складі DSV.',
          style: TextStyle(color: Colors.white70, fontSize: 16, height: 1.4),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Зрозуміло', style: TextStyle(color: Colors.greenAccent, fontSize: 16)),
          ),
        ],
      ),
    );
  }

  // --- ВІКНО НОТАТНИКА (Пасхалка 1+=) ---
  void _showNotesDialog() {
    AppHaptics.success();
    final noteInputCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              backgroundColor: const Color(0xFF161616),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
                side: BorderSide(color: Colors.greenAccent.withOpacity(0.2), width: 1),
              ),
              title: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.sticky_note_2_outlined, color: Colors.greenAccent),
                      SizedBox(width: 10),
                      Text('Складські Нотатки', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                    ],
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white38),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              content: SizedBox(
                width: double.maxFinite,
                height: MediaQuery.of(context).size.height * 0.5,
                child: Column(
                  children: [
                    TextField(
                      controller: noteInputCtrl,
                      style: const TextStyle(color: Colors.white, fontSize: 14),
                      maxLines: 2,
                      decoration: InputDecoration(
                        hintText: 'Записати важливий лог, реф або номер...',
                        hintStyle: const TextStyle(color: Colors.white30, fontSize: 13),
                        filled: true,
                        fillColor: const Color(0xFF222222),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                        suffixIcon: IconButton(
                          icon: const Icon(Icons.send, color: Colors.greenAccent),
                          onPressed: () async {
                            if (noteInputCtrl.text.trim().isNotEmpty) {
                              await ref.read(generalNoteControllerProvider).saveNote(noteInputCtrl.text);
                              noteInputCtrl.clear();
                              AppHaptics.success();
                              setDialogState(() {}); 
                            }
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    
                    Expanded(
                      child: Consumer(
                        builder: (context, ref, child) {
                          final notesAsync = ref.watch(generalNotesListProvider);
                          
                          return notesAsync.when(
                            data: (notesList) {
                              if (notesList.isEmpty) {
                                return const Center(
                                  child: Text('Нотаток немає.\nТут можна ховати логістичні дані.', 
                                    textAlign: TextAlign.center, 
                                    style: TextStyle(color: Colors.white24, fontSize: 13),
                                  ),
                                );
                              }
                              
                              return ListView.builder(
                                itemCount: notesList.length,
                                itemBuilder: (context, index) {
                                  final note = notesList[index];
                                  final String timeStr = DateFormat('dd.MM HH:mm').format(note.createdAt); 

                                  return Container(
                                    margin: const EdgeInsets.symmetric(vertical: 4),
                                    padding: const EdgeInsets.all(10),
                                    decoration: BoxDecoration(
                                      color: const Color(0xFF202020),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: Row(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Text(
                                                note.textContent,
                                                style: const TextStyle(color: Colors.white70, fontSize: 14),
                                              ),
                                              const SizedBox(height: 6),
                                              Text(
                                                '🕒 $timeStr',
                                                style: TextStyle(color: Colors.greenAccent.withOpacity(0.5), fontSize: 11, fontFamily: 'monospace'),
                                              ),
                                            ],
                                          ),
                                        ),
                                        IconButton(
                                          icon: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 18),
                                          padding: EdgeInsets.zero,
                                          constraints: const BoxConstraints(),
                                          onPressed: () async {
                                            await ref.read(generalNoteControllerProvider).deleteNote(note.id);
                                            AppHaptics.light();
                                            setDialogState(() {}); 
                                          },
                                        ),
                                      ],
                                    ),
                                  );
                                },
                              );
                            },
                            loading: () => const Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
                            error: (err, stack) => Center(child: Text('Помилка: $err', style: const TextStyle(color: Colors.redAccent))),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  void _buttonPressed(String buttonText) {
    AppHaptics.light();

    setState(() {
      if (buttonText == 'C') {
        _output = '0';
        _num1 = 0;
        _operand = '';
        _isNewNumber = true;
        _livePreview = '';
      } else if (buttonText == '⌫') {
        if (_isNewNumber) return;
        if (_output.length > 1) {
          _output = _output.substring(0, _output.length - 1);
        } else {
          _output = '0';
          _isNewNumber = true;
        }
        _updateLivePreview();
      } else if (buttonText == '+' || buttonText == '-' || buttonText == '×' || buttonText == '÷') {
        if (_operand.isNotEmpty && !_isNewNumber) {
          double num2 = double.tryParse(_output) ?? 0;
          _num1 = _calculate(_num1, num2, _operand);
          _output = _formatNumber(_num1);
        } else {
          _num1 = double.tryParse(_output) ?? 0;
        }
        _operand = buttonText;
        _isNewNumber = true;
        _livePreview = '';
      } else if (buttonText == '=') {
        
        // ПАСХАЛКА 1: Виклик прихованого нотатника через "1 + ="
        // Спрацює, якщо ввели 1, натиснули +, і відразу натиснули = (не вводячи друге число)
        if (_operand == '+' && _num1 == 1.0 && _isNewNumber) { 
          _showNotesDialog();
          _output = '0';
          _num1 = 0;
          _operand = '';
          _isNewNumber = true;
          _livePreview = '';
          return;
        }

        if (_operand.isEmpty) {
          // ПАСХАЛКА 2: Творець Вадим через "1986 ="
          if (_output == '1986') {
            _showCreatorDialog();
            _output = '0';
            _isNewNumber = true;
          }
          return;
        }

        AppHaptics.success();
        double num2 = double.tryParse(_output) ?? 0;
        double result = _calculate(_num1, num2, _operand);
        
        _historyLog.insert(0, '${_formatNumber(_num1)} $_operand ${_formatNumber(num2)} = ${_formatNumber(result)}');
        if (_historyLog.length > 5) _historyLog.removeLast();

        _output = _formatNumber(result);
        _num1 = result;
        _operand = '';
        _isNewNumber = true;
        _livePreview = '';
      } else if (buttonText == '.') {
        if (_isNewNumber) {
          _output = '0.';
          _isNewNumber = false;
        } else if (!_output.contains('.')) {
          _output += '.';
        }
        _updateLivePreview();
      } else {
        if (_isNewNumber || _output == '0') {
          _output = buttonText == '00' ? '0' : buttonText;
          _isNewNumber = false;
        } else {
          _output += buttonText;
        }
        _updateLivePreview();
      }
    });
  }

  void _copyAndClose() {
    AppHaptics.success();
    Clipboard.setData(ClipboardData(text: _output));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('✅ Скопійовано: $_output', style: const TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.greenAccent.shade700,
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 2),
      ),
    );
    Navigator.pop(context);
  }

  Widget _buildCalcBtn(String text, {Color? textColor, Color? bgColor, int flex = 1}) {
    return Expanded(
      flex: flex,
      child: Padding(
        padding: const EdgeInsets.all(4.0),
        child: ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: bgColor ?? const Color(0xFF2A2A2A),
            foregroundColor: textColor ?? Colors.white,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            padding: const EdgeInsets.symmetric(vertical: 20),
            elevation: 0,
          ),
          onPressed: () => _buttonPressed(text),
          child: Text(text, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w500)),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.9,
      decoration: const BoxDecoration(
        color: Color(0xFF121212),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              Expanded(
                flex: 2,
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.5),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.greenAccent.withOpacity(0.2)),
                  ),
                  child: _historyLog.isEmpty
                      ? const Center(child: Text('Історія чиста...', style: TextStyle(color: Colors.white24)))
                      : ListView.builder(
                          reverse: true,
                          itemCount: _historyLog.length,
                          itemBuilder: (context, i) {
                            return Padding(
                              padding: const EdgeInsets.symmetric(vertical: 4.0),
                              child: Text(
                                '> ${_historyLog[i]}',
                                style: const TextStyle(
                                  color: Colors.greenAccent, 
                                  fontFamily: 'monospace', 
                                  fontSize: 16
                                ),
                              ),
                            );
                          },
                        ),
                ),
              ),
              const SizedBox(height: 12),

              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      _livePreview,
                      style: const TextStyle(color: Colors.white38, fontSize: 24, fontWeight: FontWeight.bold),
                    ),
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        _output,
                        style: const TextStyle(color: Colors.white, fontSize: 64, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
              ),
              
              ElevatedButton.icon(
                onPressed: _copyAndClose,
                icon: const Icon(Icons.copy_all, color: Colors.black),
                label: const Text('Копіювати', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 18, letterSpacing: 1.2)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.yellowAccent,
                  minimumSize: const Size.fromHeight(50),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
              const SizedBox(height: 12),

              Row(
                children: [
                  _buildCalcBtn('C', textColor: Colors.redAccent, bgColor: Colors.redAccent.withOpacity(0.1)),
                  _buildCalcBtn('⌫', textColor: Colors.orangeAccent, bgColor: Colors.orangeAccent.withOpacity(0.1)),
                  _buildCalcBtn('÷', textColor: Colors.greenAccent, bgColor: Colors.greenAccent.withOpacity(0.1)),
                  _buildCalcBtn('×', textColor: Colors.greenAccent, bgColor: Colors.greenAccent.withOpacity(0.1)),
                ],
              ),
              Row(
                children: [
                  _buildCalcBtn('7'), _buildCalcBtn('8'), _buildCalcBtn('9'),
                  _buildCalcBtn('-', textColor: Colors.greenAccent, bgColor: Colors.greenAccent.withOpacity(0.1)),
                ],
              ),
              Row(
                children: [
                  _buildCalcBtn('4'), _buildCalcBtn('5'), _buildCalcBtn('6'),
                  _buildCalcBtn('+', textColor: Colors.greenAccent, bgColor: Colors.greenAccent.withOpacity(0.1)),
                ],
              ),
              Row(
                children: [
                  _buildCalcBtn('1'), _buildCalcBtn('2'), _buildCalcBtn('3'),
                  _buildCalcBtn('=', textColor: Colors.black, bgColor: Colors.greenAccent),
                ],
              ),
              Row(
                children: [
                  _buildCalcBtn('00', flex: 1),
                  _buildCalcBtn('0', flex: 2),
                  _buildCalcBtn('.', flex: 1),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}