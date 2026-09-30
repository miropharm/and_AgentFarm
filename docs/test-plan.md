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
