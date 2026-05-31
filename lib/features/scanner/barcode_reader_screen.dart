import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../utils/haptics.dart';

class BarcodeReaderScreen extends StatefulWidget {
  const BarcodeReaderScreen({super.key});

  @override
  State<BarcodeReaderScreen> createState() => _BarcodeReaderScreenState();
}

class _BarcodeReaderScreenState extends State<BarcodeReaderScreen> {
  final MobileScannerController cameraController = MobileScannerController();
  bool _isScanned = false; 
  bool _isTorchOn = false;

  @override
  void dispose() {
    cameraController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Сканування'),
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(
            icon: Icon(_isTorchOn ? Icons.flash_on : Icons.flash_off, color: _isTorchOn ? Colors.yellow : Colors.white54),
            onPressed: () {
              cameraController.toggleTorch();
              setState(() => _isTorchOn = !_isTorchOn);
            },
          ),
        ],
      ),
      body: MobileScanner(
        controller: cameraController,
        onDetect: (BarcodeCapture capture) {
          if (_isScanned) return; 

          final List<Barcode> barcodes = capture.barcodes;
          for (final barcode in barcodes) {
            if (barcode.rawValue != null) {
              _isScanned = true;
              AppHaptics.success(); 
              Navigator.pop(context, barcode.rawValue!);
              break; 
            }
          }
        },
      ),
    );
  }
}