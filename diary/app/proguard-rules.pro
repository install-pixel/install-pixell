# Правила R8 для релізної збірки «Щоденника».
-keepattributes SourceFile,LineNumberTable
# SQLCipher викликає свої класи з нативного коду (JNI) — їх не можна перейменовувати чи викидати.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.** { *; }
