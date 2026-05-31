import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:share_plus/share_plus.dart';

import '../../providers/delivery_provider.dart';
import '../../models/delivery_session.dart';
import 'pallet_calculator_screen.dart';
import '../scanner/barcode_reader_screen.dart';
import '../calculator/global_calculator_sheet.dart';
import '../../utils/haptics.dart';

class SessionDetailScreen extends ConsumerStatefulWidget {
  final int sessionId;
  const SessionDetailScreen({super.key, required this.sessionId});

  @override
  ConsumerState<SessionDetailScreen> createState() => _SessionDetailScreenState();
}

class _SessionDetailScreenState extends ConsumerState<SessionDetailScreen> {
  bool _isProcessing = false;
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  // Генерація та відправка текстового звіту
  void _exportSession(DeliverySession session, List<DocumentPhoto> photos) {
    final buffer = StringBuffer();
    buffer.writeln('📦 ЗВІТ ПРО ПРИЙМАННЯ');
    buffer.writeln('База: ${session.name ?? 'Без назви'}');
    buffer.writeln('Рампа: ${session.rampNumber} | Тип: ${session.deliveryType}');
    buffer.writeln('Загальна кількість палет: ${session.totalCalculatedPallets}');
    buffer.writeln('-------------------------');

    if (photos.isEmpty) {
      buffer.writeln('Накладних ще немає.');
    } else {
      for (int i = 0; i < photos.length; i++) {
        final p = photos[i];
        final status = p.isDone ? '✅ Оброблено' : '⏳ В процесі';
        buffer.writeln('Накладна #${i + 1} [$status]:');
        
        if (p.comment != null && p.comment!.isNotEmpty) {
          buffer.writeln(p.comment);
        } else {
          buffer.writeln('Коментарів чи розрахунків немає.');
        }
        buffer.writeln(''); // Пустий рядок між накладними
      }
    }

    Share.share(buffer.toString(), subject: 'Звіт: Рампа ${session.rampNumber}');
  }

