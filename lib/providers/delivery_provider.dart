import 'dart:math';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:isar/isar.dart';
import '../models/delivery_session.dart';
import 'db_provider.dart';

final deliverySessionsProvider = StreamProvider.autoDispose<List<DeliverySession>>((ref) {
  final isar = ref.watch(isarProvider);
  return isar.deliverySessions.where().sortByCreatedAtDesc().watch(fireImmediately: true);
});

final sessionPhotosProvider = StreamProvider.autoDispose.family<List<DocumentPhoto>, int>((ref, int sessionId) {
  final isar = ref.watch(isarProvider);
  return isar.documentPhotos.filter().sessionIdEqualTo(sessionId).sortByAddedAtDesc().watch(fireImmediately: true);
});

class DeliveryController {
  final Isar isar;
  DeliveryController(this.isar);

  Future<void> createSession(String name, int ramp, String type) async {
    final session = DeliverySession()
      ..name = name
      ..rampNumber = ramp
      ..deliveryType = type
      ..startTime = DateTime.now();
    await isar.writeTxn(() async { await isar.deliverySessions.put(session); });
  }

  Future<void> deleteSession(int sessionId) async {
    await isar.writeTxn(() async {
      await isar.documentPhotos.filter().sessionIdEqualTo(sessionId).deleteAll();
      await isar.palletCalculations.filter().sessionIdEqualTo(sessionId).deleteAll();
      await isar.deliverySessions.delete(sessionId);
    });
  }

  Future<void> toggleArchiveStatus(int sessionId) async {
    await isar.writeTxn(() async {
      final session = await isar.deliverySessions.get(sessionId);
      if (session != null) {
        session.isArchived = !session.isArchived;
        await isar.deliverySessions.put(session);
      }
    });
  }

  Future<void> addDocumentPhoto(
    int sessionId, 
    String path, 
    List<String> words, 
    String type, 
    List<String> recognizedText, 
  ) async {
    final photo = DocumentPhoto()
      ..sessionId = sessionId
      ..imagePath = path
      ..photoType = type
      ..recognizedText = recognizedText; 
    await isar.writeTxn(() async { await isar.documentPhotos.put(photo); });
  }

  Future<void> deletePhoto(int photoId) async {
    await isar.writeTxn(() async {
      final photo = await isar.documentPhotos.get(photoId);
      if (photo != null && photo.sessionId != null) {
        final calcs = await isar.palletCalculations.filter().photoIdEqualTo(photoId).findAll();
        int palletsToRemove = 0;
        for (var c in calcs) {
          int loose = (c.totalUnits ?? 0) % (c.unitsPerBox ?? 1);
          palletsToRemove += (c.fullPallets ?? 0) + (((c.remainderBoxes ?? 0) > 0 || loose > 0) ? 1 : 0);
        }
        
        final session = await isar.deliverySessions.get(photo.sessionId!);
        if (session != null) {
          session.totalCalculatedPallets = max(0, session.totalCalculatedPallets - palletsToRemove);
          await isar.deliverySessions.put(session);
        }
        
        await isar.palletCalculations.filter().photoIdEqualTo(photoId).deleteAll();
        await isar.documentPhotos.delete(photoId);
      }
    });
  }

  Future<void> updateComment(int photoId, String text) async {
    await isar.writeTxn(() async {
      final photo = await isar.documentPhotos.get(photoId);
      if (photo != null) {
        photo.comment = text;
        await isar.documentPhotos.put(photo);
      }
    });
  }

  Future<void> toggleStatus(int photoId, bool currentStatus) async {
    await isar.writeTxn(() async {
      final photo = await isar.documentPhotos.get(photoId);
      if (photo != null) {
        photo.isDone = !currentStatus;
        await isar.documentPhotos.put(photo);

        if (photo.sessionId != null) {
          final session = await isar.deliverySessions.get(photo.sessionId!);
          if (session != null) {
            final allPhotos = await isar.documentPhotos.filter().sessionIdEqualTo(session.id).findAll();
            if (allPhotos.isNotEmpty) {
              final allDone = allPhotos.every((p) => p.isDone);
              if (allDone && !session.isCompleted) {
                session.isCompleted = true;
                session.isArchived = true;
                session.endTime = DateTime.now();
              } else if (!allDone && session.isCompleted) {
                session.isCompleted = false;
                session.isArchived = false;
                session.endTime = null;
              }
              await isar.deliverySessions.put(session);
            }
          }
        }
      }
    });
  }

