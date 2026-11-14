# PortihMod (Fabric 1.21.11, client-only)

Таргет худ, кейстроки (WASD, прыжок, ЛКМ/ПКМ, CPS), маленький крутящийся меч в руке,
звук победы, меню на весь экран со светлой/тёмной темой.

## Управление
- Right Shift - открыть меню (меняется в Настройки -> Управление -> Бинды клавиш -> PortihMod)
- Конфиг: `.minecraft/config/portihmod.json`

## Сборка
GitHub: вкладка Actions -> build -> Artifacts -> PortihMod (внутри jar).
Локально: `gradle build` (Gradle 9.2+, Java 21), jar в `build/libs/`.
Нужен Fabric Loader 0.18.4+ и Fabric API.
