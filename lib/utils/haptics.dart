import 'package:flutter/services.dart';

class AppHaptics {
  // Легкий відгук (наприклад, при натисканні кнопок)
  static void light() {
    HapticFeedback.lightImpact();
  }

  // Чіткий, середній відгук (ідеально для успішного сканування)
  static void success() {
    HapticFeedback.mediumImpact();
  }

  // Помилка або нестача (різка вібрація)
  static void error() {
    HapticFeedback.heavyImpact();
  }
}