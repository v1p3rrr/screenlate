package com.vpr.screenlate.core.anki.note

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class SentenceTest {
    private fun extract(paragraph: String, word: String, language: Language = Language.JAPANESE): Sentence =
        Sentence.extract(paragraph, paragraph.indexOf(word), word.length, language)

    @Test
    fun cutsAtTerminators() {
        val sentence = extract("昨日は雨だった。今日は晴れている！明日は？", "晴れ")
        assertThat(sentence).isEqualTo(Sentence("今日は", "晴れ", "ている！"))
    }

    @Test
    fun keepsQuotedSpeechTogether() {
        val sentence = extract("「行く。」と彼は言った。次の文。", "言っ")
        assertThat(sentence.text).isEqualTo("「行く。」と彼は言った。")
        assertThat(sentence.body).isEqualTo("言っ")
    }

    @Test
    fun keepsClosingQuoteAfterTerminator() {
        val sentence = extract("彼は「もう帰る。」", "帰る")
        assertThat(sentence.text).isEqualTo("彼は「もう帰る。」")
    }

    @Test
    fun wholeParagraphWithoutTerminators() {
        val sentence = extract("ありがとうございます 桃香さんもね", "桃香")
        assertThat(sentence.prefix).isEqualTo("ありがとうございます ")
        assertThat(sentence.suffix).isEqualTo("さんもね")
    }

    @Test
    fun abbreviationsAndDecimalsDoNotEndAnEnglishSentence() {
        val paragraph = "She gave up. Mr. Smith met her at 5 p.m. at gate 3.5 near example.com. They went home."
        val sentence = extract(paragraph, "gate", Language.ENGLISH)
        assertThat(sentence.text).isEqualTo("Mr. Smith met her at 5 p.m. at gate 3.5 near example.com.")
    }

    @Test
    fun englishSentenceEndsAtOtherTerminatorsAndQuotes() {
        val sentence = extract("Really? “Go home.” he said. Fine!", "said", Language.ENGLISH)
        assertThat(sentence.text).isEqualTo("“Go home.” he said.")
        assertThat(extract("It was late... Then it rained.", "rained", Language.ENGLISH).text).isEqualTo("Then it rained.")
    }

    @Test
    fun spanishSentenceStartsAtInvertedMarksAndKeepsAngleQuotes() {
        val paragraph = "Llegó tarde. ¿Dónde está el Sr. García? No sé. «Ven aquí.» dijo ella."
        assertThat(extract(paragraph, "García", Language.SPANISH).text).isEqualTo("¿Dónde está el Sr. García?")
        assertThat(extract(paragraph, "dijo", Language.SPANISH).text).isEqualTo("«Ven aquí.» dijo ella.")
    }

    @Test
    fun frenchSentenceKeepsSpacedGuillemetsAndMonsieur() {
        val paragraph = "Il pleut. M. Dupont arrive. « Restez ici. » dit-il."
        assertThat(extract(paragraph, "Dupont", Language.FRENCH).text).isEqualTo("M. Dupont arrive.")
        assertThat(extract(paragraph, "dit", Language.FRENCH).text).isEqualTo("« Restez ici. » dit-il.")
    }

    @Test
    fun germanSentenceKeepsOrdinalsAndLowQuotes() {
        val paragraph = "Es regnet. Am 3. Oktober kommt er, z. B. mit dem Zug. „Bleib hier.“ sagte sie."
        assertThat(extract(paragraph, "Oktober", Language.GERMAN).text)
            .isEqualTo("Am 3. Oktober kommt er, z. B. mit dem Zug.")
        assertThat(extract(paragraph, "sagte", Language.GERMAN).text).isEqualTo("„Bleib hier.“ sagte sie.")
    }

    @Test
    fun italianSentenceKeepsAngleQuotesAndSignore() {
        val paragraph = "Piove. Il sig. Rossi arriva. «Resta qui.» disse lei."
        assertThat(extract(paragraph, "Rossi", Language.ITALIAN).text).isEqualTo("Il sig. Rossi arriva.")
        assertThat(extract(paragraph, "disse", Language.ITALIAN).text).isEqualTo("«Resta qui.» disse lei.")
    }

    @Test
    fun portugueseSentenceKeepsAngleQuotesAndSenhora() {
        val paragraph = "Chove. A Sra. Silva chegou. «Fica aqui.» disse ela."
        assertThat(extract(paragraph, "Silva", Language.PORTUGUESE).text).isEqualTo("A Sra. Silva chegou.")
        assertThat(extract(paragraph, "disse", Language.PORTUGUESE).text).isEqualTo("«Fica aqui.» disse ela.")
    }

    @Test
    fun polishSentenceKeepsLowQuotesOrdinalsAndAbbreviations() {
        val paragraph = "Pada. W XX w. powstał np. ten dom. „Zostań tu.” powiedziała. To 5. piętro."
        assertThat(extract(paragraph, "powstał", Language.POLISH).text).isEqualTo("W XX w. powstał np. ten dom.")
        assertThat(extract(paragraph, "powiedziała", Language.POLISH).text).isEqualTo("„Zostań tu.” powiedziała.")
        assertThat(extract(paragraph, "piętro", Language.POLISH).text).isEqualTo("To 5. piętro.")
    }

    @Test
    fun turkishSentenceKeepsOrdinalsAndAbbreviations() {
        val paragraph = "Yağmur yağdı. 15. yüzyılda Dr. Ayşe yaşadı. Sonra gitti."
        assertThat(extract(paragraph, "Ayşe", Language.TURKISH).text).isEqualTo("15. yüzyılda Dr. Ayşe yaşadı.")
    }

    @Test
    fun latvianSentenceKeepsYearsLowQuotesAndAbbreviations() {
        val paragraph = "Lija. 1918. gada 18. novembrī dibināja valsti. „Nāc šurp.“ viņa teica. Āboli u.c. augļi auga."
        assertThat(extract(paragraph, "dibināja", Language.LATVIAN).text)
            .isEqualTo("1918. gada 18. novembrī dibināja valsti.")
        assertThat(extract(paragraph, "teica", Language.LATVIAN).text).isEqualTo("„Nāc šurp.“ viņa teica.")
        assertThat(extract(paragraph, "augļi", Language.LATVIAN).text).isEqualTo("Āboli u.c. augļi auga.")
    }

    @Test
    fun dutchSentenceKeepsAbbreviationsAndLowQuotes() {
        val paragraph = "Het regende. Fruit, bijv. appels, d.w.z. goed fruit. „Kom hier.” zei ze. Ik zag Jan. Hij liep."
        assertThat(extract(paragraph, "appels", Language.DUTCH).text).isEqualTo("Fruit, bijv. appels, d.w.z. goed fruit.")
        assertThat(extract(paragraph, "zei", Language.DUTCH).text).isEqualTo("„Kom hier.” zei ze.")
        assertThat(extract(paragraph, "zag", Language.DUTCH).text).isEqualTo("Ik zag Jan.")
    }

    @Test
    fun swedishSentenceKeepsAbbreviationsAndTheQuotesItOpens() {
        val paragraph = "Det regnade. Hon köpte t.ex. äpplen, bl.a. röda. ”Kom hit nu.” Vi ses kl. 8 i morgon. Hon är ung. Slut."
        assertThat(extract(paragraph, "äpplen", Language.SWEDISH).text).isEqualTo("Hon köpte t.ex. äpplen, bl.a. röda.")
        assertThat(extract(paragraph, "hit", Language.SWEDISH).text).isEqualTo("”Kom hit nu.”")
        assertThat(extract(paragraph, "ses", Language.SWEDISH).text).isEqualTo("Vi ses kl. 8 i morgon.")
        assertThat(extract(paragraph, "ung", Language.SWEDISH).text).isEqualTo("Hon är ung.")
    }

    @Test
    fun norwegianSentenceKeepsAbbreviationsOrdinalsAndTheGuillemetsItOpens() {
        val paragraph = "Det regnet. Hun kjøpte f.eks. epler, bl.a. røde. «Kom hit nå!» ropte hun. Vi ses den 17. mai kl. 8 i morgen. Slutt."
        assertThat(extract(paragraph, "epler", Language.NORWEGIAN).text).isEqualTo("Hun kjøpte f.eks. epler, bl.a. røde.")
        assertThat(extract(paragraph, "ropte", Language.NORWEGIAN).text).isEqualTo("«Kom hit nå!» ropte hun.")
        assertThat(extract(paragraph, "ses", Language.NORWEGIAN).text).isEqualTo("Vi ses den 17. mai kl. 8 i morgen.")
    }

    @Test
    fun danishSentenceKeepsAbbreviationsOrdinalsAndTheGuillemetsItOpensWithTheRightPointingOne() {
        val paragraph = "Det regnede. Hun købte f.eks. æbler, bl.a. røde. »Kom her!« råbte hun. Vi ses den 5. maj kl. 8 i morgen. Slut."
        assertThat(extract(paragraph, "æbler", Language.DANISH).text).isEqualTo("Hun købte f.eks. æbler, bl.a. røde.")
        assertThat(extract(paragraph, "råbte", Language.DANISH).text).isEqualTo("»Kom her!« råbte hun.")
        assertThat(extract(paragraph, "ses", Language.DANISH).text).isEqualTo("Vi ses den 5. maj kl. 8 i morgen.")
    }

    @Test
    fun czechSentenceKeepsOrdinalsEraAndAbbreviations() {
        val paragraph = "Pršelo. Dne 5. května 300 př. n. l. vznikl např. tento dům. „Pojď sem.“ řekla. Konec."
        assertThat(extract(paragraph, "vznikl", Language.CZECH).text)
            .isEqualTo("Dne 5. května 300 př. n. l. vznikl např. tento dům.")
        assertThat(extract(paragraph, "řekla", Language.CZECH).text).isEqualTo("„Pojď sem.“ řekla.")
    }

    @Test
    fun indonesianAndMalaySentencesKeepTheirAbbreviations() {
        val indonesian = "Hujan turun. Dia tinggal di Jl. Merdeka No. 5 sejak lama. Selesai."
        assertThat(extract(indonesian, "tinggal", Language.INDONESIAN).text)
            .isEqualTo("Dia tinggal di Jl. Merdeka No. 5 sejak lama.")
        val malay = "Hujan turun. Saya jumpa En. Ali di Maju Sdn. Bhd. semalam. Tamat."
        assertThat(extract(malay, "jumpa", Language.MALAY).text).isEqualTo("Saya jumpa En. Ali di Maju Sdn. Bhd. semalam.")
    }

    @Test
    fun filipinoSentenceKeepsTitlesAndSaints() {
        val paragraph = "Umulan kahapon. Nakita ko si G. Santos sa Sta. Cruz kanina. Tapos na."
        assertThat(extract(paragraph, "Nakita", Language.FILIPINO).text).isEqualTo("Nakita ko si G. Santos sa Sta. Cruz kanina.")
    }

    @Test
    fun vietnameseSentenceKeepsCityAbbreviation() {
        val paragraph = "Trời mưa. Tôi sống ở TP. Hồ Chí Minh. Hết."
        assertThat(extract(paragraph, "sống", Language.VIETNAMESE).text).isEqualTo("Tôi sống ở TP. Hồ Chí Minh.")
    }

    @Test
    fun russianSentenceKeepsQuotesAndAbbreviations() {
        val paragraph = "Шёл дождь. В 1999 г. он сказал: «Я приду. Обязательно». Потом ушёл."
        assertThat(extract(paragraph, "сказал", Language.RUSSIAN).text)
            .isEqualTo("В 1999 г. он сказал: «Я приду. Обязательно».")
    }

    @Test
    fun ukrainianAndBelarusianSentencesKeepQuotesAndAbbreviations() {
        val ukrainian = "Ішов дощ. У 1991 р. він побачив т. зв. „диво“ на вул. Шевченка. Кінець."
        assertThat(extract(ukrainian, "побачив", Language.UKRAINIAN).text)
            .isEqualTo("У 1991 р. він побачив т. зв. „диво“ на вул. Шевченка.")
        val belarusian = "Ішоў дождж. У 1991 г. ён сказаў: «Я прыйду. Абавязкова». Потым пайшоў."
        assertThat(extract(belarusian, "сказаў", Language.BELARUSIAN).text)
            .isEqualTo("У 1991 г. ён сказаў: «Я прыйду. Абавязкова».")
    }

    @Test
    fun greekSentencesEndAtTheQuestionMarkAndKeepQuotesAndAbbreviations() {
        val greek = "Πού πας; Ο κ. Νίκος είπε· «Έρχομαι. Περίμενε», π.χ. αύριο. Τέλος."
        assertThat(extract(greek, "είπε", Language.GREEK).text)
            .isEqualTo("Ο κ. Νίκος είπε· «Έρχομαι. Περίμενε», π.χ. αύριο.")
        assertThat(extract(greek, "πας", Language.GREEK).text).isEqualTo("Πού πας;")
    }

    @Test
    fun hindiSentencesEndAtTheDandaAndKeepQuotesAbbreviationsAndInitials() {
        val hindi = "वह घर गया। डॉ. शर्मा ने कहा, “कल आना। ज़रूर।” फिर वे चले गए। अंत।"
        assertThat(extract(hindi, "शर्मा", Language.HINDI).text)
            .isEqualTo("डॉ. शर्मा ने कहा, “कल आना। ज़रूर।” फिर वे चले गए।")
        assertThat(extract(hindi, "गया", Language.HINDI).text).isEqualTo("वह घर गया।")
        val initials = "भारत के राष्ट्रपति ए.पी.जे. अब्दुल कलाम थे। वे वैज्ञानिक थे।"
        assertThat(extract(initials, "कलाम", Language.HINDI).text).isEqualTo("भारत के राष्ट्रपति ए.पी.जे. अब्दुल कलाम थे।")
    }

    @Test
    fun bengaliSentencesEndAtTheDandaAndKeepQuotesAbbreviationsAndInitials() {
        val bengali = "সে বাড়ি গেল। ড. ইউনূস বললেন, “কাল এসো। অবশ্যই।” তারপর তাঁরা চলে গেলেন। শেষ।"
        assertThat(extract(bengali, "ইউনূস", Language.BENGALI).text)
            .isEqualTo("ড. ইউনূস বললেন, “কাল এসো। অবশ্যই।” তারপর তাঁরা চলে গেলেন।")
        assertThat(extract(bengali, "গেল", Language.BENGALI).text).isEqualTo("সে বাড়ি গেল।")
        val initials = "বাংলার প্রধানমন্ত্রী ছিলেন এ.কে. ফজলুল হক। তিনি আইনজীবী ছিলেন।"
        assertThat(extract(initials, "ফজলুল", Language.BENGALI).text).isEqualTo("বাংলার প্রধানমন্ত্রী ছিলেন এ.কে. ফজলুল হক।")
    }

    @Test
    fun georgianSentencesKeepTheirAbbreviationsAndLowHighQuotes() {
        val georgian = "ის ქ. თბილისში 1991 წ. დაიბადა. მან თქვა: „ხვალ მოვალ. აუცილებლად.“ შემდეგ წავიდა. დასასრული."
        assertThat(extract(georgian, "თბილისში", Language.GEORGIAN).text).isEqualTo("ის ქ. თბილისში 1991 წ. დაიბადა.")
        assertThat(extract(georgian, "თქვა", Language.GEORGIAN).text)
            .isEqualTo("მან თქვა: „ხვალ მოვალ. აუცილებლად.“ შემდეგ წავიდა.")
    }

    @Test
    fun armenianSentencesEndAtTheFullStopAndTheColonNotAtThePeriod() {
        val armenian = "Նա եկավ։ Նա ասաց. «Վաղը կգամ։ Անպայման։» Հետո գնաց։ Ժամը 10:30-ին: Վերջ։"
        assertThat(extract(armenian, "ասաց", Language.ARMENIAN).text).isEqualTo("Նա ասաց. «Վաղը կգամ։ Անպայման։» Հետո գնաց։")
        assertThat(extract(armenian, "Ժամը", Language.ARMENIAN).text).isEqualTo("Ժամը 10:30-ին:")
    }

    @Test
    fun thaiSentencesEndAtASpaceBetweenLettersButNotBesideMaiYamokPaiyannoiOrNumbers() {
        val thai = "วันนี้อากาศดีมาก ไปเดินเล่นกันไหม เด็ก ๆ ชอบเล่น กรุงเทพฯ เป็นเมืองหลวง ปี 2024 มีฝนมาก"
        assertThat(extract(thai, "เดิน", Language.THAI).text).isEqualTo("ไปเดินเล่นกันไหม")
        assertThat(extract(thai, "ชอบ", Language.THAI).text).isEqualTo("เด็ก ๆ ชอบเล่น")
        assertThat(extract(thai, "เมือง", Language.THAI).text).isEqualTo("กรุงเทพฯ เป็นเมืองหลวง")
        assertThat(extract(thai, "ฝน", Language.THAI).text).isEqualTo("ปี 2024 มีฝนมาก")
        // A full stop after Thai letters is an abbreviation's (พ.ศ., the Buddhist era); after Latin letters it ends one.
        assertThat(extract("พ.ศ. 2567 เขาไปเชียงใหม่", "เขา", Language.THAI).text).isEqualTo("พ.ศ. 2567 เขาไปเชียงใหม่")
        assertThat(extract("เขาใช้ iOS. เราไปกัน", "เรา", Language.THAI).text).isEqualTo("เราไปกัน")
    }

    @Test
    fun khmerSentencesEndAtKhanAndTheSectionEndNotAtASpace() {
        val khmer = "ខ្ញុំទៅ សាលារៀន។ គាត់ធ្វើការនៅភ្នំពេញ។ តើអ្នកសុខសប្បាយទេ? ក្មេងៗលេងបាល់៕ ចប់"
        assertThat(extract(khmer, "សាលា", Language.KHMER).text).isEqualTo("ខ្ញុំទៅ សាលារៀន។")
        assertThat(extract(khmer, "ធ្វើការ", Language.KHMER).text).isEqualTo("គាត់ធ្វើការនៅភ្នំពេញ។")
        assertThat(extract(khmer, "សុខសប្បាយ", Language.KHMER).text).isEqualTo("តើអ្នកសុខសប្បាយទេ?")
        assertThat(extract(khmer, "បាល់", Language.KHMER).text).isEqualTo("ក្មេងៗលេងបាល់៕")
    }

    @Test
    fun arabicSentencesEndAtTheArabicQuestionMarkNotAtItsCommaOrAnAbbreviation() {
        val arabic = "هل قرأت الكتاب؟ نعم، قرأته في المكتبة؛ كان جميلا. قال د. أحمد «اقرأ!» ثم خرج."
        assertThat(extract(arabic, "قرأت", Language.ARABIC).text).isEqualTo("هل قرأت الكتاب؟")
        assertThat(extract(arabic, "المكتبة", Language.ARABIC).text).isEqualTo("نعم، قرأته في المكتبة؛ كان جميلا.")
        assertThat(extract(arabic, "أحمد", Language.ARABIC).text).isEqualTo("قال د. أحمد «اقرأ!» ثم خرج.")
        assertThat(extract("وُلد سنة ٥٠ ق.م. في روما", "روما", Language.ARABIC).text)
            .isEqualTo("وُلد سنة ٥٠ ق.م. في روما")
    }

    @Test
    fun persianSentencesEndAtTheArabicQuestionMarkNotAtItsCommaOrAPageNumber() {
        val persian = "کتاب\u200Cها را خواندی؟ بله، در کتابخانه خواندم؛ خیلی خوب بود. نگاه کن ص. ۱۲ «عالی!» گفت."
        assertThat(extract(persian, "خواندی", Language.PERSIAN).text).isEqualTo("کتاب\u200Cها را خواندی؟")
        assertThat(extract(persian, "کتابخانه", Language.PERSIAN).text).isEqualTo("بله، در کتابخانه خواندم؛ خیلی خوب بود.")
        assertThat(extract(persian, "نگاه", Language.PERSIAN).text).isEqualTo("نگاه کن ص. ۱۲ «عالی!» گفت.")
    }

    @Test
    fun hebrewSentencesKeepTheMaqafTheGershayimAndAQuotedExclamation() {
        val hebrew = "הלכתי לבית־הספר. מה שלומך? הוא אמר: „שלום!” וצה״ל הגיע."
        assertThat(extract(hebrew, "הספר", Language.HEBREW).text).isEqualTo("הלכתי לבית־הספר.")
        assertThat(extract(hebrew, "שלומך", Language.HEBREW).text).isEqualTo("מה שלומך?")
        assertThat(extract(hebrew, "וצה״ל", Language.HEBREW).text).isEqualTo("הוא אמר: „שלום!” וצה״ל הגיע.")
    }

    @Test
    fun kazakhSentencesKeepTheYearAndOtherAbbreviationsBeforeLowercaseWords() {
        val kazakh = "Күн жылы. Ол 2007 ж. қазанда кітап, дәптер т.б. заттар алды. Туған жылы – 1991 ж. Кейін көшті. Соңы."
        assertThat(extract(kazakh, "қазанда", Language.KAZAKH).text)
            .isEqualTo("Ол 2007 ж. қазанда кітап, дәптер т.б. заттар алды.")
        assertThat(extract(kazakh, "Кейін", Language.KAZAKH).text).isEqualTo("Кейін көшті.")
        val quoted = "Ол: «Келемін. Міндетті түрде», – деді. Кетті."
        assertThat(extract(quoted, "деді", Language.KAZAKH).text).isEqualTo("Ол: «Келемін. Міндетті түрде», – деді.")
    }

    @Test
    fun kyrgyzSentencesKeepTheYearAndTheSpacedAbbreviationsBeforeLowercaseWords() {
        val kyrgyz = "Күн жылуу. Ал 1991-ж. жайында алма, өрүк ж. б. жемиштерди сатып алды. Проф. Асанов келди. Кийин кетти."
        assertThat(extract(kyrgyz, "жайында", Language.KYRGYZ).text)
            .isEqualTo("Ал 1991-ж. жайында алма, өрүк ж. б. жемиштерди сатып алды.")
        assertThat(extract(kyrgyz, "Асанов", Language.KYRGYZ).text).isEqualTo("Проф. Асанов келди.")
        assertThat(extract(kyrgyz, "Кийин", Language.KYRGYZ).text).isEqualTo("Кийин кетти.")
    }

    @Test
    fun aQuoteTheParagraphDoesNotCloseKeepsTheSentenceShort() {
        // German pairs „ with “; an English-style “ja” leaves a “ without its opening quote.
        val german = "Das ist gut. Er sagte “ja” und ging. Dann kam sie."
        assertThat(extract(german, "ging", Language.GERMAN).text).isEqualTo("Er sagte “ja” und ging.")
        // A list item's bracket has no opening one.
        val list = "Steps: 1) open it. 2) close it. Done."
        assertThat(extract(list, "close", Language.ENGLISH).text).isEqualTo("2) close it.")
        // Polish pairs „ with ”; a German-style „Idę.“ leaves an opening quote open after the word.
        val polish = "Powiedział „Idę.“ Potem wyszedł. Koniec."
        assertThat(extract(polish, "Powiedział", Language.POLISH).text).isEqualTo("Powiedział „Idę.“")
        // A quote that closes still holds its terminators.
        assertThat(extract("1) Er sagte: „Komm. Jetzt.“ und ging. Fertig.", "sagte", Language.GERMAN).text)
            .isEqualTo("1) Er sagte: „Komm. Jetzt.“ und ging.")
        assertThat(extract("「行く。」と彼は言った。", "言っ").text).isEqualTo("「行く。」と彼は言った。")
    }

    @Test
    fun chineseSentenceKeepsQuotedSpeechDecimalsAndLatinPeriods() {
        val speech = "他说：“我走了。”然后离开了。明天见！"
        assertThat(extract(speech, "离开", Language.CHINESE).text).isEqualTo("他说：“我走了。”然后离开了。")
        assertThat(extract(speech, "明天", Language.CHINESE).text).isEqualTo("明天见！")
        assertThat(extract("圆周率是３．１４。下一句。", "圆周率", Language.CHINESE).text).isEqualTo("圆周率是３．１４。")
        // A Latin period is no Chinese terminator.
        assertThat(extract("我在3.5节看到了。下一句。", "看到", Language.CHINESE).text).isEqualTo("我在3.5节看到了。")
        assertThat(extract("你好吗？我很好。", "很好", Language.CHINESE).text).isEqualTo("我很好。")
    }

    @Test
    fun koreanSentenceEndsAtSpacedPeriodsButKeepsDecimalsAndQuotes() {
        val paragraph = "나는 학교에 갔다. 그리고 공부했다! 내일은?"
        assertThat(extract(paragraph, "공부", Language.KOREAN)).isEqualTo(Sentence("그리고 ", "공부", "했다!"))
        assertThat(extract("원주율은 3.14이다. 다음 문장.", "원주율", Language.KOREAN).text).isEqualTo("원주율은 3.14이다.")
        assertThat(extract("그가 “집에 가자.”라고 말했다. 다음 문장.", "말했", Language.KOREAN).text)
            .isEqualTo("그가 “집에 가자.”라고 말했다.")
    }

    @Test
    fun aClosingQuoteOfAnotherLanguageStaysWithItsSentence() {
        // English quotes in German text: ” closes in every language here, so it ends the sentence it follows.
        val german = "Er sagte “Komm.” Dann ging er. Fertig."
        assertThat(extract(german, "sagte", Language.GERMAN).text).isEqualTo("Er sagte “Komm.”")
        assertThat(extract(german, "Dann", Language.GERMAN).text).isEqualTo("Dann ging er.")
        // An apostrophe that starts a word at the paragraph start stays.
        assertThat(extract("’Tis late. Go.", "late", Language.ENGLISH).text).isEqualTo("’Tis late.")
        // Polish pairs „ with ”: the quote holds its terminator, as before.
        assertThat(extract("Powiedział „Idę.” Potem wyszedł.", "Potem", Language.POLISH).text)
            .isEqualTo("Powiedział „Idę.” Potem wyszedł.")
    }
}
