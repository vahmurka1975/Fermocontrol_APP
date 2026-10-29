# ФермоКонтроль v1.0.3 (versionCode = 4)

Обновление поверх коммита `74d4386` из репозитория `https://github.com/vahmurka1975/Fermocontrol_APP`.

## Что входит в архив
- `app/build.gradle.kts` — версия поднята до `1.0.3` (`versionCode = 4`) для проверки обновления по воздуху (OTA).
- `app/src/main/java/ru/fermocontrol/app/Product.kt` — функции `nextActiveNumberForRestore` и `restoreArchivedProduct` для безопасного возврата продукта из архива с проверкой свободного номера.
- `app/src/main/java/ru/fermocontrol/app/ProductForms.kt` — в окне журнала продукта добавлены:
  - кнопка возврата продукта из архива в работу с диалогом подтверждения (с сохранением прежнего номера, если он свободен, либо назначением первого свободного номера);
  - кнопка удаления ошибочной записи взвешивания у активного продукта с подтверждением.
- `app/src/main/java/ru/fermocontrol/app/FermocontrolApp.kt` — подключение обработчиков `onDeleteWeighing` и `onUnarchive`.
- `app/src/test/java/ru/fermocontrol/app/ProductTest.kt` — 3 новых модульных JUnit-теста возврата из архива и сохранения истории.

## Установка и сборка
1. Распакуйте архив в корень проекта `C:\AndroidStudioProjects\Fermocontrol` с заменой совпадающих файлов.
2. В терминале Android Studio выполните:
   ```powershell
   .\gradlew.bat testDebugUnitTest assembleRelease --no-configuration-cache
   ```
3. Готовый подписанный APK будет создан по пути:
   `app/build/outputs/apk/release/app-release.apk`
4. Опубликуйте новый Release `v1.0.3` на GitHub и прикрепите `app-release.apk`, чтобы проверить обновление по воздуху со смартфона (кнопка «Данные → Проверить обновления»).
