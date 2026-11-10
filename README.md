# XO

Android-приложение: вода, еда и ходьба за день, общий показатель «Баланс дня». Данные хранятся только на телефоне.

- Kotlin + Jetpack Compose, Room, WorkManager. `minSdk 26`, `targetSdk 36`.
- Шаги: датчик шагов телефона; на телефонах без него — счёт по движению (акселерометр) и ручной ввод.
- Русский и английский.

## Сборка

```bash
cd app
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Отладочный APK: `app/app/build/outputs/apk/debug/app-debug.apk`.

Релиз для Google Play подписывается ключом из `app/keystore.properties` (в репозиторий не попадает):

```
storeFile=../upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

```bash
cd app
./gradlew :app:bundleRelease
```

## Документы

- `docs/PRODUCT.md` — что делаем, `docs/DESIGN.md` — токены и анимации, `docs/DECISIONS.md` — журнал решений.
- `docs/PRIVACY.md` — политика конфиденциальности.
- `tests/TESTER-CHECKLIST.md` — ручная проверка.
