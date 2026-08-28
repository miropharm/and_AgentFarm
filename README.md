# and_AgentFarm — Android Companion App for Agent Farm

**Agent Farm** ekosisteminin Android mobil komuta, canlı izleme ve uzaktan yönetim uygulaması (Android Companion & Remote Mirror Cockpit).

## Proje Bilgileri
- **Proje Adı:** `PRS.MuratVural_DEV_AND.AgentFarm`
- **Proje Kimliği (ID):** `prj_m8k4v2p9`
- **Obsidian Dokümantasyonu:** `00_AGENT_AREA/10_PROJECTS/PRS.MuratVural_DEV_AND.AgentFarm/`
- **Ana Masaüstü Projesi:** `PRS.MuratVural_DEV_VSC.AgentFarm` (`vsc_AgentFarm`)

## Temel Yetenekler & Mimari İlkeler
1. **Uzaktan Canlı İzleme & Komuta:** Masaüstü PC'deki Agent Farm WebSocket sunucusuna bağlanarak çalışan ajanları, açık oturumları ve streaming terminal çıktılarını telefondan gecikmesiz izleme.
2. **Çift Modlu Görünüm:** Kullanıcının ihtiyacına göre sadeleştirilmiş **Diyalog Modu** (sadece sohbet ve kararlar) veya detaylı **Terminal Modu** (araç çağrıları ve log akordeonu).
3. **Çift Yönlü Mobil Diyalog & Onay:** Ajan soru sorduğunda (`AskUserQuestion`) telefondan tek tıkla onaylama ve metin yazarak yeni turlar başlatma.
4. **QR Pairing & Güvenli Bağlantı:** PC ekranındaki dinamik QR kod ile tek saniyede şifreli eşleşme; mDNS ile yerel ağda sıfır konfigürasyon.
5. **Foreground Service & Push Bildirimleri:** Telefon kilitliyken bile tur bitişini ve onay taleplerini bildiren yerel bildirim servisi ve kilit ekranı butonları (`[Onayla / Durdur]`).
6. **TalkScribe (ZEP) Sinerjisi:** Sesli dikte ile ajana telefondan doğrudan Türkçe sesli komut verme ve Android TTS ile sesli geri bildirim alma.
7. **Hibrit Yerel Ajan:** PC kapalıyken bile telefon üzerinde doğrudan LLM API'leri (Gemini Flash 2.0 / Claude Haiku) ile hafif mobil sorgulamalar ve Obsidian Vault senkronizasyonu.

## 6 Fazlı Yol Haritası
- **Faz 1 — Remote Mirror MVP:** WebSocket Remote Bridge & Android Canlı Konsol/Komuta MVP'si
- **Faz 2 — Güvenli Eşleşme & Bildirimler:** QR Kod ile Eşleşme, Dış Ağ Tüneli, Native Push Bildirimleri & Dashboard
- **Faz 3 — Gelişmiş Mobil UX:** Çift modlu görünüm, çoklu oturum odak kilidi, mobil slash komutlar & Kill Switch
- **Faz 4 — TalkScribe ZEP & TTS:** TalkScribe sesli dikte entegrasyonu, TTS sesli asistan & hands-free araç modu
- **Faz 5 — Hibrit Mod & Mobil LLM:** Standby Hub, bağımsız mobil LLM motoru & Obsidian Vault senkronizasyonu
- **Faz 6 — Kararlılık & Dağıtım:** Offline replay dayanıklılığı, Doze modu pil optimizasyonu, imzalı APK/AAB paketi
