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
 Telefon: Android uygulaması (bu repo)
   yerel ekranlar (Şimdi, Konuşma, soru/izin, yeni oturum, Menü) ── köprü op'ları + canlı akış ──┐
   WebView (Menü'deki Agent Farm sayfaları + mobil katman) ── yerel taşıma ─────────────────────┤
   bağlantı yöneticisi · eşleşme · Keystore anahtarı ───────────────────────────────────────────┤── tek WebSocket
   ön plan servisi · bildirimler · ses · paylaşım · widget ─────────────────────────────────────┘
```

- **Ana işlerin ekranı telefonun, gerisi Agent Farm'ın** (B yolu, kullanıcı kararı 2026-10-16; tasarım:
  vault `10_Notes/A-PLN - Telefon Yeniden Tasarımı B Yolu - …- 261016`). Şimdi, Konuşma, soru/izin kartı,
  yeni oturum ve Menü Compose ekranlarıdır; diğer her sayfayı `vsc_AgentFarm/src/remote/` sunar ve
  Menü'den WebView çerçevesinde açılır. Yerel ekran yalnız çizer: veriyi köprü op'larından ve izlenen
  oturumun canlı akışından alır, PC'nin hiçbir kuralını yeniden yazmaz.
- **Kotlin'in payı** ana işlerin ekranları ile telefonun yerel yapabildiği ve web'in yapamadığı işlerdir:
  eşleşme, bağlantı, bildirim, ses, paylaşım, hızlı erişim, çevrimdışı giden kutusu, uygulama kilidi,
  telefondaki kopya ve parça parça yükleme.
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
  sırayla dener; kullanıcı yol seçmez, yalnız durum şeridinde görür. Agent Farm "Your network"
  (`lan`) kipinde tüm arayüzleri dinler; QR önce özel ağ adreslerini, sonra makinede Tailscale varsa
  onun 100.x adresini yazar — Tailscale'i olan telefon aynı eşleşmeyle evin dışından da ulaşır.
- **Keşif (`transport.discovery`):** `lan` kipinde Agent Farm yerel ağda mDNS (DNS-SD) ile
  `_agentfarm._tcp.local` olarak cevap verir: SRV portu, A özel IPv4 adreslerini, TXT `farm=<farm id>`
  ve `contract=<sürüm>` taşır (`vsc_AgentFarm/src/remote/server/mdns.ts`). Telefon yalnız bir çiftliğe
  yeni bir adresin çözebileceği bir nedenle ulaşamazken arar (`core/link/Discovery.wanted`,
  `net/FarmFinder`), kendi eşleştiği çiftliğin kimliğini görürse o adresi ilk sıraya koyar ve
  beklemeyi keser (`Reconnect.order`). PC'nin adresi değişse de yeni QR gerekmez; eşleşme ve sabitlenen
  sertifika aynı kalır.

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
| `welcome` | Agent Farm | Bağlantı kabul: yetki, özellik cevapları, son olay numarası, sayfaların dili (`lang`) |
| `refuse` | Agent Farm | Bağlantı reddi, nedeniyle; sürüm uyuşmazlığında gereken sürüm |
| `event` | Agent Farm | Numaralı olay (`seq`, `type`, `ts`, `data`); `user` kimin kaydı olduğunu söyler, alanı olmayan olay sahibinindir |
| `gap` | Agent Farm | İstenen olayların bir kısmı artık tutulmuyor: `from`–`to` arası kayıp |
| `call` | telefon | Bir köprü op'u çağırır: `id`, `op`, `args` |
| `result` | Agent Farm | Çağrının cevabı; aynı `id` tekrar gelirse saklı cevap + `duplicate` |
| `view.open` | telefon | Bir Agent Farm sayfasını açar (`view`, `page`) |
| `view.post` | Agent Farm | Sayfaya host mesajı |
| `view.msg` | telefon | Sayfadan host'a mesaj |
| `view.close` | ikisi de | Görünümü kapatır |
| `watch` | telefon | Bir Console oturumunu canlı izler (`watch` kimliği, `session`, `depth` `full` \| `summary`); `session` `"*"` + `summary` bütün kartlar |
| `unwatch` | ikisi de | İzlemeyi bitirir; Agent Farm'dan gelirse `reason`: `ended` (oturum gitti) ya da `unavailable` (izlenemez) |
| `live` | Agent Farm | İzlenen oturumun bir vuruşu (`watch`, `session`, `data`); numarasız, tekrar edilmez |
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
| `gate.opened` | Bir iş akışının yöneticisi bir insanı bekliyor (insan kapısı): `workflows.answer { id: gateId, option (1'den) \| answer }` ile cevaplanır, ilk cevap kazanır |
| `gate.closed` | Kapı cevaplandı, nerede olursa olsun; uyarısı her telefondan kalkar. Pencere yeniden yüklenince açık kapı bu olay olmadan düşer: cevap `ok:false` döner, bildirim düğmeye basınca zaten kalkar |
| `mission.ended` | Üst düzey bir görev (mission) bitti; `outcome` `done` ya da `failed` |
| `farm.language` | Çiftliğin dil ayarı değişti; sayfalar artık `lang` dilinde çiziliyor (uyarı değildir, telefonu uyandırmaz) |

## 8. Kurallar

- Telefon tanımadığı olay türünü yok sayar; Agent Farm tanımadığı çerçeveyi reddeder (`call` için `ok:false`).
- **Tam bir kez:** her `call` telefonun ürettiği bir `id` taşır; aynı `id` ikinci kez gelirse op yeniden
  çalışmaz, saklı cevap `duplicate:true` ile döner. Çevrimdışı giden kutusu bu yüzden güvenle yeniden gönderir.
- **Okuma giden kutusuna girmez.** Ekranların okuduğu op'lar (`console.sessions`, `sessions.recent`,
  `needs.list`, `sessions.read`) yalnız bağlıyken açık sokete gider (`ConnectionManager.sendRead`,
  `net/CallAwait.kt` `read`); cevapsız kalan bir okuma yeniden bağlanınca bir daha gönderilmez. Cevaplar ve
  eylemler (bir sorunun cevabı, izin, gönderilen mesaj) giden kutusundan, tam bir kez gider.
- **Şimdi: önce kopya, sonra tazesi, sonra sayfalar.** Açılışta telefondaki kopya (çiftliğin son üç cevabı:
  kartlar, geçmişin ilk sayfası, Needs; `net/NowStore`, çiftlik unutulunca silinir) aynı kurucuyla
  (`core/now/NowBuild`) çizilir. Çiftlik bağlanınca `console.sessions { view: "summary" }`,
  `sessions.recent { view: "page" }` ve `needs.list` okunur; ekran açıkken `watch "*" summary` kartları canlı
  tutar (kartın sürmekte olan adımı ve tur başlangıcı yalnız canlı vuruştan gelir, tazelemede korunur). Liste
  sonuna gelince `cursor` ile sonraki 20 satır istenir. Yeniden bağlanınca yalnız ilk sayfa okunur: üste
  çıkan satır yerini alır, kaydırılıp okunmuş satırlar imleçleriyle kalır. `needs.changed` gelince üçü yeniden
  okunur; bir kartın turu bitince geçmişin ilk sayfası. "Seni bekliyor": soran kart (`asking`) ve kart dışındaki
  her Needs satırı; izin satırdan yalnız çiftliğin Needs anahtarıyla (`needs.act`), soru kartın kendi
  seçenekleriyle (`console.answer`) cevaplanır — bildirim düğmeleriyle aynı kurucu (`core/session/Answers`).
- **Giden kutusu diskte durur, altı saatten eski çağrı atılır:** süreç ölse de bekleyen çağrı ilk `welcome`
  sonrasında gider; bekleyen bir soruya saatler sonra düşen cevap yeni bir istem gibi okunabileceği için
  `createdAt` üzerinden altı saati geçen çağrı gönderilmez (`core/state/Outbox.kt`).
- **Bağlantı yokken paylaşım:** paylaşım ekranı cevap vermeyen bir çiftliği de sunar, yeter ki oturumları daha
  önce okunmuş olsun (`console.sessions` her okunduğunda `FarmStatus.sessions` güncellenir). Gönderilen metin
  giden kutusuna `receipt` (kime gittiği) ile girer; çiftlik cevap verince ya da altı saat dolunca sonucu bir
  bildirimle söylenir (`Share.receipt`: teslim edildi · reddedildi · kapıda durdu · süresi doldu).
- **Bekleyen sayısı 0'a inince** (`needs.changed`) o çiftliğin soru/izin bildirimleri, `session.ended` gelince
  o oturumunkiler telefondan kalkar.
- **Olay numaraları yalnız artar.** `resumeAfter` sonrası olaylar verilir; tutulmayanlar için önce bir `gap`.
  Konumu olmayan telefon (ilk bağlantı) ilk `welcome`'ın `lastSeq`'inden başlar; böylece henüz hiç olay
  gelmeden kopan bağlantı, kopukken kaçanları da ister (`ShellReducer.onFrame`).
- Sürüm uyuşmazlığı: `refuse` nedeni `contract`, gereken sürümle (`minContract`).
- **Gizli değerler hiçbir çerçevede gezmez**; yalnız var olup olmadıkları.
- **`spoken { text, lang }`** (`ask.opened`, `ask.answersSoon`, `permission.opened`, `turn.finished`,
  `notice.posted`): Agent Farm'ın kendi okuyucusunun o olay için söyleyeceği metin, sese göre temizlenmiş, ve
  dilinin BCP-47 ana etiketi. İsteğe bağlıdır; yoksa telefon kendi okumasına döner.
- **Canlı kanal (`watch` / `live` / `unwatch`).** Şimdi ya da Konuşma açıkken telefon izlediğini söyler; ekran
  kapanınca `unwatch` yollar, uygulama arka plandayken hiçbir şey izlenmez. `data`, Console kartının kendi
  hali: `agent`, `status` (`starting` · `idle` · `working` · `ended` · `error`), `turnSince` (yalnız tur
  sürerken), `cost`, `steerable`, `step` (sürmekte olan çağrı `{ tool, detail, since }`, iki çağrı arasında
  `null`), `asking` (`console.sessions`'ın `asking`'iyle aynı) ve yalnız `full`'da `messages`: son vuruştan beri
  yeni ya da büyümüş satırlar, `sessions.read`'in sözcükleriyle (`key`, `turn`, `role`, `ts`, `text?`, `tool?`,
  `input?`, `isError?`, `clipped?`). Vuruş `full`'da en çok 250 ms'de, `summary`'de saniyede bir; ekranın
  çizdiği bir şey değişmediyse hiç gelmez. İzlemenin ilk karesi satır taşımaz: o ana kadarı `sessions.read`
  ile okunur. Canlı satır **geçicidir**: son okunan satırın arkasına eklenir ve tur bitince (ya da yeniden
  bağlanınca) son onaylı `i`'den yapılan `sessions.read` onu yerine koyar (`core/live/LiveMerge.kt`; aynı
  `key`'li ya da son okunan satırdan eski geçici satır düşer, yenisi kalır). Soket düşünce izlemeler biter;
  telefon her yeni soketin `welcome`'unda açık izlemelerini yeniden yollar ve ekrana "yeniden oku" der; izlemenin
  zaten sorulduğu soketin `welcome`'u (ekran bağlantı kurulurken açıldıysa) yeniden bağlanma sayılmaz (`net/LiveWatch.kt`).
  İzleme kimliğini telefon rastgele üretir: aynı cihazın eski soketi kapanırken yeni izlemeyi silemez.
- **Yerel ekranların okuduğu op cevapları sözleşmededir (`ops`).** `console.sessions`, `sessions.read`,
  `sessions.recent` ve `needs.list` için sözleşme telefonun okuduğu alanları gerçek bir örnekle bildirir:
  `answer` (`array` ya da `object`), `fields` (nesne cevabın kendi alanları), `list` (satırları taşıyan alan),
  `row`. Agent Farm fazlasını verebilir, eksiğini ya da başka adla vermez; vsc bunu cevabı kuran koda karşı
  sınar. `console.sessions { view: "summary" }` yalnız bildirilen satır alanlarını verir. `pages.list`
  telefonun yerel Menü'sünün listesidir (`{ pages: [{ id, label, group }] }`, grup `work` · `info` ·
  `settings` · `more`): listeyi PC tutar (vsc `core/phoneMenu.ts`) ve PC'nin sunmadığı sayfayı vermez;
  telefon onu çizer, son kopyasını saklar ve satırı o sayfa olarak açar (`view.open`).
  `sessions.recent { view: "page", cursor?, limit? }` → `{ rows, next }`: son etkinliği en yeni önce, `cursor`'dan
  sonra (imleç bir satırı adlar — etkinlik zamanı ve kimliği; üste yeni oturum düşse de sayfa kaymaz);
  `next` sonraki sayfanın imleci, son sayfada `null`; `limit` en çok 50, varsayılan 20. Satır hafiftir: `id`,
  `agent`, `title`, `engine`, `lastTs`, `origin`, `status` (Console kartının ya da canlı izleyicinin dediği,
  bilen yoksa `null`), `waiting` (oturumda kullanıcıyı bekleyen bir şey var mı) ve `lastLine?` (ajanın son
  söylediğinin ilk satırı).

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
- **Bildirim düğmeleri** (`core/notify/Alerts`): soru → seçenekler (1-3) ya da yazılı cevap; izin → Allow
  (kilit açtırır) ve Deny; ikisinde de yer kaldıkça **Stop** (`console.interrupt`, oturum kalır, kilit açtırır;
  Android en çok üç düğme gösterir). Kritik hata ayrı bir olay değildir: çiftlik onu seviye 3-4 notice olarak
  yollar ve yüksek öncelikli "Important notices" kanalına düşer.
