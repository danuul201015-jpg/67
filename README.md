# Golden Drop — APK через GitHub

1. Создай пустой репозиторий на GitHub и загрузи в него всё содержимое этой папки (включая папку `.github`).
   Файл `www/index.html` большой (~19 МБ), поэтому лучше пушить через git, а не через веб-интерфейс:
   `git init && git add . && git commit -m init && git branch -M main && git remote add origin <URL> && git push -u origin main`
2. Открой вкладку **Actions** — сборка `Build APK` запустится сама (или нажми **Run workflow**).
3. Когда сборка станет зелёной, открой её и скачай артефакт **golden-drop-apk** — внутри `app-debug.apk`.
4. Установи APK на телефон (разреши установку из неизвестных источников).

Заметки:
- `app-debug.apk` подписан отладочным ключом — для личной установки этого достаточно. Для Google Play нужен release-ключ.
- Валюта в игре виртуальная. Реальные платежи и вывод не подключены.
- Изменить название/иконку: `capacitor.config.json` и, после `npx cap add android`, папка `android/app/src/main/res`.


### Аватар
В `www/avatar.png` добавлена новая аватарка Golden Drop. Она подключена в `www/index.html` и попадёт в APK при сборке.
