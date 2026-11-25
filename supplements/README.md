# Добавки (Supps)

Простий офлайн-трекер добавок для Android: що приймати сьогодні (за сном і їжею, а не за годинником), цикли й
чергування, залишок і прогноз «вистачить до», витрати, щоденна оцінка самопочуття й порівняння «з прийомом / без».

- Kotlin + Jetpack Compose, Room; без дозволу INTERNET.
- Оформлення — за `THEMES.md` (дві темні теми «Скло» й «Аркуш», компоненти з QRaft). Кнопка «Назад» — унизу.
- Мови: uk / pl / en (`values-uk`, `values-pl`, `values`).
- APK збирає `.github/workflows/supplements.yml`; постійне посилання на останню збірку:
  `releases/download/supps-latest/Supps.apk`.

## Схема JSON (імпорт / копія)

```json
{
  "format": "supps", "version": 1,
  "groups": [ { "key": "adapt", "name": "Адаптогени", "breakDays": 0 } ],
  "supplements": [
    { "name": "Омега-3", "form": "capsule", "mg": 750, "perDose": 2, "slot": "wake",
      "schedule": { "type": "daily" }, "stock": null, "pack": 60, "price": null, "note": "" },
    { "name": "Бакопа", "schedule": { "type": "cycle", "on": 84, "off": 28, "doneDays": 0 } },
    { "name": "Родіола", "schedule": { "type": "group", "group": "adapt", "days": 56, "order": 0, "doneDays": 0 } },
    { "name": "Мелатонін", "schedule": { "type": "asNeeded" } }
  ]
}
```

`form`: capsule | tablet | powder | drops | other. `slot`: wake | day | dinner | bed. Невідоме — `null` або не писати.
`doneDays` — скільки днів уже приймаєш (цикл чи черга продовжиться з потрібного дня). Повна копія з Налаштувань —
той самий формат плюс розділ `backup` з усією історією.

## Код

- `data/` — сутності Room, `Schedule` (цикли, чергування, «вистачить до»), `Stats`, `JsonIO`. Логіка покрита тестами.
- `ui/` — компоненти тем (`Sheet.kt`, `Kit.kt`, `Switches.kt`, `Bounce.kt`, `Glyphs.kt` — з QRaft) і екрани.