- **Wake-on-LAN:** `welcome.wake` çiftliğin yerel ağdaki kartlarını (`mac`, `broadcast`) taşır; telefon son
  değeri saklar (`ShellPrefs`, çiftlik unutulunca silinir). Çiftliğe ulaşılamazken bekleme ekranı
  "Wake this PC" gösterir: sihirli paket (6×FF + 16×MAC) UDP 9'a kartın yayın adresine ve 255.255.255.255'e
  gider, ardından bağlantı geri çekilme beklemeden birkaç kez dener (`Reconnect.AFTER_WAKE_MS`).
- **Dil:** `welcome.lang` çiftliğin sayfalarını çizdiği dili taşır (`agentFarm.language`'ın çözülmüş hali; `auto`
  ise VS Code'un görüntü dili). Telefon her çiftliğin son söylediği dili ve o dilin ne zaman değiştiğini saklar
  (`ShellPrefs`, çiftlik unutulunca silinir); uygulama, söyleyişi en son değişen eşli çiftliğin dilinde çizer,
  hiçbiri söylemediyse telefonun dilinde (`core/lang/AppLanguage`). Alanı göndermeyen eski bir çiftlik ya da
  VS Code kapalıyken cevap veren başsız servis bir şey değiştirmez. Ayar telefon bağlıyken değişirse
  `farm.language { lang }` olayı hemen gelir; yeniden bağlanınca tekrar oynatılan eski bir dil olayı
  (`seq` ≤ `welcome.lastSeq`) uygulanmaz, çünkü `welcome.lang` zaten sonuncusunu söyler (`net/ConnectionManager.kt`).
- **Push ile uyandırma (FCM):** telefon Firebase Cloud Messaging'den (proje `agent-farm-f0701`) bir kayıt
  jetonu alır (`net/PushWakeService`, `ShellPrefs.pushToken`); `welcome.features` `remote.wake`'i listeleyen her
  çiftliğe her welcome'dan sonra, jeton yenilenince de hemen `call remote.wake.register { token }` ile verir
  (çiftlik o an gönderemiyor olsa da saklar). Çiftliğin veri mesajı içeriksizdir: `{ wake: "1", farm: <id> }`;
  eşleşmiş bir çiftliği adlandırırsa (`core/link/PushWake.farmOf`) ön plan servisi kalkar ve o çiftliğin bağlantısı
  geri çekilmeyi beklemeden dener; kaçırılanlar kendi kanalından `hello.resumeAfter` ile gelir, bildirimi yerel kod
  kurar. Firebase anahtarı repoda değildir: CI onu Firebase projesinin Android uygulama yapılandırmasından okur
  (`scripts/ci/firebase-key.sh`) ve `BuildConfig.FIREBASE_API_KEY` olur; anahtarsız bir derlemede ya da Google Play
  hizmetleri olmayan telefonda push uyandırması yoktur, bağlantı servisi her zamanki gibi çalışır.
- **Boştayken dinlenme (pil):** push ile uyandırabilen bir çiftliğin soketi yalnız çiftliğin push'lamayacağı bir
  şey olabilecekken açık tutulur (`core/power/LinkRest.hold`): uygulama ekrandayken, giden kutusunda o çiftliğe
  bir çağrı varken, kullanıcıyı bekleyen bir şey (`needs`) varken, çalışan tur sayısı bilinmezken ya da sıfırdan
  büyükken. Çiftlik `remote.wake` listelemiyorsa ya da telefonun jetonu yoksa soket hep açıktır. Hiçbiri yoksa
  bağlı ya da ulaşılamayan bağlantı `GRACE_MS` (2 dk) sonra kapanır ve `Link.Resting` olur: çok yayınlı arama ve
  Wake-on-LAN önerisi ona açılmaz, durum satırı "Resting" der. Dinlenen bağlantı push uyandırmasıyla, uygulama
  öne gelince, giden kutusuna bir çağrı düşünce ya da `PEEK_EVERY_MS`'de (15 dk) bir yoklama için açılır;
  yoklama bir şey bulmazsa `PEEK_GRACE_MS` (20 sn) sonra yeniden dinlenir. Kararı `net/LinkRestKeeper` bağlantı
  servisinde her durum değişikliğinde ve kararın adlandırdığı anda uygular; soketi `FarmConnection.rest` kapatır,
  `retryNow` açar. Dinlenme kapanışı çiftlik için temiz bir `close 1000 "resting"`dir: soketsiz telefon artık
  uyarı gerektiren olaylarda push ile uyandırılır.
- **Yerel "yeni oturum"** (paylaşım, ses, widget) Telegram `/new` ile aynı kararı kullanır ve son ayarları
  devralır (`seedLastLaunch`). Köprüdeki `sessions.start` devralmadığı için kullanılmaz. Kapısı
  `console.new { agent, prompt?, attach? }`: argümansız çağrılınca açılabilecek ajanları (`agents`) verir;
  telefondan gelince Telegram'ın günlük bütçesine sayılır, bütçe bitmişse `ok:false` ile reddeder. Açınca
  `{ id, agent, note }` döner: Şimdi'nin paneli o `id`'nin kartını Konuşma'da açar (döküm kimliğini motor
  adlandırınca Konuşma `console.sessions` özetinden sorar); Geri Şimdi'dir.
