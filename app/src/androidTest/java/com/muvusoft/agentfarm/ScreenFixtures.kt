package com.muvusoft.agentfarm

import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.conversation.Outgoing
import com.muvusoft.agentfarm.core.conversation.Role
import com.muvusoft.agentfarm.core.conversation.SendState
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.now.Recent
import com.muvusoft.agentfarm.core.now.Running
import com.muvusoft.agentfarm.core.now.Waiting
import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.AskOption
import com.muvusoft.agentfarm.core.session.AskQuestion
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.core.state.Link

/** The user's own kind of data (Turkish titles, real agents, a real spread of states) for the drawn screens. */
object ScreenFixtures {
    const val NOW = 1_760_600_000_000L
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    val online = Link.Online("192.168.1.20:7300", "manage", emptyMap(), NOW - 2 * HOUR)
    val offline = Link.Offline("unreachable", NOW - 12 * MIN)

    private val waiting = listOf(
        Waiting("c_psy", "psikolog", "Haftalık değerlendirme", Ask(
            AskKind.QUESTION, "", questions = listOf(AskQuestion("Bu haftanın notlarını şimdi özetleyeyim mi?", options = listOf(AskOption("Evet"), AskOption("Hayır")))),
            since = NOW - 2 * MIN,
        )),
        Waiting("c_ops", "developer", "and_AgentFarm CI düzeltmesi", Ask(AskKind.PERMISSION, "git push origin master", "Bash", since = NOW - 40_000)),
        Waiting("c_yaz", "yazar", "Bülten taslağı — Ekim sayısı", Ask(
            AskKind.QUESTION, "",
            questions = listOf(AskQuestion(
                "Başlık için hangisi daha iyi: kısa ve vurucu mu, yoksa açıklayıcı mı? Okur kitlesi teknik değil, telefonda okuyor.",
                header = "Başlık", options = listOf(AskOption("Kısa ve vurucu"), AskOption("Açıklayıcı"), AskOption("İkisini de dene"), AskOption("Sen seç")),
            )),
            since = NOW - 9 * MIN,
        )),
    )

    private val running = listOf(
        Running("c_dev", "developer", "Telefon: Şimdi ekranı çizimleri", Step("Bash", "./gradlew testDebugUnitTest", NOW - 12_000), NOW - 3 * MIN, 0.42),
        Running(
            "c_res", "arastirmaci", "Yerel LLM seçenekleri ve donanım gereksinimleri — i7-2600K üzerinde neler çalışır, karşılaştırma tablosu",
            Step("WebSearch", "küçük modeller CPU üzerinde token hızı", NOW - 4_000), NOW - 11 * MIN, 1.87,
        ),
        Running("c_mgr", "manager", "Gece vardiyası planı", null, NOW - 25_000, 0.06),
    )

    private val recent = listOf(
        Recent("r1", "developer", "vsc 3.992.0 paketlendi", "Paket kuruldu ve yüklendi; build.pending false.", NOW - 14 * MIN),
        Recent("r2", "psikolog", "Sabah notu", "Bugün enerjin düşük görünüyor; iki küçük iş seç ve öğleden sonra yürüyüş ekle.", NOW - HOUR),
        Recent("r3", "yazar", "Bülten: Eylül sayısı", "Taslak hazır, 1.200 kelime; başlık önerileri sonda.", NOW - 3 * HOUR),
        Recent("r4", "developer", "TalkScribe ZEN 2.4 yayın notu", "Mağaza notu yazıldı; sürüm 2.4.1 gönderildi.", NOW - 5 * HOUR),
        Recent("r5", "arastirmaci", "Tailscale kurulumu", "Durdum: hesap girişi gerekiyor, bu adımı sen yapmalısın.", NOW - 26 * HOUR, failed = true),
        Recent("r6", "manager", "Haftalık plan", "7 iş sıraya kondu; ikisi senin onayını bekliyor.", NOW - 50 * HOUR),
        Recent("r7", "developer", "ZEP Android 1.9 çökme raporu", "Kök neden: mikrofon izni geri alınınca servis yeniden başlıyordu.", NOW - 3 * 24 * HOUR),
        Recent("r8", "yazar", "LinkedIn yazısı", "İki sürüm yazdım; ikincisi daha kısa.", NOW - 4 * 24 * HOUR),
    )

    val nowLoading = NowState("Ev PC", Link.Connecting("192.168.1.20:7300", 1, NOW - 2_000), 0, loading = true)
    val nowEmpty = NowState("Ev PC", online, NOW - 3_000)
    val nowOne = NowState("Ev PC", online, NOW - 3_000, running = running.take(1), recent = recent.take(2))
    val nowMany = NowState("Ev PC", online, NOW - 3_000, waiting = waiting, running = running, recent = recent, moreRecent = true)
    val nowOffline = nowMany.copy(link = offline, updatedAt = NOW - 12 * MIN, moreRecent = false)
    val nowError = nowOne.copy(error = "The farm did not answer console.sessions in 10 s; it may be busy.")

    private var i = 0
    private fun m(role: Role, text: String? = null, tool: String? = null, input: String? = null, turn: Int = 2, ago: Long = 0, error: Boolean = false) =
        Message(++i, turn, role, NOW - ago, text = text, tool = tool, input = input, isError = error)

