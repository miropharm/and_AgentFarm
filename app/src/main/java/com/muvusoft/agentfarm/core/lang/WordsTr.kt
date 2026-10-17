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
)