  Future<void> _openBarcodeScanner() async {
    final result = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (context) => const BarcodeReaderScreen()),
    );

    if (result != null && result.isNotEmpty) {
      setState(() {
        _searchController.text = result;
        _searchQuery = result.toLowerCase();
      });
    }
  }

  Future<void> _openTextScanner() async {
    final picker = ImagePicker();
    final XFile? image = await picker.pickImage(source: ImageSource.camera, imageQuality: 80);
    if (image != null) {
      setState(() => _isProcessing = true);
      try {
        final inputImage = InputImage.fromFilePath(image.path);
        final textRecognizer = TextRecognizer(script: TextRecognitionScript.latin);
        final RecognizedText recognizedText = await textRecognizer.processImage(inputImage);
        textRecognizer.close();

        Set<String> foundNumbers = {};
        for (TextBlock block in recognizedText.blocks) {
          for (TextLine line in block.lines) {
            for (TextElement element in line.elements) {
              if (element.text.contains(RegExp(r'\d'))) {
                foundNumbers.add(element.text);
              }
            }
          }
        }

        setState(() => _isProcessing = false);

        if (foundNumbers.isEmpty) {
          if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Цифр не знайдено на фото')));
          return;
        }

        if (mounted) {
          showDialog(
            context: context,
            builder: (ctx) => AlertDialog(
              backgroundColor: const Color(0xFF1E1E1E),
              title: const Text('Знайдені номери:', style: TextStyle(color: Colors.white)),
              content: Wrap(
                spacing: 10,
                runSpacing: 10,
                children: foundNumbers.map((num) => ActionChip(
                  label: Text(num, style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
                  backgroundColor: Colors.yellowAccent,
                  onPressed: () {
                    Navigator.pop(ctx);
                    setState(() {
                      _searchController.text = num;
                      _searchQuery = num.toLowerCase();
                    });
                  },
                )).toList(),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Скасувати', style: TextStyle(color: Colors.white54)),
                )
              ],
            ),
          );
        }
      } catch (e) {
        setState(() => _isProcessing = false);
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Помилка OCR: $e')));
      }
    }
  }

  Future<void> _takeDocumentPhoto() async {
    final picker = ImagePicker();
    final XFile? image = await picker.pickImage(
      source: ImageSource.camera, 
      imageQuality: 50, 
      maxWidth: 1600,
    );

    if (image != null) {
      setState(() => _isProcessing = true);
      try {
        final inputImage = InputImage.fromFilePath(image.path);
        final textRecognizer = TextRecognizer(script: TextRecognitionScript.latin);
        final RecognizedText recognizedText = await textRecognizer.processImage(inputImage);
        
        List<String> words = [];
        for (TextBlock block in recognizedText.blocks) {
          for (TextLine line in block.lines) {
            for (TextElement element in line.elements) {
              words.add(element.text.toLowerCase()); 
            }
          }
        }
        textRecognizer.close();

        await ref.read(deliveryControllerProvider).addDocumentPhoto(
          widget.sessionId,
          image.path,
          words,
          'Накладна', // Задаємо тип накладної за замовчуванням
          words,
        );

      } catch (e) {
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Помилка OCR: $e')));
      } finally {
        setState(() => _isProcessing = false);
      }
    }
  }

  void _showCommentDialog(int photoId, String? currentComment) {
    final controller = TextEditingController(text: currentComment ?? '');
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        title: const Text('Коментар до фото'),
        content: TextField(
          controller: controller,
          maxLines: 3,
          style: const TextStyle(color: Colors.white),
          decoration: InputDecoration(
            hintText: 'Наприклад: Пошкоджена коробка, не вистачає 2 шт...',
            hintStyle: const TextStyle(color: Colors.white38),
            filled: true,
            fillColor: Colors.black26,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Скасувати', style: TextStyle(color: Colors.white54)),
          ),
          TextButton(
            onPressed: () {
              ref.read(deliveryControllerProvider).updateComment(photoId, controller.text);
              Navigator.pop(ctx);
            },
            child: const Text('Зберегти', style: TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  void _openFullScreenImage(String imagePath) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            elevation: 0,
            iconTheme: const IconThemeData(color: Colors.white),
          ),
          extendBodyBehindAppBar: true, 
          body: InteractiveViewer(
            panEnabled: true,
            minScale: 0.5,
            maxScale: 4.0,
            boundaryMargin: const EdgeInsets.all(double.infinity), 
            clipBehavior: Clip.none,
            child: Center(
              child: Image.file(
                File(imagePath),
                fit: BoxFit.contain, 
                width: double.infinity,
                height: double.infinity,
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final photosAsync = ref.watch(sessionPhotosProvider(widget.sessionId));
    
    final sessionsAsync = ref.watch(deliverySessionsProvider);
    final currentSession = sessionsAsync.value?.firstWhere(
      (s) => s.id == widget.sessionId, 
      orElse: () => throw Exception('Session not found'), 
    );
    final totalPallets = currentSession?.totalCalculatedPallets ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Деталі бази', style: TextStyle(fontSize: 18)),
            if (totalPallets > 0)
              Text(
                'Всього палет: $totalPallets', 
                style: const TextStyle(fontSize: 14, color: Colors.greenAccent, fontWeight: FontWeight.bold),
              ),
          ],
        ),
        actions: [
          // ДОДАНО: Кнопка експорту звіту
          IconButton(
            icon: const Icon(Icons.ios_share_rounded, color: Colors.white),
            tooltip: 'Відправити звіт',
            onPressed: () {
              if (currentSession != null && photosAsync.value != null) {
                _exportSession(currentSession, photosAsync.value!);
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.calculate_outlined, color: Colors.greenAccent),
            tooltip: 'Глобальний калькулятор',
            onPressed: () {
              GlobalCalculatorSheet.show(context);
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: TextField(
              controller: _searchController,
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Пошук SKU...',
                prefixIcon: const Icon(Icons.search, color: Colors.white54),
                suffixIcon: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (_searchQuery.isNotEmpty)
                      IconButton(
                        icon: const Icon(Icons.clear, color: Colors.white54),
                        onPressed: () {
                          _searchController.clear();
                          setState(() => _searchQuery = '');
                        },
                      ),
                    IconButton(
                      icon: const Icon(Icons.text_fields, color: Colors.yellowAccent),
                      tooltip: 'Сфотографувати цифри',
                      onPressed: _openTextScanner,
                    ),
                    IconButton(
                      icon: const Icon(Icons.qr_code_scanner, color: Colors.greenAccent),
                      tooltip: 'Зісканувати штрих-код',
                      onPressed: _openBarcodeScanner,
                    ),
                  ],
                ),
                filled: true,
                fillColor: const Color(0xFF1E1E1E),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
              ),
              onChanged: (val) => setState(() => _searchQuery = val.toLowerCase()),
            ),
          ),
          
          if (_isProcessing)
            const Padding(
              padding: EdgeInsets.all(16.0),
              child: Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
            ),

          Expanded(
            child: photosAsync.when(
              data: (photos) {
                final filteredPhotos = _searchQuery.isEmpty 
                    ? photos 
                    : photos.where((p) {
                        if (p.recognizedText == null) return false;
                        return p.recognizedText!.any((word) => word.contains(_searchQuery));
                      }).toList();

                if (filteredPhotos.isEmpty) {
                  return const Center(child: Text('Фотографій не знайдено', style: TextStyle(color: Colors.white54)));
                }

                return ListView.builder(
                  itemCount: filteredPhotos.length,
                  itemBuilder: (context, index) {
                    final photo = filteredPhotos[index];
                    final hasComment = photo.comment != null && photo.comment!.isNotEmpty;

                    return Card(
                      color: const Color(0xFF1E1E1E),
                      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          GestureDetector(
                            onTap: () => _openFullScreenImage(photo.imagePath!),
                            child: Container(
                              height: 250,
                              width: double.infinity,
                              decoration: BoxDecoration(
                                borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
                                image: DecorationImage(
                                  image: FileImage(File(photo.imagePath!)),
                                  fit: BoxFit.cover,
                                ),
                              ),
                              child: Align(
                                alignment: Alignment.topRight,
                                child: Container(
                                  margin: const EdgeInsets.all(8),
                                  padding: const EdgeInsets.all(6),
                                  decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(8)),
                                  child: const Icon(Icons.zoom_out_map, color: Colors.white, size: 20),
                                ),
                              ),
                            ),
                          ),
                          
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                            child: ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.white12, 
                                foregroundColor: Colors.greenAccent,
                                minimumSize: const Size.fromHeight(46), 
                                elevation: 0,
                              ),
                              icon: const Icon(Icons.calculate),
                              label: const Text('Розрахувати палети (Додати в базу)', style: TextStyle(fontWeight: FontWeight.bold)),
                              onPressed: () {
                                showDialog(
                                  context: context,
                                  builder: (ctx) {
                                    final skuCtrl = TextEditingController(text: _searchQuery); 
                                    return AlertDialog(
                                      backgroundColor: const Color(0xFF1E1E1E),
                                      title: const Text('Який артикул (SKU) рахуємо?', style: TextStyle(fontSize: 18, color: Colors.white)),
                                      content: TextField(
                                        controller: skuCtrl,
                                        style: const TextStyle(color: Colors.white),
                                        decoration: InputDecoration(
                                          hintText: 'Введіть SKU...',
                                          filled: true,
                                          fillColor: Colors.black26,
                                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                                        ),
                                      ),
                                      actions: [
                                        TextButton(
                                          onPressed: () => Navigator.pop(ctx), 
                                          child: const Text('Скасувати', style: TextStyle(color: Colors.white54))
                                        ),
                                        TextButton(
                                          onPressed: () {
                                            Navigator.pop(ctx);
                                            Navigator.push(
                                              context,
                                              MaterialPageRoute(
                                                builder: (context) => PalletCalculatorScreen(
                                                  sessionId: widget.sessionId,
                                                  photoId: photo.id,
                                                  sku: skuCtrl.text.isEmpty ? 'Невідомий SKU' : skuCtrl.text, 
                                                ),
                                              ),
                                            );
                                          },
                                          child: const Text('Порахувати', style: TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold))
                                        ),
                                      ],
                                    );
                                  }
                                );
                              },
                            ),
                          ),
                          
                          ListTile(
                            title: Text(
                              photo.isDone ? 'Накладна оброблена' : 'Очікує обробки',
                              style: TextStyle(
                                color: photo.isDone ? Colors.greenAccent : Colors.orangeAccent,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            subtitle: hasComment 
                                ? Padding(
                                    padding: const EdgeInsets.only(top: 4.0),
                                    child: Text('💬 ${photo.comment}', style: const TextStyle(color: Colors.white70)),
                                  ) 
                                : null,
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                IconButton(
                                  icon: Icon(
                                    hasComment ? Icons.comment : Icons.add_comment, 
                                    color: hasComment ? Colors.greenAccent : Colors.white54,
                                  ),
                                  onPressed: () => _showCommentDialog(photo.id, photo.comment),
                                ),
                                IconButton(
                                  icon: Icon(photo.isDone ? Icons.check_circle : Icons.check_circle_outline, 
                                        color: photo.isDone ? Colors.greenAccent : Colors.white54),
                                  onPressed: () => ref.read(deliveryControllerProvider).toggleStatus(photo.id, photo.isDone),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.delete, color: Colors.redAccent),
                                  onPressed: () => ref.read(deliveryControllerProvider).deletePhoto(photo.id),
                                ),
                              ],
                            ),
                          )
                        ],
                      ),
                    );
                  },
                );
              },
              loading: () => const Center(child: CircularProgressIndicator(color: Colors.greenAccent)),
              error: (err, stack) => Center(child: Text('Помилка: $err')),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _takeDocumentPhoto,
        backgroundColor: Colors.greenAccent,
        child: const Icon(Icons.camera_alt, color: Colors.black),
      ),
    );
  }
}