---
inclusion: always
---

# Git Context Protocol

Kullanıcı aşağıdaki ifadeleri (veya aynı anlama gelen kısa varyasyonları) kullandığında **terminal yetkisini kullanarak** ilgili `git` komutlarını çalıştır, çıktıyı oku ve bağlamı buna göre güncelle. Kullanıcıya sadece “şunu çalıştır” deme; komutları kendin çalıştır.

## `/context-stat`

- Çalıştır: `git --no-pager diff HEAD~1 --stat`
- Çıktıdan hangi dosyaların değiştiğini özetle (eklenen/silinen satır istatistikleri varsa kısaca belirt).

## `/context-show`

- Çalıştır: `git --no-pager show HEAD`
- Commit mesajını ve yapılan tüm değişiklikleri incele; gerekirse dosya bazında kısa özet ver.

## `/read-file [dosya_yolu]`

- `dosya_yolu`: repoya göre **relative path** (ör. `app/src/main/...`). Windows’ta bile `git show` için tercihen `/` kullan; gerekirse yolu normalize et.
- Çalıştır: `git --no-pager show "HEAD:dosya_yolu"` (yolda boşluk veya özel karakter varsa uygun şekilde kaçır/quote et).
- Çıktıyı o commit’teki dosya içeriği olarak kullan.

## `/read-commit-message`

- Çalıştır: `git log -1 --pretty=%B`
- Commit mesajını incele ve değişiklik yapılmış dosyaların muhmetel değişikliklerini öğren.

## Tamamlandıktan sonra

Komut(lar) çalıştırılıp çıktı işlendikten sonra şu formatta geri bildirim ver:

**Bağlam başarıyla alındı, [X] dosyalarındaki değişiklikleri anladım**

- `[X]`: mümkünse sayı (ör. `diff --stat` veya `show` çıktısından değişen dosya sayısı). Tek dosya okunduysa `1` veya bağlama uygun net bir ifade kullan.
- Özet veya kritik bulguları bu cümleden hemen sonra kısaca ekleyebilirsin.