- **Paylaşılan görsel** önce `console.stage { name, data (base64), mediaType }` ile PC'nin geçici ek klasörüne
  gider, dönen `path` mesajın `attach` listesine konur (`console.send` ya da `console.new`). Telefon yalnız
  kendi yüklediği (o klasördeki) yolları ekleyebilir; görsel en çok 5 MB, meşgul bir tura bekletilemez.
  Görsel yalnız o an cevap veren çiftliğe gider; çevrimdışı giden kutusu yalnız metin taşır.
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
  `afSpeak` (`token`, `paragraphs[]`, `lang`, `rate`) sayfanın metnini telefonun kendi sesiyle okur
  (`core/speech/PageReading`, `net/Speaker`). Paragrafları çiftlik hazırlar: sayfada 🔊'e basılınca
  `media/afPhoneRead.js` `readFor` ile ister, çiftlik `readText` ile PC'nin okuyacağı biçimde döner (aynı
  temizleme, araya giren sözcükler, metnin kendi dili) ve PC hiç konuşmaz. Ses: metnin dili için telefonda ses
  varsa o, yoksa kullanıcının ses dili; ikisi de yoksa okuma nedenini söyleyerek biter. Uyarılarla aynı motor:
  yeni okuma eskisini keser, araya giren bir soru uyarısı okumayı bitirir; okuma medya sesi olarak çıkar, ses
  odağını tutar, arama gelince duraklar; sayfadan çıkılınca biter. `afSpeakControl` (`action`: stop · pause ·
  resume · toggle · next · prev · rate, `rate?`) okumayı yönetir. Her değişiklikte sayfaya `afSpeakState`
  (`token`, `state`: reading · paused · idle, `paragraph`, `count`, `rate`, `error?`) döner; sayfa bunu
  kendi okuma durumu yapar (🔊 / ■ / ▶ ve sekme çubuğunun üstündeki küçük oynatıcı). Sayfa 🔊'ü yalnız
  `afShell.speaks()` doğru dönerse çizer.
  `afSave` (`token`, `name`, `mime`, `data` = base64) çiftliğin yaptığı bir dosyayı (bir dışa aktarma) telefonda
  kişinin seçtiği yere yazar: sistemin kaydetme seçicisi (`ACTION_CREATE_DOCUMENT`, önerilen ad `name`) açılır,
  baytlar ana iş parçacığı dışında yazılır (`ui/page/PageSave`) ve sayfaya `afSaveDone` (`token`, `ok`,
  `name?` = sağlayıcının verdiği ad, `error?`) döner; seçici kapatılırsa ne `name` ne `error` vardır. Sayfa
  (`media/afAsk.js`) `afSave`'i yalnız `afShell.saves()` doğru dönerse gönderir; dönmezse çiftliğe "uygulamayı
  güncelleyin" der. Çiftlik bunu bir sayfanın `afAsk` `kind:"save"` sorusundan üretir (`PanelAsk.save`); bir
  mesajda 8 MB'tan büyük dosya telefona hiç gönderilmez.
  Diğer bütün sayfa mesajları `view.msg` olarak gider.
