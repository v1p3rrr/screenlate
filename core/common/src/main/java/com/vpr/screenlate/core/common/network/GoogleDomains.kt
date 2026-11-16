package com.vpr.screenlate.core.common.network

import com.vpr.screenlate.core.common.Language

/**
 * Regional Google search domains for image search queries across supported languages.
 */
object GoogleDomains {
    fun domain(language: Language): String = when (language) {
        Language.JAPANESE -> "www.google.co.jp"
        Language.RUSSIAN -> "www.google.ru"
        Language.ENGLISH -> "www.google.com"
        Language.GERMAN -> "www.google.de"
        Language.FRENCH -> "www.google.fr"
        Language.SPANISH -> "www.google.es"
        Language.ITALIAN -> "www.google.it"
        Language.PORTUGUESE -> "www.google.pt"
        Language.POLISH -> "www.google.pl"
        Language.TURKISH -> "www.google.com.tr"
        Language.DUTCH -> "www.google.nl"
        Language.CZECH -> "www.google.cz"
        Language.UKRAINIAN -> "www.google.com.ua"
        Language.BELARUSIAN -> "www.google.by"
        Language.KOREAN -> "www.google.co.kr"
        Language.GREEK -> "www.google.gr"
        Language.SWEDISH -> "www.google.se"
        Language.FINNISH -> "www.google.fi"
        Language.DANISH -> "www.google.dk"
        Language.NORWEGIAN -> "www.google.no"
        Language.HUNGARIAN -> "www.google.hu"
        Language.ROMANIAN -> "www.google.ro"
        Language.SLOVAK -> "www.google.sk"
        Language.SLOVENIAN -> "www.google.si"
        Language.BULGARIAN -> "www.google.bg"
        Language.CROATIAN -> "www.google.hr"
        Language.SERBIAN -> "www.google.rs"
        Language.LITHUANIAN -> "www.google.lt"
        Language.LATVIAN -> "www.google.lv"
        Language.ESTONIAN -> "www.google.ee"
        Language.VIETNAMESE -> "www.google.com.vn"
        Language.THAI -> "www.google.co.th"
        Language.INDONESIAN -> "www.google.co.id"
        Language.MALAY -> "www.google.com.my"
        Language.FILIPINO -> "www.google.com.ph"
        Language.HEBREW -> "www.google.co.il"
        Language.KAZAKH -> "www.google.kz"
        Language.KYRGYZ -> "www.google.kg"
        Language.UZBEK -> "www.google.co.uz"
        Language.AZERBAIJANI -> "www.google.az"
        Language.GEORGIAN -> "www.google.ge"
        Language.ARMENIAN -> "www.google.am"
        Language.ALBANIAN -> "www.google.al"
        Language.MACEDONIAN -> "www.google.mk"
        Language.MONGOLIAN -> "www.google.mn"
        Language.HINDI,
        Language.BENGALI,
        Language.MARATHI,
        Language.TAMIL,
        Language.TELUGU,
        Language.KANNADA -> "www.google.co.in"
        Language.CHINESE,
        Language.CANTONESE,
        Language.ARABIC,
        Language.PERSIAN,
        Language.SWAHILI,
        Language.WELSH,
        Language.IRISH,
        Language.SCOTTISH_GAELIC,
        Language.LATIN,
        Language.ESPERANTO,
        Language.KHMER -> "www.google.com"
    }
}
