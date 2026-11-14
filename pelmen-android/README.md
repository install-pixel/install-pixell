# Пельмень — Android-застосунок

Оболонка для гри `../pelmen-game/pelmen.html`: показує її на весь екран і додає вібрацію,
кнопку «Назад», паузу при згортанні та екран завантаження. Інтернет не потрібен.

- Пакет: `com.installpixel.pelmen`, назва під іконкою — «Пельмень», мінімум Android 7.0.
- Збирає GitHub Actions (`.github/workflows/pelmen-apk.yml`):
  - push з змінами гри/оболонки → тестовий APK у вкладці *Actions → Artifacts*;
  - коміт із позначкою `[release]` (версія — у `version.txt`) або тег `pelmen-vX.Y` → реліз на сторінці *Releases*.
- Іконки згенеровано з того самого малюнка пельменя, що в грі (`iconLayer` у pelmen.html).

## Ключ підпису
`keystore/pelmen-release.jks` — один ключ для всіх версій, тому оновлення встановлюються поверх
старої версії й прогрес не губиться. Ключ лежить у репозиторії, щоб збірка працювала без
додаткових налаштувань. Перед публікацією в Google Play його варто замінити на ключ із секретів
GitHub (`PELMEN_STORE_PASSWORD`, `PELMEN_KEY_PASSWORD`).

## Локальна збірка
Потрібні JDK 17, Gradle 8.11+ і Android SDK:
```
cp ../pelmen-game/pelmen.html app/src/main/assets/index.html
gradle assembleRelease
```
