# Test Planı

Çalışma yöntemi: vault `10_Notes/A-SOP - Android Otonom Geliştirme ve Test Çalışma Yöntemi - 260930`.
Her senaryo: ön koşul · eylem · beklenen · otomasyon · son sonuç.

| Kod | Senaryo | Ön koşul | Eylem | Beklenen | Otomasyon | Son sonuç |
|---|---|---|---|---|---|---|
| TC-001 | Açılış | Temiz kurulum | Uygulamayı aç | Çökmeden açılır, logcat'te FATAL EXCEPTION yok | emulator (her push), Test Lab Robo | — |
| TC-002 | Ana ekran içeriği | TC-001 | Ana ekrana bak | "Merhaba, Agent Farm" ve "Sürüm 0.0.1-b<N>" görünür | `MainActivityTest.greetingAndVersionAreShown` | — |
| TC-003 | Sürüm etiketi | — | `buildLabel("0.0.1-b7")` | "Sürüm 0.0.1-b7" | `GreetingTest` (birim) | — |
| TC-004 | Görsel: dikey / yatay / koyu | TC-001 | Ekran görüntüleri | Metin kesilmez, ortalanır, okunur; koyu temada koyu renkler | emulator ekran görüntüleri + `screen-state.txt` + görsel inceleme | b3: dikey GEÇTİ · yatay çekilemedi (betik döndürmedi) · koyu KALDI (uygulama koyu temayı dinlemiyordu) → ikisi düzeltildi, sonraki turda doğrulanacak |
