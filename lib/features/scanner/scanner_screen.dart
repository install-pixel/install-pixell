import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:image_picker/image_picker.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key});

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  final MobileScannerController controller = MobileScannerController();
  bool _isProcessingOcr = false; // Індикатор завантаження для тексту

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  // Функція для OCR (читання тексту з фотографії)
  Future<void> _scanTextFromImage() async {
    final picker = ImagePicker();
    // Відкриваємо камеру для фото
    final XFile? image = await picker.pickImage(source: ImageSource.camera);
    
    if (image != null) {
      setState(() => _isProcessingOcr = true);
      
      try {
        final inputImage = InputImage.fromFilePath(image.path);
        // Запускаємо розпізнавання латинських символів та цифр
        final textRecognizer = TextRecognizer(script: TextRecognitionScript.latin);
        final RecognizedText recognizedText = await textRecognizer.processImage(inputImage);
        
        await textRecognizer.close();

        // Якщо текст знайдено
        if (recognizedText.blocks.isNotEmpty && mounted) {
          _showTextBlocksSheet(recognizedText.blocks);
        } else if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Текст не знайдено на фото')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Помилка : $e')),
          );
        }
      } finally {
        if (mounted) {
          setState(() => _isProcessingOcr = false);
        }
      }
    }
  }

  // Показуємо панель зі знайденими блоками тексту
  void _showTextBlocksSheet(List<TextBlock> blocks) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.grey.shade900,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        return Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Знайдений текст (натисніть потрібний):',
                style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: ListView.builder(
                  itemCount: blocks.length,
                  itemBuilder: (context, index) {
                    final text = blocks[index].text;
                    return Card(
                      color: Colors.black,
                      margin: const EdgeInsets.only(bottom: 8),
                      child: ListTile(
                        title: Text(text, style: const TextStyle(color: Colors.greenAccent)),
                        trailing: const Icon(Icons.edit, color: Colors.white54),
                        onTap: () {
                          Navigator.pop(context); // Закриваємо нижню панель
                          _showEditDialog(text);  // Відкриваємо вікно корекції
                        },
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  // Вікно для корекції вибраного тексту перед поверненням
  void _showEditDialog(String initialText) {
    final TextEditingController editController = TextEditingController(text: initialText);
    
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: Colors.grey.shade900,
          title: const Text('Корекція тексту', style: TextStyle(color: Colors.white)),
          content: TextField(
            controller: editController,
            style: const TextStyle(color: Colors.white),
            decoration: const InputDecoration(
              enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: Colors.greenAccent)),
              focusedBorder: UnderlineInputBorder(borderSide: BorderSide(color: Colors.green)),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Скасувати', style: TextStyle(color: Colors.white54)),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
              onPressed: () {
                final finalText = editController.text.trim();
                if (finalText.isNotEmpty) {
                  Navigator.pop(context); // Закриваємо діалог
                  controller.stop();      // Зупиняємо фонову камеру
                  Navigator.pop(context, finalText); // Повертаємо текст у Генератор!
                }
              },
              child: const Text('Застосувати', style: TextStyle(color: Colors.white)),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Сканер'),
      ),
      body: Stack(
        children: [
          // Жива камера для штрихкодів
          MobileScanner(
            controller: controller,
            onDetect: (capture) {
              final List<Barcode> barcodes = capture.barcodes;
              if (barcodes.isNotEmpty && !_isProcessingOcr) {
                final String? code = barcodes.first.rawValue;
                if (code != null) {
                  controller.stop();
                  Navigator.pop(context, code);
                }
              }
            },
          ),
          
          // Індикатор завантаження під час обробки фото
          if (_isProcessingOcr)
            Container(
              color: Colors.black87,
              child: const Center(
                child: CircularProgressIndicator(color: Colors.greenAccent),
              ),
            ),
        ],
      ),
      // Кнопка розпізнавання звичайного тексту
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _scanTextFromImage,
        backgroundColor: Colors.greenAccent,
        icon: const Icon(Icons.text_fields, color: Colors.black),
        label: const Text('Зчитати текст', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }
}