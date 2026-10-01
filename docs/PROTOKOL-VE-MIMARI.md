# Agent Farm Android — Protokol ve Mimari

Telefon ile Agent Farm arasındaki bağın özeti. **Tek doğru sözleşme dosyasıdır:**
`contract/remote-contract.v1.json`. Bu belge onu insan için anlatır; ikisi ayrışırsa dosya haklıdır
ve `test/test_contract.js` dosyadaki her çerçeve ve olayın burada anıldığını ölçer.

Tasarımın gerekçesi: vault projesi `10_Notes/A-PLN - Android Uygulaması Yeniden Tasarım Raporu - Telefondan Tam Agent Farm - 260930.md`.

---

## 1. Mimari — ince kabuk, sayfalar Agent Farm'dan

```
 PC: VS Code + Agent Farm
   sayfalar (Şimdi, Bekleyenler, Konsol, ...) ── aynı kod ──> Panel Host
   köprü op'ları + olay yayını ──> Remote Host (src/remote)  HTTPS + WebSocket
                                        │ LAN doğrudan · Tailscale · (ileride relay)
 Telefon: Android kabuğu (bu repo)
   WebView (Agent Farm sayfaları + mobil katman) ── yerel taşıma ──┐
   bağlantı yöneticisi · eşleşme · Keystore anahtarı ──────────────┤── tek WebSocket
   ön plan servisi · bildirimler · ses · paylaşım · widget ────────┘
```

- **Ekranları Agent Farm çizer.** Telefon hiçbir Agent Farm ekranını yeniden yazmaz; sayfaları
  `vsc_AgentFarm/src/remote/` sunar, kabuk gösterir.
- **Kotlin'in payı** telefonun yerel yapabildiği ve web'in yapamadığı işlerdir: eşleşme, bağlantı,
  bildirim, ses, paylaşım, hızlı erişim, çevrimdışı giden kutusu, uygulama kilidi. `ui/` yalnız kabuk
  ekranlarını tutar: eşleşme, bağlantı durumu, cihaz ayarları.
- **WebView ağa kendisi çıkmaz.** Sayfa ve kaynak istekleri `shouldInterceptRequest` ile, canlı mesajlar
  sayfadaki `afRemote.js`'in yerel köprüsüyle yerel koda gider; yerel kod sabitlenmiş sertifikayla
  bağlanır. Kendinden imzalı sertifikayı WebView'de "kabul et" diye geçiştirmek yoktur.

## 2. Sözleşmenin sahibi ve kopyası

| | Yer |
|---|---|
| Sahibi | `vsc_AgentFarm/src/remote/contract/remote-contract.v1.json` (Agent Farm) |
| Kopya | `contract/remote-contract.v1.json` (bu repo) |
| Yenileme | `node tools/sync_contract.js` — iki repo yan yanaysa kopyalar; `--check` yalnız karşılaştırır |

- Sözleşme değişikliği Agent Farm'da yapılır; kopya aynı iş kaleminde yenilenir.
- Yeni sürüm yeni dosyadır (`remote-contract.v2.json`); eski sürüm konuşan telefonlar için durur.
- Kotlin modelleri kopyadan türetilir; CI'daki sahte sunucu da kopyayı okur.

## 3. Taşıma

| Uç | Ne yapar |
|---|---|
| `GET /health` | Kimlik doğrulamasız: `{ ok, farm: { id, name }, contract }` |
| `POST /pair` | Eşleşme isteği → eşleşme cevabı ya da `{ error }` |
| `GET /ws` | WebSocket; her mesaj `kind` taşıyan tek bir JSON çerçevesi |

- **TLS:** Agent Farm kendi sertifikasını üretir. Telefon, eşleşme bağlantısından okuduğu SHA-256
  parmak izini sabitler ve başka hiçbir sertifikaya güvenmez.
- **İmza:** ECDSA P-256 + SHA-256 (DER). İmzalanan metin UTF-8 olarak
  `agentfarm.remote/1|<farm id>|<device id>|<nonce>`. Cihazın açık anahtarı X.509 SPKI DER, base64.
- **Adresler:** eşleşme bağlantısı aday adres listesi taşır (en yerel önce). Bağlantı yöneticisi
  sırayla dener; kullanıcı yol seçmez, yalnız durum şeridinde görür.

## 4. Eşleşme

1. Agent Farm QR gösterir:
   `agentfarm://pair?v=1&farm=<id>&name=<ad>&code=<tek kullanımlık kod>&fp=<sha256>&a=<host:port>,...`
2. Telefon Android Keystore'da kendi P-256 anahtar çiftini üretir (özel anahtar cihazdan çıkmaz).
3. `POST /pair` gövdesi: `code`, `name` (cihaz adı), `publicKey`, `platform`, `app`.
4. Cevap: `device` (cihaz kimliği), `farm`, `scope`. Bundan sonra token gezmez; her bağlantı imzayla açılır.

