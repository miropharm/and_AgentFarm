package com.muvusoft.agentfarm.core.lang

/** Turkish for every Words.t key in core/ (test/test_core_words.js holds the two together). */
internal val TURKISH: Map<String, String> = mapOf(
    // core/Time.kt
    "{0} s" to "{0} sn",
    "{0} min" to "{0} dk",
    "{0} h" to "{0} sa",
    "{0} d" to "{0} g",
    // core/link/LinkText.kt
    "{0} Its service answers at {1}; open the farm to start Agent Farm from here." to
        "{0} Servisi {1} adresinde yanıt veriyor; Agent Farm'ı buradan başlatmak için çiftliği aç.",
    "Connected · {0}" to "Bağlı · {0}",
    "Connected: {0}, scope {1}, {2} ago." to "Bağlı: {0}, yetki {1}, {2} önce.",
    "Connecting · {0}" to "Bağlanıyor · {0}",
    "Reconnecting · attempt {0}" to "Yeniden bağlanıyor · {0}. deneme",
    "Connecting: {0}, attempt {1}, started {2} ago. It connects by itself once the farm is reachable." to
        "Bağlanıyor: {0}, {1}. deneme, {2} önce başladı. Çiftliğe ulaşılınca kendiliğinden bağlanır.",
    "Not connected · {0}" to "Bağlı değil · {0}",
    "Not connected: {0}, for {1}. It connects by itself once the farm is reachable." to
        "Bağlı değil: {0}, {1} süredir. Çiftliğe ulaşılınca kendiliğinden bağlanır.",
    "Resting · wakes when something happens" to "Dinleniyor · bir şey olunca uyanır",
    "Resting for {0}: the link closed to spare the battery. The farm wakes this phone when a question, a permission or a finished turn arrives; it also opens when the app comes forward and every few minutes to catch up." to
        "{0} süredir dinleniyor: pil için bağlantı kapandı. Bir soru, bir izin ya da biten bir tur gelince çiftlik bu telefonu uyandırır; bağlantı uygulama öne gelince ve kaçırılanları almak için birkaç dakikada bir de açılır.",
    "Refused · {0}" to "Reddedildi · {0}",
    "The farm refused the link: {0}" to "Çiftlik bağlantıyı reddetti: {0}",
    "Addresses: {0}" to "Adresler: {0}",
    "this app is out of date" to "bu uygulama eski",
    "Agent Farm is out of date" to "Agent Farm eski",
    "the versions do not match" to "sürümler uyuşmuyor",
    "this device's access was revoked" to "bu cihazın erişimi kaldırıldı",
    "the farm does not know this device" to "çiftlik bu cihazı tanımıyor",
    "the device signature could not be verified" to "cihaz imzası doğrulanamadı",
    "not allowed" to "izin verilmedi",
    "the farm is busy" to "çiftlik meşgul",
    "Update the app on the phone: the farm needs contract {0}, this app speaks {1}." to
        "Telefondaki uygulamayı güncelle: çiftlik {0}. sözleşmeyi istiyor, bu uygulama {1}. sözleşmeyi konuşuyor.",
    "Update Agent Farm: this app speaks contract {0}, the farm an older one." to
        "Agent Farm'ı güncelle: bu uygulama {0}. sözleşmeyi konuşuyor, çiftlik daha eskisini.",
    "Update the app or Agent Farm." to "Uygulamayı ya da Agent Farm'ı güncelle.",
    "It tries again shortly." to "Birazdan yeniden dener.",
    "Pair again with a new QR code from Agent Farm." to "Agent Farm'dan yeni bir QR kodla yeniden eşle.",
    "not connected yet" to "henüz bağlanmadı",
    "its addresses could not be reached" to "adreslerine ulaşılamadı",
    "the link closed" to "bağlantı kapandı",
    "stopped" to "durduruldu",
    // core/now/NowText.kt
    "The farm did not answer. Pull down to try again." to "Çiftlik yanıt vermedi. Yeniden denemek için aşağı çek.",
    "The farm's answer did not read. Agent Farm on the PC may need an update." to
        "Çiftliğin yanıtı okunamadı. PC'deki Agent Farm'ın güncellenmesi gerekebilir.",
    // core/session/SessionText.kt
    "Connecting" to "Bağlanıyor",
    "Resting" to "Dinleniyor",
    "Offline" to "Çevrimdışı",
    "{0} · nothing saved yet" to "{0} · henüz kayıt yok",
    "{0} · updated {1} ago" to "{0} · {1} önce güncellendi",
    // core/away/Away.kt
    "The farm did not answer; try again in a moment." to "Çiftlik yanıt vermedi; birazdan yeniden dene.",
    "Agent Farm is not running: VS Code is closed on {0}." to "Agent Farm çalışmıyor: {0} üzerinde VS Code kapalı.",
    "VS Code is closed · last state {0} ago" to "VS Code kapalı · son durum {0} önce",
    "VS Code is closed" to "VS Code kapalı",
    "VS Code was last seen {0} ago." to "VS Code en son {0} önce görüldü.",
    "There is no saved state to show." to "Gösterilecek kayıtlı bir durum yok.",
    "The numbers below are from {0} ago; nothing behind them moves until VS Code opens." to
        "Aşağıdaki sayılar {0} öncesine ait; VS Code açılana dek arkalarındaki hiçbir şey ilerlemez.",
    "{0} waiting for you" to "{0} seni bekliyor",
    "{0} unread notice" to "{0} okunmamış bildirim",
    "{0} unread notices" to "{0} okunmamış bildirim",
    "{0} running task" to "{0} çalışan görev",
    "{0} running tasks" to "{0} çalışan görev",
    "Agent Farm could not be opened." to "Agent Farm açılamadı.",
    "Agent Farm is already running on {0}." to "Agent Farm {0} üzerinde zaten çalışıyor.",
    "VS Code is already starting on {0} (asked {1} ago); it connects by itself once it is up." to
        "VS Code {0} üzerinde zaten açılıyor ({1} önce istendi); açılınca kendiliğinden bağlanır.",
    "VS Code is already starting on {0}; it connects by itself once it is up." to
        "VS Code {0} üzerinde zaten açılıyor; açılınca kendiliğinden bağlanır.",
    "Starting VS Code on {0}; the farm's pages open here once it is up." to
        "VS Code {0} üzerinde başlatılıyor; açılınca çiftliğin sayfaları burada açılır.",
    "Agent Farm answered, but not with a start." to "Agent Farm yanıt verdi, ama başlatmadı.",
    // core/conversation/Blocks.kt
    "1 step" to "1 adım",
    "{0} steps" to "{0} adım",
    "{0} failed" to "{0} başarısız",
    // core/conversation/Sending.kt
    "Not open in the Console: the phone reads it, and writes only to a session the Console hosts." to
        "Konsolda açık değil: telefon bunu okur, ama yalnızca Konsolun barındırdığı bir oturuma yazar.",
    "The farm did not answer." to "Çiftlik yanıt vermedi.",
    "A picture goes only while the farm answers." to "Resim yalnızca çiftlik yanıt verirken gider.",
    "This session closed in the Console; what it said stays here to read." to
        "Bu oturum Konsolda kapandı; söyledikleri burada okunmak üzere kalır.",
    "This session cannot be followed live from here." to "Bu oturum buradan canlı izlenemiyor.",
    "This session cannot be followed live from here ({0})." to "Bu oturum buradan canlı izlenemiyor ({0}).",
    // core/Greeting.kt
    "Hello, Agent Farm" to "Merhaba, Agent Farm",
    "Version {0}" to "Sürüm {0}",
    // core/lock/LockPolicy.kt
    "This phone has no screen lock or fingerprint set up." to "Bu telefonda ekran kilidi ya da parmak izi kurulu değil.",
    "This phone offers no way to verify its owner." to "Bu telefon sahibini doğrulamak için bir yol sunmuyor.",
    "Owner verification is not available right now." to "Sahip doğrulaması şu an kullanılamıyor.",
    // core/notify/Alerts.kt
    "{0} is asking · {1}" to "{0} soruyor · {1}",
    "Reply" to "Yanıtla",
    "{0} asks permission: {1} · {2}" to "{0} izin istiyor: {1} · {2}",
    "Allow" to "İzin ver",
    "Deny" to "Reddet",
    "{0}: turn finished · {1}" to "{0}: tur bitti · {1}",
    "Workflow \"{0}\" asks · {1}" to "\"{0}\" iş akışı soruyor · {1}",
    "Mission failed · {0}" to "Misyon başarısız · {0}",
    "Mission finished · {0}" to "Misyon bitti · {0}",
    "Quota warning · {0}" to "Kota uyarısı · {0}",
    "{0} {1} window is {2}% full" to "{0} {1} penceresi %{2} dolu",
    "Answers itself in {0} min" to "{0} dk sonra kendiliğinden yanıtlanır",
    "{0}'s question answers itself in {1} min · {2}" to "{0}: soru {1} dk sonra kendiliğinden yanıtlanır · {2}",
    "Answer it before then, or its recommended option is taken for you." to
        "O zamana dek yanıtla; yoksa önerilen seçenek senin yerine seçilir.",
    "Stop" to "Durdur",
    // core/notify/StatusLine.kt
    "No paired farm" to "Eşlenmiş çiftlik yok",
    "VS Code closed · {0}" to "VS Code kapalı · {0}",
    "Resting · {0}" to "Dinleniyor · {0}",
    "{0} farms connected" to "{0} çiftlik bağlı",
    "{0} farms resting" to "{0} çiftlik dinleniyor",
    "{0} connected · {1} resting" to "{0} bağlı · {1} dinleniyor",
    "{0} resting · {1} not connected" to "{0} dinleniyor · {1} bağlı değil",
    "Link refused" to "Bağlantı reddedildi",
    "Not connected · retrying" to "Bağlı değil · yeniden deneniyor",
    "{0}/{1} farms connected" to "{0}/{1} çiftlik bağlı",
    "{0} with VS Code closed" to "{0} tanesinde VS Code kapalı",
    "{0} waiting" to "{0} bekliyor",
    "nothing waiting" to "bekleyen yok",
    "{0} running" to "{0} çalışıyor",
    "nothing running" to "çalışan yok",
    "last turn {0} ago" to "son tur {0} önce",
    "{0} unsent" to "{0} gönderilmemiş",
    // core/pairing/PairingVerdict.kt
    "This is not an Agent Farm pairing link." to "Bu bir Agent Farm eşleme bağlantısı değil.",
    "This farm speaks a newer contract (v{0}); update the app." to "Bu çiftlik daha yeni bir sözleşme konuşuyor (v{0}); uygulamayı güncelle.",
    "The pairing code was used or has expired; get a new code in Agent Farm." to
        "Eşleme kodu kullanılmış ya da süresi dolmuş; Agent Farm'dan yeni bir kod al.",
    "The farm that answered is not the farm in the link; nothing was paired." to
        "Yanıt veren çiftlik bağlantıdaki çiftlik değil; hiçbir şey eşlenmedi.",
    "The server certificate does not match the fingerprint in the link; nothing was paired." to
        "Sunucu sertifikası bağlantıdaki parmak iziyle uyuşmuyor; hiçbir şey eşlenmedi.",
    "Could not reach the farm. Addresses tried: {0}. Is the phone on the same Wi-Fi? On the computer, Toolbox > Services > Agent Farm app says what stops it." to
        "Çiftliğe ulaşılamadı. Denenen adresler: {0}. Telefon aynı Wi-Fi'da mı? Bilgisayarda Araç Kutusu > Servisler > Agent Farm uygulaması neyin engellediğini söyler.",
    "The farm refused the pairing ({0}): {1}" to "Çiftlik eşlemeyi reddetti ({0}): {1}",
    "The farm refused the pairing ({0})." to "Çiftlik eşlemeyi reddetti ({0}).",
    // core/power/BatteryPolicy.kt
    "Not restricted" to "Kısıtlanmamış",
    "The link and its alerts arrive without delay, even with the screen off." to
        "Bağlantı ve uyarıları, ekran kapalıyken bile gecikmeden gelir.",
    "Open setting" to "Ayarı aç",
    "Optimized" to "Optimize ediliyor",
    "Alerts may be late while the phone sits idle for a long time. Find Agent Farm in the list and choose \"Don't optimize\"." to
        "Telefon uzun süre boşta kalınca uyarılar gecikebilir. Listede Agent Farm'ı bul ve \"Optimize etme\" seçeneğini seç.",
    // core/share/Share.kt
    "waiting for you" to "seni bekliyor",
    "running" to "çalışıyor",
    "idle" to "boşta",
    "Not sent: {0}" to "Gönderilmedi: {0}",
    "the farm did not answer" to "çiftlik yanıt vermedi",
    "Queued; it is said when the turn ends." to "Sıraya alındı; tur bitince söylenir.",
    "Sent." to "Gönderildi.",
    "{0} · not connected" to "{0} · bağlı değil",
    "Waiting on this phone: it goes to {0} when {1} answers, within {2} h. A notification says how it went." to
        "Bu telefonda bekliyor: {1} {2} sa içinde yanıt verince {0} hedefine gider. Nasıl gittiğini bir bildirim söyler.",
    "Not sent to {0} · {1}" to "Gönderilmedi: {0} · {1}",
    "{0} did not answer within {1} h; the text was dropped." to "{0} {1} sa içinde yanıt vermedi; metin bırakıldı.",
    "{0} Share it again to answer." to "{0} Yanıtlamak için yeniden paylaş.",
    "Delivered to {0} · {1}" to "Ulaştı: {0} · {1}",
    // core/share/Stop.kt
    "Not stopped: {0}" to "Durdurulmadı: {0}",
    "Stopped." to "Durduruldu.",
    "Stopped: {0}." to "Durduruldu: {0}.",
    // core/speech/PageReading.kt
    "this language" to "bu dil",
    "This phone has no voice for {0} - add one in the phone's text-to-speech settings." to
        "Bu telefonda {0} için ses yok - telefonun metin okuma ayarlarından bir tane ekle.",
    "The phone's voice could not read this." to "Telefonun sesi bunu okuyamadı.",
    // core/handsfree/VoiceAnswer.kt
    "{0}. Sending." to "{0}. Gönderiyorum.",
    "Called off." to "Vazgeçildi.",
    "No link: it waits on this phone." to "Bağlantı yok: telefonda bekliyor.",
    // core/handsfree/StatusCall.kt
    "Agent Farm does not answer right now." to "Agent Farm şu an cevap vermiyor.",
    "Agent Farm could not say how it is: it may need an update on the computer." to
        "Agent Farm durumunu söyleyemedi: bilgisayarda güncellenmesi gerekebilir.",
    // core/handsfree/VoiceNew.kt
    "Which agent?" to "Hangi ajan?",
    "What should {0} do?" to "{0} ne yapsın?",
    "{0}: {1}. Opening." to "{0}: {1}. Açıyorum.",
    "{0} is open." to "{0} açıldı.",
    "Agent Farm did not open it." to "Agent Farm bunu açmadı.",
)
