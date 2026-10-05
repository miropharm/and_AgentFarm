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
   `afRemote.js`), `GET /res/<yol>` sayfanın yüklediği dosyaları verir; `GET /res/~/<görünüm>/<belirteç>`
   sayfanın kendi seçtiği bir dosyayı (Stüdyo'nun resmi, klibi) yalnız o görünümü açan cihaza, görünüm açıkken
   verir — belirteç görünüme özel ve tahmin edilemez, boyut sınırını aşan dosya `413` alır; üçü de
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
| `event` | Agent Farm | Numaralı olay (`seq`, `type`, `ts`, `data`); `user` kimin kaydı olduğunu söyler, alanı olmayan olay sahibinindir |
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
| `ask.answersSoon` | Bekleyen bir soru, kimse cevaplamazsa `dueAt` anında kendiliğinden cevaplanacak (`minutes` kala) |
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
- **`spoken { text, lang }`** (`ask.opened`, `ask.answersSoon`, `permission.opened`, `turn.finished`,
  `notice.posted`): Agent Farm'ın kendi okuyucusunun o olay için söyleyeceği metin, sese göre temizlenmiş, ve
  dilinin BCP-47 ana etiketi. İsteğe bağlıdır; yoksa telefon kendi okumasına döner.

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
- **Kabuğun `console.send`'i Telegram'la aynı uzaktan kapıdan geçer** (bugünkü bütçe, geri alınamayan komut,
  meşgulse kuyruk; tek defter, tek tavan): durdurulursa cevap `ok:true` + `{ sent:false, confirm:<kapı>, note }`.
  Telefon `note`'u gösterir, sahibin kilidiyle onay alır ve aynı metni `confirm:<kapı>` ile **yeni** bir `call`
  olarak yollar (aynı `id` saklı cevabı geri getirirdi). Kapı adlarını çiftlik verir ve denetler, telefon yalnız
  geri yollar; onay kısa bir süre (`CONFIRM_TTL_MS`) ve yalnız soran cihaz, aynı oturum ve aynı metin için
  geçerlidir; her evet öncekilere eklenir, yani iki kapıya takılan mesaj iki kez sorulur ve gider.
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
  `needs`, `console`, `sessions`, `tasks`, `notices`, `dashboard`, `missions`, `queue`, `turns` (çiftliğin her
  turu; telefonda her tur bir kart), `projects` (projeler; telefonda tek sütun, liste açık projenin
  üstünde; zaman çizelgesindeki bir oturum bağlantısı telefonda oturum sayfasını açar), `agent` (bir ajanın
  sayfası; `args.agentId` hangi ajan olduğunu söyler, çiftliğin tanımadığı ajan `unavailable` alır; sayfalardaki
  `openAgent` tıklaması telefonda bu sayfaya `afnav` olur), `search` (bütün transcript'lerde arama; okuma
  yetkisi yeter, bir sonuca dokunmak telefonda oturum sayfasını açar), `timeline` (canlı zaman çizelgesi; şeritler
  telefonda çizildikleri boyutta kalır ve kutularında yana kayar, bir şerit, işaret ya da "transcript'i aç" telefonda
  oturum sayfasını açar; terminal, oturumu bitirme ve zorla kapatma PC'de kalır), `engines` ve `accounts`
  (motorlar, sağlayıcı rotaları, hesaplar ve kotaları; ikisi de `admin` ister — hesap, sağlayıcı, motor ve kural
  op'ları bakmak için bile admin ister; manage cihaz `unavailable` alır), `a2anotes` ve `a2atasks` (ajanlar
arası notlar ve görevler; okuma yetkisi açar, değiştirmek manage ister; `args.focusId` tek bir notu/görevi açar,
bir notun görevi ile bir görevin notu telefonda öbür panoyu açar), `portfolio` (Ajanlar; telefonda ajan başına
bir kart, oturum başlatma, proje atama, klasör ve yeni ajan PC'de kalır), `guide` (yardım; `args.doc`
yalnız yardım belgesi anahtarı — `guide` ya da `examples`, ham dosya `unavailable` alır — `args.anchor` bir
başlık; sayfalardaki `openGuide` tıklaması telefonda bu sayfaya `afnav` olur; telefonda dil seçimi yalnız o
görünümü değiştirir, bir bağlantı telefona kopyalanır), `wiki` (wiki işaretleri; telefonda işaret başına
bir kart, değiştirmek manage ister, kategori adlandırma soruları telefonda sorulur), `toolbox` (Araç Kutusu;
`admin` ister — MCP tanımları ve Telegram, web, telefon uygulaması yapılandırması bakmak için bile admin;
araçlar kart, onaylar telefonda; erişim modu telefonda yalnız gösterilir, Telegram belirteci, güvenlik duvarı,
PC panosu, dosya ve klasör PC'de kalır), `sound` (Ses; okuma yetkisi açar, her anahtar, ton, ses ve deneme
manage ister ve PC'de çalar; olaylar, oturumlar ve ajanlar kart; yeni ses için dosya seçici, ses klasörü ve kanal
ayarı PC'de kalır — Konsol'un ve canlı zaman çizelgesinin ses panelleri de bu ikisini telefondan reddeder),
`settings` (Ayarlar; `admin` ister — ayarlar ad alanı bakmak için bile admin; bir satır, sıfırlaması, ayar
seti, Akış ve İçerik kartı ve ajan seçimleri telefondan yazar; telefonun kendi `agentFarm.remote.listen` ve
`.port` ayarı telefonda yalnız gösterilir ve her yazma yolunda atlanır; Claude Code'un ham settings.json'ı
telefona gönderilmez; dosya/klasör seçiciler, editör, terminal, anahtar kasası, bağlantılar, dışa/içe aktarma
ve Telegram belirteci PC'de kalır), `living` (Canlılık; bakmak da denetim kaydetmek de manage ister — köprüdeki
`living.*` op'ları gibi; bir sinyalin oku telefonda o sayfayı `afnav` ile açar; dosya seçici ve "JSON olarak
düzenle" PC'de kalır), `studio` (Stüdyo; bakmak da her iş de manage ister — köprüdeki `studio.*` op'ları gibi;
resimler ve klipler `/res/~/` yoluyla gelir; ↻ telefonda çalışır ve sonucu telefonda söyler, sahne durumu
telefonda sorulur; dosyayı PC'de açmak, yeni tarif, dosya kaydetme, ayar ve dışa aktarma PC'de kalır),
`evals` (Değerlendirmeler; bakmak da vaka eklemek, silmek ve seti çalıştırmak da manage ister — köprüdeki
`evals.runs` / `.add` / `.delete` gibi; seti çalıştırmanın onayı telefonda sorulur, ilerleme ve sonuç
telefonda yazılır; A/B denemesi, dosya seçici ve rehber PC'de kalır), `capabilities` (Yetenek Matrisi;
okuma yetkisi açar — köprüdeki `toolbox.list` gibi; bir ajana kopyalamak manage ister — `toolbox.copy`,
`memory.copy`, `duties.add` gibi; kopyanın kaynağı ve CLAUDE.md sorusu telefonda sorulur; telefonda matris
kart içinde yana kayar, satır adları yerinde kalır; dosyayı PC'de açmak ve görev geçmişi PC'de kalır),
`airtable` (Airtable Motoru; bakmak da her iş de manage ister — açılış canlı örnekleri yoklar, köprüdeki
`airtable.instances` gibi; varsayılanlar, klasör çıkarma, MCP kaydı ve kaldırma, araç yoklaması ve başlatma
telefondan yapılır, kaldırma sorusu telefonda sorulur; klasör ekleme (PC'nin seçicisi), motorun web paneli,
klasör ve yapılandırma dosyası PC'de kalır), `analytics` (Görev Analitiği; bakmak manage ister — köprüdeki
`schedules.forecast` gibi, telefonda kendine ait işi yok; hesap başına harcama telefona hiç gönderilmez —
Hesaplar'ın verisi, telefonda admin; görev geçmişi, tam tahmin, Görevler'e dönüş ve Markdown dışa aktarma PC'de
kalır), `board` (pano; PC'deki gibi Görevler sayfasının Çalışma sekmesinde açılır ve Görevler'in kuralını aynen
taşır — bakmak read, görev işleri manage; Görevler'de PC'ye kalan her şey burada da PC'de kalır), `memgraph`
(Bellek Grafiği; `args.agentId` o ajanın belleğinde açar; bakmak da her iş de manage ister — açılışta dizin onarım
planı ve paylaşılan dosyalar da gelir, köprüdeki `memory.index.plan` / `memory.duplicates` gibi; yeni bellek, kopya,
MEMORY.md onarımı, filoya soru ve tek kaynaktan eşitleme telefondan yapılır, eşitlemenin üzerine yazma sorusu telefonda
sorulur; grafik telefonda gerçek boyutunda kalıp yana kayar; dosyayı açmak, damıtma, iki kopyayı karşılaştırma ve
birleştirme taslağı PC'de kalır), `chains` (röle zincirleri; `args.chain` o zincirde açar), `session` (bir oturumun transcript'i; sayfalardaki "transcript'i aç" tıklaması telefonda
  bu sayfaya `afnav` olur — `vsc_AgentFarm/media/afRemote.js`); listede olmayan sayfa `view.close`
  `unavailable` alır. Masaüstünün `afnav` `chains:<id>` biçimi (sayfa kimliğinde `:` olmaz) telefonda
  `afRemote.js` tarafından `to: 'chains'`, `args: { chain: <id> }` olarak gönderilir. Bir sayfanın PC'de
  açacağı seçim menüsü ya da onay sorusu telefonda o sayfanın kendi `afAsk` penceresiyle sorulur (X-447).

## 11. Ses — TalkScribe (ZEP)

Komut kutusundaki mikrofon: ZEP yüklüyse onun dikte Intent'i (`com.miropharm.talkscribe.ACTION_DICTATE`,
sonuç `ActivityResult` ile döner), değilse Android'in konuşma tanıma servisi. Kulaklık modunda tur özeti
ve sorular TTS ile okunur.

## 12. Güvenlik kalkanı

- **Yıkıcı eylemler** (silme, hard reset, hesap değişikliği) Agent Farm'ın şekil filtresinden geçer ve
  telefonda biyometrik onay ister.
- **Bütçe:** uzaktan kanal bütçesi Telegram ile ortaktır; aşılınca sistem durur ve açık onay ister.
- Yalnız eşleşmiş ve iptal edilmemiş cihazların imzası kabul edilir.