- İçeriği istenen bir dosya (Console'da 📎, ayar seti ya da iş akışı içe aktarma) telefonda telefonun kendi
  dosyalarından seçilir: çiftliğin `afAsk` `kind:"files"` sorusu sayfada bir alt sayfa açar, düğmesi sayfanın kendi
  `<input type=file>`'ıdır ve kabuk onu sistemin seçicisiyle karşılar (`ui/page/PageChrome`). Seçilenler base64
  olarak `afAskAnswer` ile çiftliğe gider (toplam 8 MB) ve PC onları ek hazırlama klasörüne kopyalar; PC'deki bir
  yol (bir ayarın klasörü) telefondan seçilemez.
- Çiftlik tarafında sayfa başına bir kural vardır (`vsc_AgentFarm/src/panels/remotePages.ts`): bir
  mesaj ya yalnız PC'de çalışır (telefondan hiç çalışmaz), ya bakar (`read`), ya cevaplar
  (`answer`), ya da sayfanın işlem yetkisini ister. Sesli okuma (`readAloud`, `readControl`) hiçbir sayfada
  telefondan çalışmaz (telefon PC'yi konuşturmaz); `readFor` her sayfada bakıştır. Bugün telefonda açılan sayfalar: `now`,
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
birleştirme taslağı PC'de kalır), `selection` (iş akışının seçim adımı; `args.id` o açık seçimi, yoksa ilk sorulanı
açar, hiç açık seçim yoksa "kullanılamıyor"; bakmak da tutmak/iptal de manage ister — köprüdeki `workflows.select`
gibi; aday görseller görünüme özel `/res/~/` adresleriyle gelir; telefondan gelen seçim adımın kuralından geçer, ret
nedeni telefonda yazılır; görünümden çıkmak iptal etmez; başka kapıdan cevaplanınca sayfa `answered` alır ve düğmeleri
kapatır), `chains` (röle zincirleri; `args.chain` o zincirde açar), `session` (bir oturumun transcript'i; sayfalardaki "transcript'i aç" tıklaması telefonda
  bu sayfaya `afnav` olur — `vsc_AgentFarm/media/afRemote.js`); listede olmayan sayfa `view.close`
  `unavailable` alır. Masaüstünün `afnav` `chains:<id>` biçimi (sayfa kimliğinde `:` olmaz) telefonda
  `afRemote.js` tarafından `to: 'chains'`, `args: { chain: <id> }` olarak gönderilir. Bir sayfanın PC'de
  açacağı seçim menüsü ya da onay sorusu telefonda o sayfanın kendi `afAsk` penceresiyle sorulur (X-447).

## 11. Ses — TalkScribe (ZEP)

Komut kutusundaki mikrofon: ZEP yüklüyse onun dikte Intent'i (`com.miropharm.talkscribe.ACTION_DICTATE`,
sonuç `ActivityResult` ile döner), değilse Android'in konuşma tanıma servisi. Kulaklık modunda tur özeti
ve sorular TTS ile okunur.

**Kulaklık modu (S-3; tasarım: vault `10_Notes/A-PLN - Kulaklık Modu (S-3) - … - 261018`).** Ayarlar'daki
anahtar açıkken (`ShellPrefs.handsFree`) ve `RECORD_AUDIO` verilmişken bağlantı servisi ön plana mikrofon
türüyle (`remoteMessaging|microphone`) gelir — yalnız uygulama öndeyken: Android arka plandan (örneğin push
uyandırmasıyla) başlatılan mikrofonlu servisi reddeder; reddedilirse servis mikrofonsuz sürer
(`core/handsfree/HandsFree.asksMicrophone`). Kulaklık yoksa mod bekler (`net/Headset`); dinleme ve sesle
cevap sonraki alt kalemlerdedir (S-8…S-11).

## 12. Güvenlik kalkanı

- **Yıkıcı eylemler** (silme, hard reset, hesap değişikliği) Agent Farm'ın şekil filtresinden geçer ve
  telefonda biyometrik onay ister.
- **Bütçe:** uzaktan kanal bütçesi Telegram ile ortaktır; aşılınca sistem durur ve açık onay ister.
- Yalnız eşleşmiş ve iptal edilmemiş cihazların imzası kabul edilir.
