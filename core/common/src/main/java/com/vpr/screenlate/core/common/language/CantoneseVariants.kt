package com.vpr.screenlate.core.common.language

/**
 * Characters Hong Kong, Taiwan and OpenCC's standard text write differently for one word (衞 衛, 綫 線, 裏 裡, 温 溫):
 * words.hk spells the Hong Kong forms, CC-CEDICT the Taiwan ones, rime-cantonese's word list OpenCC's. From OpenCC's
 * HKVariants and TWVariants (Apache-2.0), without the pairs that are different words in some uses (只 衹, 才 纔, 着 著,
 * 臺 台). The lookup page's `cantoneseVariants` processor (`scripts/yomitan-language/own-processors.js`) holds the same
 * groups.
 */
internal object CantoneseVariants {
    /** Groups separated by spaces, each the Hong Kong form, the Taiwan form, then the others. */
    const val GROUPS = "偽偽僞 兑兌 叁叄 啓啟 囱囪 妝妝粧 媪媼 嫺嫻 媯媯嬀 峯峰 悦悅 愠慍 户戶 捝挩 揾搵 敚敓 敍敘 枴柺 棁梲 榅榲 檐簷 氲氳 污汙 泄洩 涚涗 温溫 濕溼 溈溈潙 潀潀潨 煴熅 為為爲 牀床 痹痺 痴痴癡 皂皂皁 睾睪 秘祕 税稅 灶灶竈 粽粽糉糭 線線綫 緼縕 繮韁 羣群 唇唇脣 脱脫 腽膃 卧臥 蒀蒕 葱蔥 蒍蒍蔿 藴蘊 蜕蛻 眾眾衆 衞衛 裏裡 説說 輼轀 醖醞 缽缽鉢 鈎鉤 鋭銳 閲閱 鮎鯰 鰛鰮 麪麵 齶顎"

    private val groups: Map<Int, IntArray> by lazy {
        GROUPS.split(' ').flatMap { group ->
            val forms = group.codePoints().toArray()
            forms.distinct().map { it to forms }
        }.toMap()
    }

    /**
     * [text] with every character of [GROUPS] in its Hong Kong form, in its Taiwan form and in its further forms: the
     * spellings that differ from [text], none when it has no such character.
     */
    fun spellings(text: String): List<String> {
        val points = text.codePoints().toArray()
        val longest = points.maxOfOrNull { groups[it]?.size ?: 0 } ?: 0
        return (0 until longest).map { form ->
            buildString {
                for (point in points) appendCodePoint(groups[point]?.let { it[minOf(form, it.size - 1)] } ?: point)
            }
        }.distinct().filter { it != text }
    }
}
