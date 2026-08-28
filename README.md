# and_AgentFarm — Android Companion App for Agent Farm

**Agent Farm** ekosisteminin Android mobil komuta, canlı izleme ve uzaktan yönetim uygulaması (Android Companion & Remote Mirror Cockpit).

## Proje Bilgileri
- **Proje Adı:** `PRS.MuratVural_DEV_AND.AgentFarm`
- **Proje Kimliği (ID):** `prj_m8k4v2p9`
- **Obsidian Dokümantasyonu:** `00_AGENT_AREA/10_PROJECTS/PRS.MuratVural_DEV_AND.AgentFarm/`
- **Ana Masaüstü Projesi:** `PRS.MuratVural_DEV_VSC.AgentFarm` (`vsc_AgentFarm`)

## Temel Yetenekler
1. **Uzaktan Canlı İzleme & Komuta:** PC'deki Agent Farm WebSocket sunucusuna bağlanarak çalışan ajanları, açık oturumları ve streaming terminal çıktılarını telefondan izleme.
2. **Çift Yönlü Mobil Diyalog:** Ajan soru sorduğunda (`AskUserQuestion`) telefondan tek tıkla onaylama ve metin yazarak yeni turlar başlatma.
3. **QR Pairing & Güvenli Bağlantı:** PC ekranındaki QR kod ile tek saniyede şifreli eşleşme.
4. **Push Bildirimleri:** Telefon kilitliyken bile tur bitişini ve onay taleplerini bildiren yerel bildirim servisi.
5. **TalkScribe (ZEP) Sinerjisi:** Sesli dikte ile ajana telefondan sesli komut verme.
6. **Hibrit Yerel Ajan:** PC kapalıyken bile telefon üzerinde doğrudan LLM API'leri (Gemini Flash / Claude Haiku) ile hafif mobil sorgulamalar yapabilme.

## Yol Haritası (Fazlar)
- **Faz 1:** WebSocket Remote Bridge & Android Canlı Konsol/Komuta MVP'si
- **Faz 2:** QR Kod ile Eşleşme, Dış Ağ Tüneli & Native Push Bildirimleri
- **Faz 3:** TalkScribe ZEP Ses Entegrasyonu & Hibrit Yerel LLM API Motoru