## 5. El sıkışma (`/ws`)

1. Agent Farm önce konuşur: `challenge` (`nonce`, `farm`, `contract`).
2. Telefon `hello` ile cevaplar: `device`, `contract`, nonce üzerindeki `signature`, isteğe bağlı `resumeAfter`.
3. Agent Farm `welcome` (yetki, özellikler, `lastSeq`, isteğe bağlı `session`) ya da `refuse` döner.
   `refuse` nedenleri: `unknown-device`, `revoked`, `bad-signature`, `contract`, `not-allowed`, `busy`.
4. `welcome` gelmeden başka hiçbir çerçeve kabul edilmez.
5. **Sayfalar ve kaynaklar:** `GET /view/<sayfa>?view=<id>` sayfanın HTML'ini (`acquireVsCodeApi` yerine
   `afRemote.js`), `GET /res/<yol>` sayfanın yüklediği dosyaları verir; ikisi de
   `Authorization: AF <welcome.session>` ister. `session` yalnız o cihazın soketi açıkken geçerlidir,
   telefon onu diske yazmaz. WebView ağa kendisi çıkmaz: bu istekleri `shouldInterceptRequest` yakalar
   ve yerel kod sabitlenmiş sertifikalı istemciyle getirir.

## 6. Çerçeveler

| Çerçeve | Gönderen | İş |
|---|---|---|
| `challenge` | Agent Farm | Bağlantıyı açar, imzalanacak nonce'u verir |
| `hello` | telefon | Cihaz kimliği + imza; kaçırılan olaylar için `resumeAfter` |
| `welcome` | Agent Farm | Bağlantı kabul: yetki, özellik cevapları, son olay numarası |
| `refuse` | Agent Farm | Bağlantı reddi, nedeniyle; sürüm uyuşmazlığında gereken sürüm |
| `event` | Agent Farm | Numaralı olay (`seq`, `type`, `ts`, `data`) |
| `gap` | Agent Farm | İstenen olayların bir kısmı artık tutulmuyor: `from`–`to` arası kayıp |
| `call` | telefon | Bir köprü op'u çağırır: `id`, `op`, `args` |
| `result` | Agent Farm | Çağrının cevabı; aynı `id` tekrar gelirse saklı cevap + `duplicate` |
| `view.open` | telefon | Bir Agent Farm sayfasını açar (`view`, `page`) |
| `view.post` | Agent Farm | Sayfaya host mesajı |
| `view.msg` | telefon | Sayfadan host'a mesaj |
| `view.close` | ikisi de | Görünümü kapatır |
| `ping` / `pong` | ikisi de | Canlılık |

## 7. Olaylar

| Olay | Ne zaman |
|---|---|
| `needs.changed` | Bekleyenler sayısı değişti (en üstteki kalemle) |
| `ask.opened` | Bir oturum soru sordu (seçenekleriyle) |
| `permission.opened` | Bir oturum araç izni istiyor |
| `turn.finished` | Bir tur bitti (başlık + özet) |
| `notice.posted` | Notices'e yeni kayıt düştü |
| `quota.warn` | Bir motorun kota penceresi eşiği geçti |
| `session.ended` | Bir oturum kapandı |
| `ask.closed` | Bir soru cevaplandı ya da geri çekildi — masada, Telegram'da ya da bir telefonda; o sorunun uyarısı her telefondan kalkar |
| `permission.closed` | Bir izin cevaplandı ya da geri çekildi, nerede olursa olsun; o iznin uyarısı her telefondan kalkar |

## 8. Kurallar

- Telefon tanımadığı olay türünü yok sayar; Agent Farm tanımadığı çerçeveyi reddeder (`call` için `ok:false`).
- **Tam bir kez:** her `call` telefonun ürettiği bir `id` taşır; aynı `id` ikinci kez gelirse op yeniden
  çalışmaz, saklı cevap `duplicate:true` ile döner. Çevrimdışı giden kutusu bu yüzden güvenle yeniden gönderir.
- **Giden kutusu diskte durur, altı saatten eski çağrı atılır:** süreç ölse de bekleyen çağrı ilk `welcome`
  sonrasında gider; bekleyen bir soruya saatler sonra düşen cevap yeni bir istem gibi okunabileceği için
  `createdAt` üzerinden altı saati geçen çağrı gönderilmez (`core/state/Outbox.kt`).
- **Bekleyen sayısı 0'a inince** (`needs.changed`) o çiftliğin soru/izin bildirimleri, `session.ended` gelince
  o oturumunkiler telefondan kalkar.
- **Olay numaraları yalnız artar.** `resumeAfter` sonrası olaylar verilir; tutulmayanlar için önce bir `gap`.
- Sürüm uyuşmazlığı: `refuse` nedeni `contract`, gereken sürümle (`minContract`).
- **Gizli değerler hiçbir çerçevede gezmez**; yalnız var olup olmadıkları.

