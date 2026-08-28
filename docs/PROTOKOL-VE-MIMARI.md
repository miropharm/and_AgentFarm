# Agent Farm Android Companion — Protokol, Veri Sözleşmesi ve Ekran Mimarisi

Bu döküman, Masaüstü VS Code Eklentisi (`vsc_AgentFarm`) ile Android Uygulaması (`and_AgentFarm`) arasındaki iletişim protokolünü, JSON-RPC olay ve komut şemasını, ekran navigasyonunu ve TalkScribe ZEP entegrasyon modelini tanımlar.

---

## 1. Ağ İletişim Protokolü ve JSON-RPC Şeması

Tüm iletişim WebSocket (port 8378 veya güvenli TLS tüneli) üzerinden çift yönlü JSON-RPC 2.0 tabanlı mesajlaşma ile gerçekleşir.

### A. Sunucudan Telefona Akan Canlı Olaylar (Downstream Events)

| Olay Adı (`event`) | Açıklama | Örnek Payload |
| :--- | :--- | :--- |
| `session.list` | Açık oturumların tam anlık görüntüsü | `{"sessions": [{"id": "s1", "agent": "developer", "state": "running", "turns": 3, "cost": 0.04}]}` |
| `turn.started` | Ajan yeni bir tura başladığında | `{"sessionId": "s1", "turnIndex": 4, "prompt": "Testleri çalıştır", "startedAt": 1787893200}` |
| `stream.chunk` | Terminal çıktısının canlı akan metin parçası | `{"sessionId": "s1", "delta": "Running tests...\n", "type": "stdout"}` |
| `tool.invoked` | Ajan bir aracı çağırdığında (Terminal modunda akordeon) | `{"sessionId": "s1", "tool": "run_command", "args": {"CommandLine": "npm test"}}` |
| `tool.result` | Aracın çalışma sonucu döndüğünde | `{"sessionId": "s1", "tool": "run_command", "status": "done", "durationMs": 1200}` |
| `ask.question` | Ajan onay veya çoktan seçmeli soru sorduğunda | `{"sessionId": "s1", "questionId": "q1", "text": "Hangi modelle devam edilsin?", "options": ["Haiku", "Sonnet"]}` |
| `turn.finished` | Tur tamamlandığında özet ve metrikler | `{"sessionId": "s1", "turnIndex": 4, "summary": "14 test geçti", "cost": 0.03, "durationSec": 28}` |
| `metrics.updated`| Filo kotaları ve bütçe güncellendiğinde | `{"todayCost": 0.42, "claudeQuotaPct": 68, "codexQuotaPct": 40}` |

---

### B. Telefondan Sunucuya Gönderilen Komutlar (Upstream Commands)

| Komut (`method`) | Açıklama | Örnek Parametreler |
| :--- | :--- | :--- |
| `session.send` | Ajan oturumuna yeni tur promptu gönderme | `{"sessionId": "s1", "prompt": "Şimdi build al"}` |
| `session.cancel` | Çalışan turu durdurma (Interrupt) | `{"sessionId": "s1"}` |
| `question.answer`| Soruya veya onay talebine yanıt verme | `{"sessionId": "s1", "questionId": "q1", "choice": "Haiku"}` |
| `task.create` | Sıfırdan yeni görev oluşturma | `{"agent": "developer", "project": "PRS.AgentFarm", "prompt": "..."}` |
| `sync.replay` | Bağlantı kopması sonrası kaçan olayları isteme | `{"lastEventId": 4820}` |

---

## 2. Android Ekran Mimarisi ve Navigasyon Haritası

Jetpack Compose tabanlı 5 ana ekran sekmesi ve 2 alt modal akışı:

```
[MainActivity (BottomNavigationBar)]
  ├── 📱 1. Filo & Oturumlar (Fleet Hub)
  │      └── Canlı Oturum Kartları, Çalışan Ajanlar, Hızlı Durum
  ├── 💬 2. Canlı Konsol & Diyalog (Live Console Screen)
  │      ├── Üst: Oturum Geçiş Şeridi [Developer 🟢] [Basit 🟡]
  │      ├── Orta: Çift Modlu Akış (Diyalog Sohbeti / Terminal Logları)
  │      └── Alt: Çok Satırlı Komuta Kutusu + ZEP Mikrofon Butonu + Slash Seçici
  ├── 📋 3. Görevler & Kuyruk (Tasks & Queue)
  │      └── Bekleyen Görevler, Yeniden Sıralama, Yeni Görev Formu
  ├── 📊 4. Metrikler & Kotalar (Metrics & Quotas)
  │      └── Harcama Grafikleri, Kalan Kotalar, Bütçe Çubukları
  └── ⚙️ 5. Ayarlar & Bağlantı (Settings & Pairing)
         └── QR Tarayıcı, mDNS Keşif Listesi, TTS & Bildirim Sesleri

[Modallar / Bottom Sheets]
  ├── 🚨 AskUserQuestion & İzin Yanıtlayıcı (Alt Çekmece)
  └── ⚡ Hızlı Slash & Snippet Seçici
```

---

## 3. TalkScribe (ZEP) Entegrasyon Modeli

Agent Farm ile ZEP aynı Android cihazda şu iki yöntemle iletişim kurar:

1. **Doğrudan Intent / Contract Modu (Hafif):**
   * Komut kutusundaki mikrofona basıldığında `com.miropharm.talkscribe.ACTION_DICTATE` Intent'i çağrılır.
   * ZEP arka planda sesi çözer ve transkripsiyonu `ActivityResult` olarak Agent Farm'a döndürür.
2. **Yerel AIDL / Bound Service Modu (Canlı Streaming):**
   * ZEP'in `ITranscriptionService` servisine bağlanarak kullanıcı konuştukça kelime kelime komut kutusuna canlı dökülür.

---

## 4. Mobil Güvenlik ve Yıkıcı Eylem Kalkanı

1. **Kritik Komut Kalkanı:** Dosya silme (`rm`, `del`), git hard reset (`git reset --hard`) veya formatlama içeren araç çağrılarında telefon ekranında kırmızı uyarı modalı çıkar (`"Kritik Dosya Değişikliği: Onaylıyor musunuz?"`).
2. **Mobil Bütçe Limiti:** Telefondan tetiklenen işlemlerin günlük maliyeti `$1.00`'ı aştığında sistem duraklar ve telefondan açık onay ister.
3. **Cihaz Yetkilendirme (Allowed Devices):** Sadece QR kod ile eşleşmiş kayıtlı `DeviceId`'lerden gelen komutlar kabul edilir; yabancı cihazlar sessizce reddedilir.
