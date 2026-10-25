# Правила R8 для релізної збірки Black Glass.

# Голос без інтернету: Vosk викликає рідну бібліотеку через JNA — JNA шукає класи й поля за назвами.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# Рядки стеку в локальному журналі помилок — з номерами рядків (назви класів R8 скорочує).
-keepattributes SourceFile,LineNumberTable
