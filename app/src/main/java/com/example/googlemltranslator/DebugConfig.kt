package com.example.googlemltranslator

enum class CompatibilityMode {
    STANDARD,
    EXPERIMENTAL,
    HYBRID
}

var DEBUG_COMPATIBILITY_MODE =
    CompatibilityMode.HYBRID

/*
 * 1 — результат
 * 2 — смена направления / языка
 * 3 — ввод текста
 * 4 — кнопка "Перевести"
 * 5 — настройка расположения
 * 6 — управление языками
 * 7 — ML Kit / API
 *
 * Порядок:
 *
 *          ВЕРХ
 *      ┌─────────┐
 *      │    1    │
 *      └─────────┘
 *
 *   ┌───┬───┬───┬───┐
 *   │ 2 │ 5 │ 6 │ 7 │
 *   └───┴───┴───┴───┘
 *
 *      ┌─────────┐
 *      │    3    │
 *      │       4 │
 *      └─────────┘
 *          НИЗ
 */
val DEBUG_LAYOUT_ORDER = arrayOf(
    intArrayOf(1),
    intArrayOf(2, 5, 6, 7),
    intArrayOf(3, 4)
)

var DEBUG_SHOW_ELEMENT_IDS = false