## 9. Yetki ve özellikler

- **Yetki seviyeleri** (cihaz başına, azdan çoğa): `read` → `answer` (soru/izin cevabı) → `manage`
  (oturum, mesaj, iptal, görev) → `admin` (ayarlar, hesaplar, ajan dosyaları). Kullanıcının kendi
  telefonunda varsayılan `manage`.
- **Özellik kimlikleri:** `remote.access`, `remote.control`, `remote.devices`, `remote.voice`,
  `remote.share`, `remote.wake`. `welcome.features` her biri için `{ allowed, reason? }` verir.
- Cihaz tek dokunuşla iptal edilir; uzaktan yapılan her eylem cihaz adıyla denetim kaydına yazılır.

## 10. Kabuğun yerel girişleri

- Bildirim düğmeleri, paylaşım, ses, widget, hızlı ayar **var olan köprü op'larını** `call` ile çağırır;
  telefon köprünün etrafından dolaşmaz. Eksik bir yetenek Agent Farm'a op olarak eklenir.
- **Yerel "yeni oturum"** (paylaşım, ses, widget) Telegram `/new` ile aynı kararı kullanır ve son ayarları
  devralır (`seedLastLaunch`). Köprüdeki `sessions.start` devralmadığı için kullanılmaz.
- Uzaktan açılan sayfalar yeni bir oturum kapısı değildir; masaüstündeki kapıların kendisidir.
- Sayfanın host'a özgü istekleri telefonda karşılanır: kopyalama telefonun panosuna, dış bağlantı
  telefonun tarayıcısına, dosya açma salt okunur görüntüleyiciye gider.
- **Kabuğun karşıladığı sayfa mesajları** (`core/view/ShellRequest`) çiftliğe hiç gitmez:
  `afnav` (`to` = sayfa kimliği, isteğe bağlı `args` nesnesi) aynı kabukta o sayfayı kendi görünümüyle açar
  ve `args`'ı `view.open` ile olduğu gibi çiftliğe iletir — kabuk yalnız taşır, çiftlik denetler (ör. `session`
  sayfası `sessionId` ya da `file`, `ts`, `roles` alır ve yalnız dizinin bildiği bir transcript'i açar; gerisine
  `unavailable` der). Nesne olmayan `args` taşınmaz. Aynı sayfa başka `args` ile yığında yeni bir giriştir.
  Geri önceki sayfaya döner, ilk sayfada Geri çiftlikten çıkar (`core/view/PageStack`, en çok 20 sayfa); sayfa
  kimliği olmayan bir hedef hiçbir şey açmaz. `afClipboard` (`token`, `text`) metni telefonun panosuna
  koyar ve sayfaya masaüstündeki biçimle `afClipboardDone` (`token`, `ok`, `error?`) döner.
  `afVoice` (`token`) sistemin konuşma tanıyıcısını (`RECOGNIZE_SPEECH`, `tr-TR`) açar ve
  `afVoiceDone` (`token`, `text?`, `error?`) döner — vazgeçilirse `text` yoktur. Sayfa mikrofonu
  yalnız `afShell.voice()` doğru dönerse çizer (telefonda bir tanıyıcı var demektir).
  Diğer bütün sayfa mesajları `view.msg` olarak gider.
- Çiftlik tarafında sayfa başına bir kural vardır (`vsc_AgentFarm/src/panels/remotePages.ts`): bir
  mesaj ya yalnız PC'de çalışır (telefondan hiç çalışmaz), ya bakar (`read`), ya cevaplar
  (`answer`), ya da sayfanın işlem yetkisini ister. Bugün telefonda açılan sayfalar: `now`,
  `needs`, `console`, `sessions`, `tasks`, `session` (bir oturumun transcript'i; sayfalardaki "transcript'i aç"
  tıklaması telefonda bu sayfaya `afnav` olur — `vsc_AgentFarm/media/afRemote.js`); listede olmayan sayfa
  `view.close` `unavailable` alır.

## 11. Ses — TalkScribe (ZEP)

Komut kutusundaki mikrofon: ZEP yüklüyse onun dikte Intent'i (`com.miropharm.talkscribe.ACTION_DICTATE`,
sonuç `ActivityResult` ile döner), değilse Android'in konuşma tanıma servisi. Kulaklık modunda tur özeti
ve sorular TTS ile okunur.

## 12. Güvenlik kalkanı

- **Yıkıcı eylemler** (silme, hard reset, hesap değişikliği) Agent Farm'ın şekil filtresinden geçer ve
  telefonda biyometrik onay ister.
- **Bütçe:** uzaktan kanal bütçesi Telegram ile ortaktır; aşılınca sistem durur ve açık onay ister.
- Yalnız eşleşmiş ve iptal edilmemiş cihazların imzası kabul edilir.