  Future<void> addPalletCalculation({
    required int sessionId,
    required int photoId,
    required String sku,
    required int totalUnits,
    required int unitsPerBox,
    required int boxesPerPallet,
  }) async {
    await isar.writeTxn(() async {
      final int looseUnits = totalUnits % unitsPerBox; // Рахуємо розпаровку
      final int totalBoxes = totalUnits ~/ unitsPerBox;
      final int fullPallets = totalBoxes ~/ boxesPerPallet;
      final int remainderBoxes = totalBoxes % boxesPerPallet;

      final calc = PalletCalculation()
        ..sessionId = sessionId
        ..photoId = photoId
        ..sku = sku
        ..totalUnits = totalUnits
        ..unitsPerBox = unitsPerBox
        ..boxesPerPallet = boxesPerPallet
        ..fullPallets = fullPallets
        ..remainderBoxes = remainderBoxes
        ..looseUnits = looseUnits; // Зберігаємо в базу

      await isar.palletCalculations.put(calc);

      final photo = await isar.documentPhotos.get(photoId);
      if (photo != null) {
        String newCommentLine = '📦 $sku: Повних палет: $fullPallets';
        if (remainderBoxes > 0) newCommentLine += ' | Залишок: $remainderBoxes кор.';
        if (looseUnits > 0) newCommentLine += ' | ⚠️ Розпаровка: $looseUnits од.';

        if (photo.comment == null || photo.comment!.isEmpty) {
          photo.comment = newCommentLine;
        } else {
          photo.comment = '${photo.comment}\n$newCommentLine';
        }
        await isar.documentPhotos.put(photo);
      }

      final session = await isar.deliverySessions.get(sessionId);
      if (session != null) {
        int palletsToAdd = fullPallets + ((remainderBoxes > 0 || looseUnits > 0) ? 1 : 0);
        session.totalCalculatedPallets += palletsToAdd;
        await isar.deliverySessions.put(session);
      }
    });
  }
}

final deliveryControllerProvider = Provider((ref) => DeliveryController(ref.watch(isarProvider)));

final dashboardStatsProvider = FutureProvider.autoDispose<Map<String, dynamic>>((ref) async {
  final isar = ref.watch(isarProvider);
  ref.watch(deliverySessionsProvider);

  final sessions = await isar.deliverySessions.where().findAll();
  final completedSessions = sessions.where((s) => s.isCompleted && s.startTime != null && s.endTime != null).toList();

  DeliverySession? fastest;
  DeliverySession? longest;
  DeliverySession? maxPalletsSession;
  DeliverySession? maxSkusSession;
  int maxSkuCount = 0;

  for (var s in completedSessions) {
    final diff = s.endTime!.difference(s.startTime!);
    if (fastest == null || diff < fastest.endTime!.difference(fastest.startTime!)) fastest = s;
    if (longest == null || diff > longest.endTime!.difference(longest.startTime!)) longest = s;
  }

  for (var s in sessions) {
    if (maxPalletsSession == null || s.totalCalculatedPallets > maxPalletsSession.totalCalculatedPallets) {
      maxPalletsSession = s;
    }
    final skusCount = await isar.palletCalculations.filter().sessionIdEqualTo(s.id).count();
    if (skusCount >= maxSkuCount && skusCount > 0) {
      maxSkuCount = skusCount;
      maxSkusSession = s;
    }
  }

  return {
    'fastest': fastest,
    'longest': longest,
    'maxPallets': maxPalletsSession,
    'maxSkus': maxSkusSession,
    'maxSkuCount': maxSkuCount,
  };
});