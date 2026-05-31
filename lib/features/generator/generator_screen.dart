import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:barcode_widget/barcode_widget.dart';

import '../../providers/db_provider.dart';
import '../scanner/scanner_screen.dart'; // Підключили сканер

final codeTextProvider = StateProvider<String>((ref) => 'DSV-12345');
final isQrProvider = StateProvider<bool>((ref) => true);

class GeneratorScreen extends ConsumerStatefulWidget {
  const GeneratorScreen({super.key});

  @override
  ConsumerState<GeneratorScreen> createState() => _GeneratorScreenState();
}

class _GeneratorScreenState extends ConsumerState<GeneratorScreen> {
  final TextEditingController _controller = TextEditingController(text: 'DSV-12345');

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final codeText = ref.watch(codeTextProvider);
    final isQr = ref.watch(isQrProvider);
    final savedCodes = ref.watch(savedCodesProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Генератор'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            SegmentedButton<bool>(
              segments: const [
                ButtonSegment(value: true, label: Text('QR Код')),
                ButtonSegment(value: false, label: Text('Штрихкод')),
              ],
              selected: {isQr},
              onSelectionChanged: (Set<bool> newSelection) {
                ref.read(isQrProvider.notifier).state = newSelection.first;
              },
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: isQr
                  ? QrImageView(
                      data: codeText,
                      version: QrVersions.auto,
                      size: 150.0,
                    )
                  : BarcodeWidget(
                      barcode: Barcode.code128(),
                      data: codeText,
                      width: 200,
                      height: 80,
                    ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _controller,
                    onChanged: (value) {
                      if (value.isNotEmpty) {
                        ref.read(codeTextProvider.notifier).state = value;
                      }
                    },
                    decoration: InputDecoration(
                      labelText: 'Введіть текст або SKU',
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.document_scanner),
                        onPressed: () async {
                          // Викликаємо камеру
                          final scannedText = await Navigator.push<String>(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const ScannerScreen(),
                            ),
                          );

                          // Якщо щось зісканували
                          if (scannedText != null && scannedText.isNotEmpty) {
                            ref.read(codeTextProvider.notifier).state = scannedText;
                            _controller.text = scannedText;
                          }
                        },
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    minimumSize: const Size(60, 60),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    backgroundColor: Colors.grey.shade900,
                  ),
                  onPressed: () {
                    if (codeText.isNotEmpty) {
                      ref.read(savedCodesProvider.notifier).addCode(codeText, isQr);
                      FocusScope.of(context).unfocus();
                    }
                  },
                  child: const Icon(Icons.save, color: Colors.greenAccent),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Divider(),
            Expanded(
              child: savedCodes.isEmpty
                  ? const Center(
                      child: Text('Історія порожня', style: TextStyle(color: Colors.white54)),
                    )
                  : ListView.builder(
                      itemCount: savedCodes.length,
                      itemBuilder: (context, index) {
                        final item = savedCodes[index];
                        return Dismissible(
                          key: ValueKey(item.id),
                          direction: DismissDirection.endToStart,
                          background: Container(
                            color: Colors.redAccent,
                            alignment: Alignment.centerRight,
                            padding: const EdgeInsets.only(right: 20),
                            child: const Icon(Icons.delete, color: Colors.white),
                          ),
                          onDismissed: (direction) {
                            ref.read(savedCodesProvider.notifier).deleteCode(item.id);
                          },
                          child: ListTile(
                            leading: Icon(
                              item.isQr ? Icons.qr_code_2 : Icons.barcode_reader,
                              color: Colors.white70,
                            ),
                            title: Text(
                              item.data,
                              style: const TextStyle(fontWeight: FontWeight.bold),
                            ),
                            subtitle: Text(
                              '${item.createdAt.day.toString().padLeft(2, '0')}.${item.createdAt.month.toString().padLeft(2, '0')}  ${item.createdAt.hour.toString().padLeft(2, '0')}:${item.createdAt.minute.toString().padLeft(2, '0')}',
                              style: const TextStyle(fontSize: 12, color: Colors.white54),
                            ),
                            trailing: IconButton(
                              icon: Icon(
                                item.isFavorite ? Icons.star : Icons.star_border,
                                color: item.isFavorite ? Colors.yellow : Colors.white38,
                              ),
                              onPressed: () {
                                ref.read(savedCodesProvider.notifier).toggleFavorite(item.id);
                              },
                            ),
                            onTap: () {
                              ref.read(codeTextProvider.notifier).state = item.data;
                              ref.read(isQrProvider.notifier).state = item.isQr;
                              _controller.text = item.data;
                            },
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}