    const val LAST_ANSWER_HEAD = "Özet"

    private val answer = """
        ## $LAST_ANSWER_HEAD

        İki tema hazır: **açık** ve **koyu**, ikisi de simgenin yeşilinden. Gövde yazısı en az 14 sp, dokunma hedefleri en az 48 dp.

        ### Ne değişti
        - `core/theme/Tokens.kt` — renkler tek yerde
        - `ui/theme/AppTheme.kt` — Compose teması
          - durum renkleri: *çalışıyor* ve *bekliyor*
        - `colors.xml` — uygulama açılırken görünen ilk kare

        | Belirteç | Açık | Koyu |
        |---|---|---|
        | primary | #2E7D46 | #6FD58A |
        | background | #F7F8FB | #12161F |
        | waiting | #9A5B00 | #FFB95C |

        ```kotlin
        val LIGHT = Palette(primary = 0xFF2E7D46, background = 0xFFF7F8FB, /* … */)
        ```

        > Not: web sayfaları da aynı belirteçleri alacak; böylece telefonda açılan sayfa uygulamanın parçası gibi görünür.

        1. Ekran görüntülerine bak
        2. Onayla ya da düzeltme iste

        Sonraki adım için [tasarım notu](https://example.invalid/b-yolu) hazır.
    """.trimIndent()

    private val history = listOf(
        m(Role.USER, "Dünkü işi kısaca özetle.", turn = 1, ago = 40 * MIN),
        m(Role.ASSISTANT, "Dün üç iş kapandı: CI anahtarı, B yolu belgeleri ve haftalık erişim denetimi.", turn = 1, ago = 39 * MIN),
        m(Role.USER, "Şimdi ekranının renklerini Agent Farm simgesinden türet ve açık/koyu iki tema hazırla.", ago = 20 * MIN),
        m(Role.ASSISTANT, "Bakıyorum; önce simgenin renklerini çıkarıyorum.", ago = 19 * MIN),
        m(Role.TOOL, tool = "Read", input = "media/icon.png", ago = 19 * MIN),
        m(Role.TOOL_RESULT, "[image 256×256]", ago = 19 * MIN),
        m(Role.TOOL, tool = "Edit", input = "app/src/main/java/com/muvusoft/agentfarm/core/theme/Tokens.kt", ago = 15 * MIN),
        m(Role.TOOL_RESULT, "The file has been updated.", ago = 15 * MIN),
        m(Role.TOOL, tool = "Bash", input = "node test/run-all.js", ago = 12 * MIN),
        m(Role.TOOL_RESULT, "PASSED: All 13 suite(s) green (3290 assertions passed, 0 failed)", ago = 12 * MIN),
    )

    private val base = ConversationState("c_dev", "developer", "Telefon: Şimdi ekranı çizimleri", "Claude", online, NOW - 2_000)

    val convAnswer = base.copy(messages = history + m(Role.ASSISTANT, answer, ago = 10 * MIN), older = false)

    private val liveTurn = history.take(4) + listOf(
        m(Role.TOOL, tool = "Bash", input = "./gradlew connectedDebugAndroidTest", ago = 90_000),
        m(Role.TOOL_RESULT, "BUILD SUCCESSFUL in 1m 12s", ago = 30_000),
        m(Role.TOOL, tool = "Read", input = "app/src/androidTest/java/com/muvusoft/agentfarm/VisualLanguageTest.kt", ago = 20_000),
        m(Role.TOOL_RESULT, "(214 lines)", ago = 19_000),
        m(Role.TOOL, tool = "Bash", input = "./gradlew testDebugUnitTest --tests '*Markdown*'", ago = 12_000, error = true),
    )
    val convLive = base.copy(
        messages = liveTurn, running = true, turnSince = NOW - 3 * MIN, cost = 0.42, older = true,
        live = Step("Bash", "./gradlew testDebugUnitTest", NOW - 12_000),
        outgoing = listOf(Outgoing("o1", "Bitince ekran görüntülerini de gönder.", SendState.SENDING)),
    )
    val convQuestion = convLive.copy(
        live = null, outgoing = emptyList(),
        ask = Ask(AskKind.QUESTION, "", questions = listOf(AskQuestion(
            "Hangi temayı varsayılan yapayım?", header = "Tema",
            options = listOf(AskOption("Sistemi izle", "Telefonun açık/koyu ayarına uyar"), AskOption("Hep koyu"), AskOption("Hep açık")),
        )), since = NOW - MIN),
    )
    val convPermission = convLive.copy(live = null, outgoing = emptyList(), ask = Ask(AskKind.PERMISSION, "git push origin master --follow-tags", "Bash", since = NOW - 30_000))
    val convAfterTurn = convLive.copy(engineLabel = "Codex", steerable = false, outgoing = listOf(Outgoing("o2", "Bir de koyu temayı kontrol et.", SendState.AFTER_TURN)))
    val convReadOnly = convLive.copy(
        outgoing = emptyList(),
        readOnly = "This session runs in a terminal on the PC. It can be read here, not written to.",
    )
    val convOffline = convAnswer.copy(link = offline, updatedAt = NOW - 12 * MIN)
    val convLoading = base.copy(loading = true)
}
