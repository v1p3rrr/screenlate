package com.vpr.screenlate.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vpr.screenlate.R

/**
 * An item in the searchable settings index.
 *
 * @param page The settings page that hosts this setting.
 * @param titleRes The title string resource.
 * @param summaryRes Optional summary/hint string resource.
 * @param sectionRes The section title string resource (e.g. Bubble, Translation).
 * @param iconRes The icon of the section.
 * @param keywords Additional search terms in English or Russian.
 */
data class SettingsIndexItem(
    val page: SettingsPage,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int? = null,
    @StringRes val sectionRes: Int,
    @DrawableRes val iconRes: Int,
    val keywords: List<String> = emptyList(),
)

object SettingsIndex {
    val items: List<SettingsIndexItem> = listOf(
        // Bubble
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_visible,
            summaryRes = R.string.bubble_visible_hint,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("пузырь", "плавающий", "floating", "bubble", "tile", "шторка"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_aim,
            summaryRes = R.string.bubble_aim_hint,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("прицел", "палец", "центр", "aim", "finger", "center", "double tap", "двойной тап"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_dock_side,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("dock", "edge", "парковка", "край", "лево", "право", "left", "right"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_dock_top_bottom,
            summaryRes = R.string.bubble_dock_top_bottom_hint,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("верх", "низ", "top", "bottom", "gestures", "жесты", "край"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_highlight,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("подсветка", "выделение", "highlight", "word"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_haptics,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("вибрация", "отклик", "vibration", "haptic"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_color_title,
            summaryRes = R.string.bubble_color_use_app_hint,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("цвет", "color", "accent", "акцент", "theme", "тема"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BUBBLE,
            titleRes = R.string.bubble_hidden_apps,
            summaryRes = R.string.bubble_hidden_apps_hint,
            sectionRes = R.string.bubble_title,
            iconRes = R.drawable.ic_touch_app,
            keywords = listOf("скрывать", "приложения", "черный список", "hidden", "apps", "blacklist"),
        ),

        // Recognition
        SettingsIndexItem(
            page = SettingsPage.RECOGNITION,
            titleRes = R.string.bubble_ocr_engines,
            summaryRes = R.string.bubble_ocr_engines_info,
            sectionRes = R.string.recognition_title,
            iconRes = R.drawable.ic_document_scanner,
            keywords = listOf("движок", "lens", "ml kit", "распознавание", "ocr", "engines", "cloud", "облако"),
        ),
        SettingsIndexItem(
            page = SettingsPage.RECOGNITION,
            titleRes = R.string.bubble_small_text,
            summaryRes = R.string.bubble_small_text_hint,
            sectionRes = R.string.recognition_title,
            iconRes = R.drawable.ic_document_scanner,
            keywords = listOf("ускорение", "мелкий текст", "boost", "small text", "crops"),
        ),
        SettingsIndexItem(
            page = SettingsPage.RECOGNITION,
            titleRes = R.string.bubble_ocr_saving,
            summaryRes = R.string.bubble_ocr_saving_hint,
            sectionRes = R.string.recognition_title,
            iconRes = R.drawable.ic_document_scanner,
            keywords = listOf("экономия", "нагрузка", "saving", "cpu", "батарея", "delay"),
        ),
        SettingsIndexItem(
            page = SettingsPage.RECOGNITION,
            titleRes = R.string.bubble_read_app_text,
            summaryRes = R.string.bubble_read_app_text_hint,
            sectionRes = R.string.recognition_title,
            iconRes = R.drawable.ic_document_scanner,
            keywords = listOf("текст приложения", "accessibility", "app text", "спецвозможности"),
        ),
        SettingsIndexItem(
            page = SettingsPage.RECOGNITION,
            titleRes = R.string.bubble_app_text_only,
            summaryRes = R.string.bubble_app_text_only_hint,
            sectionRes = R.string.recognition_title,
            iconRes = R.drawable.ic_document_scanner,
            keywords = listOf("без скриншотов", "только текст", "app text only", "e-ink", "без сети"),
        ),

        // Lookup
        SettingsIndexItem(
            page = SettingsPage.LOOKUP,
            titleRes = R.string.lookup_scanning,
            sectionRes = R.string.lookup_title,
            iconRes = R.drawable.ic_manage_search,
            keywords = listOf("сканирование", "поиск", "длина", "max results", "результаты"),
        ),
        SettingsIndexItem(
            page = SettingsPage.LOOKUP,
            titleRes = R.string.lookup_merge_variants,
            summaryRes = R.string.lookup_merge_variants_hint,
            sectionRes = R.string.lookup_title,
            iconRes = R.drawable.ic_manage_search,
            keywords = listOf("объединение", "варианты", "кандзи", "merge", "variants"),
        ),
        SettingsIndexItem(
            page = SettingsPage.LOOKUP,
            titleRes = R.string.lookup_romaji,
            summaryRes = R.string.lookup_romaji_hint,
            sectionRes = R.string.lookup_title,
            iconRes = R.drawable.ic_manage_search,
            keywords = listOf("ромаджи", "romaji", "латиница", "конвертация"),
        ),
        SettingsIndexItem(
            page = SettingsPage.LOOKUP,
            titleRes = R.string.lookup_text_replacements,
            summaryRes = R.string.lookup_text_replacements_hint,
            sectionRes = R.string.lookup_title,
            iconRes = R.drawable.ic_manage_search,
            keywords = listOf("замены", "текст", "replacements", "правила"),
        ),

        // Dictionaries
        SettingsIndexItem(
            page = SettingsPage.DICTIONARIES,
            titleRes = R.string.dictionaries_title,
            summaryRes = R.string.dictionaries_order_hint,
            sectionRes = R.string.home_dictionaries_title,
            iconRes = R.drawable.ic_library_books,
            keywords = listOf("словари", "yomitan", "каталог", "порядок", "приоритет", "import", "импорт"),
        ),
        SettingsIndexItem(
            page = SettingsPage.DICTIONARIES,
            titleRes = R.string.dictionaries_import_file,
            sectionRes = R.string.home_dictionaries_title,
            iconRes = R.drawable.ic_library_books,
            keywords = listOf("файл", "архив", "zip", "импорт словаря", "import"),
        ),
        SettingsIndexItem(
            page = SettingsPage.DICTIONARIES,
            titleRes = R.string.dictionaries_catalog,
            sectionRes = R.string.home_dictionaries_title,
            iconRes = R.drawable.ic_library_books,
            keywords = listOf("загрузка", "скачать", "каталог", "jmdict", "колобок", "wiktionary"),
        ),
        SettingsIndexItem(
            page = SettingsPage.DICTIONARIES,
            titleRes = R.string.dictionaries_sort_harmonic,
            summaryRes = R.string.dictionaries_sort_aggregate_hint,
            sectionRes = R.string.home_dictionaries_title,
            iconRes = R.drawable.ic_library_books,
            keywords = listOf("гармоническая", "среднее", "частотность", "сортировка", "harmonic", "frequency"),
        ),

        // Anki
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_deck,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("колода", "deck", "карточки", "anki", "ankidroid"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_model,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("тип записи", "модель", "note type", "model"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_fields,
            summaryRes = R.string.anki_fields_hint,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("поля", "шаблоны", "маркеры", "fields", "expression", "reading", "glossary"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_tags,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("теги", "метки", "tags"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_screenshot_by_default,
            summaryRes = R.string.anki_screenshot_by_default_hint,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("скриншот", "снимок", "screenshot", "картинка", "обрезка"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_remember_crop_area,
            summaryRes = R.string.anki_remember_crop_area_hint,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("запоминать область", "рамка", "crop", "area", "размер скриншота"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ANKI,
            titleRes = R.string.anki_duplicate_check,
            sectionRes = R.string.anki_title,
            iconRes = R.drawable.ic_style,
            keywords = listOf("дубликаты", "проверка дубликатов", "duplicates", "overlap"),
        ),

        // Audio
        SettingsIndexItem(
            page = SettingsPage.AUDIO,
            titleRes = R.string.audio_playback,
            sectionRes = R.string.audio_title,
            iconRes = R.drawable.ic_volume_up,
            keywords = listOf("воспроизведение", "звук", "озвучка", "playback", "audio"),
        ),
        SettingsIndexItem(
            page = SettingsPage.AUDIO,
            titleRes = R.string.audio_auto_play,
            sectionRes = R.string.audio_title,
            iconRes = R.drawable.ic_volume_up,
            keywords = listOf("автовоспроизведение", "авто", "autoplay"),
        ),
        SettingsIndexItem(
            page = SettingsPage.AUDIO,
            titleRes = R.string.audio_volume,
            sectionRes = R.string.audio_title,
            iconRes = R.drawable.ic_volume_up,
            keywords = listOf("громкость", "volume", "sound"),
        ),
        SettingsIndexItem(
            page = SettingsPage.AUDIO,
            titleRes = R.string.audio_sources,
            summaryRes = R.string.audio_sources_hint,
            sectionRes = R.string.audio_title,
            iconRes = R.drawable.ic_volume_up,
            keywords = listOf("источники аудио", "jpod", "forvo", "server", "tts", "произношение"),
        ),
        SettingsIndexItem(
            page = SettingsPage.AUDIO,
            titleRes = R.string.audio_regions,
            summaryRes = R.string.audio_regions_hint,
            sectionRes = R.string.audio_title,
            iconRes = R.drawable.ic_volume_up,
            keywords = listOf("диалекты", "регионы", "акценты", "regions", "accents"),
        ),

        // Popup
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_tap_lookup,
            summaryRes = R.string.popup_tap_lookup_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("тап", "определения", "кандзи", "вложенный поиск", "tap lookup", "definitions"),
        ),
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_hide_outside_tap,
            summaryRes = R.string.popup_hide_outside_tap_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("закрывать снаружи", "тап вне окна", "outside tap", "dismiss"),
        ),
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_hide_after_add,
            summaryRes = R.string.popup_hide_after_add_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("скрывать после добавления", "anki add", "hide after add"),
        ),
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_hide_off_word,
            summaryRes = R.string.popup_hide_off_word_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("скрывать при уходе", "off word"),
        ),
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_show_source,
            summaryRes = R.string.popup_show_source_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("исходный текст", "source text", "распознанная строка"),
        ),
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_copy_switch,
            summaryRes = R.string.popup_copy_switch_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("копирование", "copy", "буфер обмена", "html"),
        ),
        SettingsIndexItem(
            page = SettingsPage.POPUP,
            titleRes = R.string.popup_font_title,
            summaryRes = R.string.popup_font_hint,
            sectionRes = R.string.popup_settings_title,
            iconRes = R.drawable.ic_web_asset,
            keywords = listOf("шрифт", "масштаб", "тема попапа", "размер текста", "font", "scale", "css"),
        ),

        // Translation
        SettingsIndexItem(
            page = SettingsPage.TRANSLATION,
            titleRes = R.string.translation_button,
            summaryRes = R.string.translation_button_hint,
            sectionRes = R.string.translation_title,
            iconRes = R.drawable.ic_translate,
            keywords = listOf("кнопка перевода", "переводчик", "translate button"),
        ),
        SettingsIndexItem(
            page = SettingsPage.TRANSLATION,
            titleRes = R.string.translation_anki,
            summaryRes = R.string.translation_anki_hint,
            sectionRes = R.string.translation_title,
            iconRes = R.drawable.ic_translate,
            keywords = listOf("перевод в anki", "sentence translation", "карточки"),
        ),
        SettingsIndexItem(
            page = SettingsPage.TRANSLATION,
            titleRes = R.string.translation_services_title,
            summaryRes = R.string.translation_services_hint,
            sectionRes = R.string.translation_title,
            iconRes = R.drawable.ic_translate,
            keywords = listOf("сервисы", "bing", "google", "edge", "каскад", "приоритет"),
        ),
        SettingsIndexItem(
            page = SettingsPage.TRANSLATION,
            titleRes = R.string.translation_language_title,
            sectionRes = R.string.translation_title,
            iconRes = R.drawable.ic_translate,
            keywords = listOf("язык перевода", "целевой язык", "target language"),
        ),
        SettingsIndexItem(
            page = SettingsPage.TRANSLATION,
            titleRes = R.string.translation_test_title,
            sectionRes = R.string.translation_title,
            iconRes = R.drawable.ic_translate,
            keywords = listOf("тест перевода", "проверка скорости", "тест"),
        ),

        // Camera
        SettingsIndexItem(
            page = SettingsPage.CAMERA,
            titleRes = R.string.camera_title,
            summaryRes = R.string.settings_camera_summary,
            sectionRes = R.string.camera_title,
            iconRes = R.drawable.ic_photo_camera,
            keywords = listOf("камера", "фото", "снимки", "поворот", "галерея", "photo", "camera"),
        ),

        // Appearance
        SettingsIndexItem(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.settings_theme_title,
            sectionRes = R.string.appearance_title,
            iconRes = R.drawable.ic_palette,
            keywords = listOf("тема", "светлая", "темная", "системная", "theme", "dark", "light"),
        ),
        SettingsIndexItem(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.settings_colors_title,
            summaryRes = R.string.settings_colors_hint,
            sectionRes = R.string.appearance_title,
            iconRes = R.drawable.ic_palette,
            keywords = listOf("палитра", "цвета", "акцент", "palette", "custom color"),
        ),
        SettingsIndexItem(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.eink_title,
            summaryRes = R.string.eink_hint,
            sectionRes = R.string.appearance_title,
            iconRes = R.drawable.ic_palette,
            keywords = listOf("e-ink", "электронная бумага", "черно-белый", "контраст"),
        ),
        SettingsIndexItem(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.settings_language,
            sectionRes = R.string.appearance_title,
            iconRes = R.drawable.ic_palette,
            keywords = listOf("язык интерфейса", "интерфейс", "app language", "русский", "english"),
        ),

        // Background
        SettingsIndexItem(
            page = SettingsPage.BACKGROUND,
            titleRes = R.string.background_battery_title,
            sectionRes = R.string.background_title,
            iconRes = R.drawable.ic_battery,
            keywords = listOf("батарея", "оптимизация", "аккумулятор", "выгрузка", "battery", "dontkillmyapp"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BACKGROUND,
            titleRes = R.string.background_launch_title,
            summaryRes = R.string.background_launch_text,
            sectionRes = R.string.background_title,
            iconRes = R.drawable.ic_battery,
            keywords = listOf("автозапуск", "фоновая работа", "косвенный запуск", "auto-launch", "background"),
        ),
        SettingsIndexItem(
            page = SettingsPage.BACKGROUND,
            titleRes = R.string.background_allowlist_title,
            summaryRes = R.string.background_allowlist_text,
            sectionRes = R.string.background_title,
            iconRes = R.drawable.ic_battery,
            keywords = listOf("замочек", "недавние", "recents", "lock", "force-stop", "принудительная остановка"),
        ),

        // Backup
        SettingsIndexItem(
            page = SettingsPage.BACKUP,
            titleRes = R.string.backup_title,
            summaryRes = R.string.backup_create_hint,
            sectionRes = R.string.backup_title,
            iconRes = R.drawable.ic_backup,
            keywords = listOf("резервная копия", "бэкап", "экспорт", "импорт", "backup", "export", "restore"),
        ),

        // About
        SettingsIndexItem(
            page = SettingsPage.ABOUT,
            titleRes = R.string.about_title,
            sectionRes = R.string.about_title,
            iconRes = R.drawable.ic_info,
            keywords = listOf("о приложении", "версия", "about", "version"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ABOUT,
            titleRes = R.string.about_logs,
            summaryRes = R.string.about_logs_hint,
            sectionRes = R.string.about_title,
            iconRes = R.drawable.ic_info,
            keywords = listOf("логи", "журнал", "ошибки", "экспорт логов", "logs"),
        ),
        SettingsIndexItem(
            page = SettingsPage.ABOUT,
            titleRes = R.string.about_libraries,
            sectionRes = R.string.about_title,
            iconRes = R.drawable.ic_info,
            keywords = listOf("лицензии", "библиотеки", "благодарности", "libraries", "notices", "licenses"),
        ),
    )

    /**
     * Filters indexed items against [query].
     * Query is split into tokens; an item matches if every query token appears in its title, summary,
     * section title, or extra keywords.
     */
    fun search(query: String, resolveString: (Int) -> String): List<SettingsIndexItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val tokens = trimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return items.filter { item ->
            val title = resolveString(item.titleRes).lowercase()
            val summary = item.summaryRes?.let { resolveString(it).lowercase() } ?: ""
            val section = resolveString(item.sectionRes).lowercase()
            val keywords = item.keywords.map { it.lowercase() }

            tokens.all { token ->
                title.contains(token) ||
                    summary.contains(token) ||
                    section.contains(token) ||
                    keywords.any { it.contains(token) }
            }
        }
    }

    fun search(query: String, context: Context): List<SettingsIndexItem> =
        search(query) { context.getString(it) }
}
