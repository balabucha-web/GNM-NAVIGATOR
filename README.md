# GNM-NAVIGATOR# (ГНМ Навигатор)

Офлайн-приложение в одном файле (`index.html`) для навигации по СБП ГНМ:
- поиск по органам, конфликтам, симптомам и диагнозам,
- вкладки по системам,
- тёмная/светлая тема,
- заметки с сохранением в LocalStorage.

## Как запустить локально
1. Скачай `index.html`.
2. Открой двойным кликом в браузере (Chrome/Firefox/Safari/Edge).
3. Всё работает офлайн; заметки сохраняются в LocalStorage.

## GitHub Pages (онлайн)
1. Создай репозиторий `gnm-navigator`.
2. Добавь файлы: `index.html` и `README.md`.
3. В Settings → Pages → Deploy from branch:
   - Branch: `main`
   - Folder: `/ (root)`
4. Открой: `https://<твой-логин>.github.io/gnm-navigator/`

## Структура данных
База находится внутри скрипта `const DB = { ... }`.
Разделы:
- `endoderma` — заполнено,
- `meso_old` — заполнено,
- `meso_new` — WIP,
- `ectoderma` — WIP,
- `nervous` — WIP.

## Лицензия
© Andrey Balabucha. Локальные заметки остаются только у пользователя.
