# ModelSpace (Fabric, Minecraft 26.3)

Этап 1: каркас, блок Workspace, предмет Workspace Key.
Этап 2: сессия редактирования (freecam + панель инструментов), предметы в креативе.

## Запуск
Нужны JDK 25+ и Gradle (для генерации wrapper).
1. В папке проекта: `gradle wrapper --gradle-version 9.7.1`
2. `./gradlew build` — jar в `build/libs/`
3. `./gradlew runClient` — тестовый клиент
4. В игре: `/give @s modelspace:workspace` и `/give @s modelspace:workspace_key`

Актуальные версии Loom/Fabric API проверяй на https://fabricmc.net/develop

## Этап 3: редактор

ПКМ по блоку Workspace с Workspace Key — открывается редактор (мышь свободна).

**Камера:** WASD/Space/Shift — полёт, Ctrl — быстрее; ПКМ+тяни — взгляд; СКМ+тяни — панорама;
колесо — зум; Ctrl+колесо — скорость полёта.

**Части модели:** слева список (клик — выбрать, Ctrl — переключить, Shift — диапазон,
двойной клик — навести камеру, квадрат справа — видимость). Добавить блок: кнопка «+ Добавить»
(берёт блок из руки, где нет ключа) или поле id внизу списка (`minecraft:oak_stairs` + Enter).

**Гизмо:** V/R/B — Move/Rotate/Scale, G — Global/Local, U — режим pivot, M — Обычный/Точный
(Обычный: шаг 1/16 блока и 15°, Точный: 0.02 и 1°). Клик по блоку во вьюпорте — выбрать
(Shift/Ctrl — добавить).

**Числа:** справа поля Position/Rotation/Scale/Pivot — клик, ввод, Enter/Tab; колесо над полем — шаг.

**Прочее:** Ctrl+Z / Ctrl+Y — undo/redo, Ctrl+C/X/V — буфер, Ctrl+D — дубль, Ctrl+A — всё,
Delete — удалить, X/Y/Z — зеркало на месте (Alt — зеркальная копия относительно центра Workspace),
Ctrl+S — сохранить. Выход (Backspace/Esc) сохраняет автоматически.

Данные хранятся в BlockEntity блока Workspace; клиент отправляет их на сервер пакетом
`SaveModelPayload`, сервер проверяет дистанцию, ключ в руке и лимиты.

## Заметки по 26.3 (что исправлено при ревью)

- Кнопки мыши сравниваются через `InputConstants.MOUSE_BUTTON_*` (в 26.3 SDL вместо GLFW, значения 0/1/2 больше не верны).
- Поля ввода сообщают игре о фокусе текста (`Minecraft#onTextInputFocusChange`), иначе `charTyped` не приходит.
- Рендер блоков через `BlockModelResolver` + `BlockModelRenderState` (`submitBlock` убран в 26.1).
- `SaveModelPayload` использует компактный бинарный формат: serverbound-пакет ограничен 32767 байтами,
  а NBT-формат при >~150 частях его превышал (сервер разрывал соединение).
- Размер списка частей ограничивается на этапе декодирования.
