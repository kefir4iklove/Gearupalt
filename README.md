# FloatHub — плавающие окна для Android

Аналог «плавающих инструментов» из GearUp Booster: поверх любого приложения
висит перетаскиваемый кружок; тап по нему открывает меню с мини-окнами.

## Возможности
- 🖼 **Галерея** — мини-окно с сеткой фото с телефона (MediaStore), просмотр по тапу
- 🌐 **Браузер** — WebView с адресной строкой, назад/домой, открытие во внешнем браузере
- 💬 **Discord** — веб-версия Discord в плавающем окне (или «открыть в приложении»)
- 📝 **Заметки** — быстрый блокнот (сохраняется в SharedPreferences)
- 📱 **Приложения** — список всех установленных приложений с запуском одним тапом
- Окна перетаскиваются за заголовок, можно открыть несколько одновременно

## Требования
- Android 8.0+ (API 26)
- Разрешение «Поверх других окон» (выдаётся из главного экрана приложения)
- Доступ к фото — для галереи

## Сборка из исходников
```bash
aapt2 compile --dir res -o res.zip
aapt2 link -o base.apk -I android.jar --manifest AndroidManifest.xml res.zip \
  --java gen --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34
javac -source 8 -target 8 -bootclasspath android.jar:core-lambda-stubs.jar \
  -d classes $(find gen src -name '*.java')
d8 --release --lib android.jar --min-api 26 --output dex $(find classes -name '*.class')
zip base.apk classes.dex && zipalign -f 4 base.apk out.apk
apksigner sign --ks debug.keystore --out FloatHub.apk out.apk
```

Готовый APK: `FloatHub.apk` (подписан debug-ключом; при установке разрешите
«устанавливать из неизвестных источников»).
