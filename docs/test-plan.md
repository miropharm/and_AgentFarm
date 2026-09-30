# Test Planı

Çalışma yöntemi: vault `10_Notes/A-SOP - Android Otonom Geliştirme ve Test Çalışma Yöntemi - 260930`.
Her senaryo: ön koşul · eylem · beklenen · otomasyon · son sonuç.

| Kod | Senaryo | Ön koşul | Eylem | Beklenen | Otomasyon | Son sonuç |
|---|---|---|---|---|---|---|
| TC-001 | Açılış | Temiz kurulum | Uygulamayı aç | Çökmeden açılır, logcat'te FATAL EXCEPTION yok | emulator (her push), Test Lab Robo | b10: GEÇTİ (core/ui ayrımı sonrası) |
| TC-002 | Ana ekran içeriği | TC-001 | Ana ekrana bak | "Merhaba, Agent Farm" ve "Sürüm 0.1.0-b<N>" görünür | `MainActivityTest.greetingAndVersionAreShown` | b10: GEÇTİ ("Sürüm 0.1.0-b10") |
| TC-003 | Sürüm etiketi | — | `buildLabel("0.1.0-b7")` | "Sürüm 0.1.0-b7" | `core/GreetingTest` (birim) | b10: GEÇTİ |
| TC-004 | Görsel: dikey / yatay / koyu | TC-001 | Ekran görüntüleri | Metin kesilmez, ortalanır, okunur; koyu temada koyu renkler | emulator ekran görüntüleri + `screen-state.txt` + görsel inceleme | b3: dikey GEÇTİ · yatay çekilemedi (betik döndürmedi) · koyu KALDI (uygulama koyu temayı dinlemiyordu) → ikisi düzeltildi · b6: yatay GEÇTİ (döndürme `screen-state.txt` ile doğrulandı) · koyu GEÇTİ; not: koyu temada durum çubuğu gri kalıyor, asıl uygulamanın tema çalışmasında ele alınacak |
| TC-005 | Robo keşfi | Temiz kurulum | Test Lab Robo uygulamayı kendi gezer | Çökme yok | Test Lab `[testlab]` | b4: GEÇTİ (MediumPhone.arm, Android 14) |
| TC-006 | Telefona teslim | `dev` grubu var | `[deliver]` | Sürüm `dev` grubuna gider | App Distribution | b4, b6, b7: KALDI (404; projedeki tek grup "Custom", "dev" değil) → b8, b9, b10: GEÇTİ (grup listeden çözülüyor, tek grup varsa ona gidiyor; b10'un sürüm notu CHANGELOG 0.1.0'dan) |
| TC-007 | Eşleşme (uçtan uca) | CI sahte çiftliği (TLS, `pairUri`) | Bağlantıdaki kodla eşleş | Keystore anahtarı oluşur, çiftlik cevabı kaydedilir; imzalı `hello` `welcome` alır | `PairingE2ETest` (4) | b14: GEÇTİ |
| TC-008 | Eşleşme reddi | TC-007 | Başka parmak izi / kullanılmış kod | "sertifika uyuşmuyor" / "kod kullanılmış" cümlesi; anahtar kalmaz | `PairingE2ETest` | b14: GEÇTİ |
| TC-009 | Eşleşme ekranı | TC-007 | Okutulan bağlantı uygulamayı açar, "Eşleş"e bas | "… ile eşlendi", çiftlik satırı görünür; yabancı bağlantıda "eşleşme bağlantısı değil" | `PairingScreenTest` (2) | b14: GEÇTİ |
| TC-010 | Bağlantı yöneticisi | TC-007 | Bağlan, olay gönder, çağrı yap; soketi düşür; cihazı iptal et | Bağlı olur, olay numarası ilerler, giden kutusu boşalır; düşünce kaçan olayla geri gelir; iptalde "Reddedildi" ve yeniden denemez | `ConnectionE2ETest` (3) | b16: GEÇTİ |
| TC-011 | Durum şeridi | TC-010 | Ana ekrana bak | Çiftlik satırında "Bağlı · 10.0.2.2:8743" | emulator ekran görüntüsü | b16: GEÇTİ (görüntüyü emulatorün kendi "Pixel Launcher yanıt vermiyor" penceresi örttü → hata pencereleri kapatıldı, odak penceresi `screen-state.txt`'e yazılıyor) |
| TC-012 | Çiftlik sayfası (WebView) | TC-010 | Çiftlik satırına dokun | Sayfa HTML'i, `afRemote.js` ve `common.css` yerel kesiciden gelir; `view.open` cevabı sayfaya ulaşır; sayfanın mesajı çiftliğe gidip yankı olarak döner | `FarmPageTest` | b17: bekliyor |
