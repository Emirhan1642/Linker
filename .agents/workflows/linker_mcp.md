---
name: linker-mcp-mastery
description: Comprehensive guide on how to efficiently use Linker MCP Server tools for Android/Kotlin Clean Architecture projects. Minimum Token Usage, Maximum Context Accuracy. Includes Advanced BM25 Semantic Search and Architect Analyzer guide.
---

# 🚀 Linker MCP: Yapay Zeka Ustalık Kılavuzu (Skill)

Bu belge, Linker MCP sunucusunu kullanarak devasa bir Android / Compose (Kotlin) projesinin nasıl **sıfır bağlam kaybı (zero context loss)** ve **maksimum token tasarrufuyla** yönetilebileceğini diğer AI ajanlarına anlatmak için tasarlanmıştır.

Klasik Unix araçları (`grep`, `find`, `cat`) yerine, kesinlikle Linker MCP'nin kendi **Lexer (AST) motorunu**, **BM25 Semantic Search** mimarisini ve **Incremental Indexing** yeteneklerini kullanmalısın!

---

## ⚡ 0. Altın Kural: Önce İndeksle!
Linker MCP'nin tüm akıllı arama ve haritalama araçları bellekteki indekse güvenir.
> **Araç:** `index_codebase`
> **Kullanım:** Yeni bir oturuma başlarken veya projede çok fazla değişiklik yaptıktan sonra mutlaka çağır. 
> **Avantaj:** **Incremental Indexing (Artımlı İndeksleme)** sayesinde SHA-256 hash'leri tutulur. Sadece değişen dosyalar parse edilir. Milyonlarca satır kod saniyeler içinde (veya hiç değişmediyse milisaniyeler içinde) indekslenir.

---

## 🔍 1. Kod Arama ve Keşif (Codebase Reconnaissance)

Dosya dizinleri içinde `list_dir` ile kaybolma. Zaman ve token tasarrufu için şu aşamaları izle:

### Adım 1: Doğal Dil ile Arama (Mükemmel Zeka)
Kullanıcı senden Türkçe veya İngilizce soyut bir özellik isterse, doğrudan `semantic_search` kullan.
> **Araç:** `semantic_search`
> **Örnek Sorgular:** `"Kullanıcı profilinde takip etme butonu"`, `"parolayı sıfırla"`, `"karanlık mod ayarları"`, `"login state viewmodel"`
> **Neden? Özellikleri Neler?** 
> - **BM25 Algoritması:** Uzun dokümanlara ceza kesmeyen özel bir kod arama algoritması barındırır.
> - **Code Body Indexing:** Sadece sınıf isimlerini değil, `ViewModel` içindeki state field'larını (`_uiState`) ve UseCase çağrılarını bile anlar.
> - **Prefix & Doğal Dil:** Türkçe'nin sondan eklemeli yapısını çözer ("şifremi", "parolayı" yazılsa dahi kök kelimeyi bulur). Component adıyla kelime tam eşleşirse (Örn: "profil ekranı" -> `ProfileScreen`) x4 Exponential Boost vererek tam hedefi en üste koyar.

### Adım 2: Spesifik İsim veya Kısaltma Arama
Eğer aradığın Sınıf / Composable adını tam veya kısmi olarak biliyorsan ana aracı kullan:
> **Araç:** `search_code`
> **Avantaj:** **Fuzzy Search & Initialism** destekler. Kullanıcı "PS" derse `ProfileScreen`'i bulur. "profScreen" gibi yazım hatalı aramaları Levenshtein edit-distance ile affeder ve doğru class'ı getirir.

---

## 📖 2. Kod Okuma & Token Tasarrufu Sanatı (Golden Rule)

Milyonlarca satırlık projelerde `get_file_content` aracını KULLANMA. Dosyanın tamamını okumak LLM bağlam penceresini (context window) doldurup şişirir.

### Sadece İhtiyacın Olanı Kes & Al!
> **Araç:** `get_component_code`
> **Kullanım:** `componentName: "AuthScreen"` veya `componentName: "LoginViewModel"`
> **Sistem Nasıl Çalışır?** Kendi yazdığımız "Brace-Balancing (Süslü Parantez Terazisi)" algoritması, string ve comment içindeki parantezleri yoksayarak tam hedeflenen class/fonksiyonun başlangıcını ve bitişini tespit eder. Karşına tüm dosya yerine sadece o hedefin pürüzsüz 20-30 satırlık kod bloğunu getirir. Muazzam token tasarrufu sağlar.

### Sınıfın Anatomisini Anlama
> **Araç:** `get_class_details`
> **Kullanım:** Dosyayı okumadan o sınıfın parametrelerini, constructor dependency'lerini, property'lerini ve kimlerden miras (superType) aldığını tek seferde çıkarır.

---

## 🏗 3. Mimari ve Etki Analizi (Architecture Graph)

Proje **Clean Architecture** (Presentation -> Domain -> Data) standartlarındadır ve arayüzler **Jetpack Compose** ile yazılmıştır. Mimariyi anlamak için kod okuma, şu araçları kullan:

- **UI Hiyerarşi Haritası (`get_composable_graph`):** Benzersiz bir özellikle gelir. Hangi ekranın (örn: HomeScreen) kendi içinde hangi alt bileşenleri (PostCard, StoryRow) çağırdığını sana tek hamlede ağaç yapısı olarak sunar. `filter: "Screen"` vererek sadece ekranlar arası bağlantıları görebilirsin.
- **Modül ve Paket Grafiği (`get_module_graph`):** Projedeki katmanların (`com.linker.domain` -> `com.linker.data`) birbirine olan bağımlılıklarını haritalar.
- **Mimari İhlal Tespiti (`detect_violations`):** Clean Architecture kurallarının çiğnenip çiğnenmediğini bulur. (Örn: ViewModel doğrudan RepositoryImpl çağırmış mı? Hangi sınıflar (God Class) tehlikeli boyutta?)
- **Etki Analizi (`find_usages` / `find_dependencies`):** Değişiklik yapacağın bir sınıfı başka kimlerin kullandığını veya kime bağımlı olduğunu AST kesinliğinde bulur. O sınıfı değiştirmeden önce nelerin kırılabileceğini görmek için harikadır.

---

## 📱 4. UI Otomasyonu (ADB Automation & Testing)

Telefon / Emülatör işlemlerini sanki elinle dokunuyormuş gibi kodla yap.

### Ekranı Tarama (Gürültüsüz UI Dump)
> **Araç:** `ui_dump`
> **Özellik:** Standart ADB XML dump'ları gibi binlerce satır FrameLayout çöpü basmaz. **Sadece Tıklanabilir (clickable), İçinde Metin Olan (text) veya ID'si olan Elementleri** filtreleyip verir.

### Aksiyonlar
Koordinat matematiği yapmana gerek yok.
- `ui_find_element`: Ekrandaki bir butonu/yazıyı text veya desc üzerinden bulur.
- `ui_tap_element`: Bulduğu elementin merkez koordinatına otomatik tıklar.
- `ui_type_text`: Input/Search alanlarına klavye vuruşları gönderir. **(ASCII/UTF-8 desteklidir. Türkçe veya emoji karakterlerini byte char formatında hatasız cihaza gönderir).**

### Test Senaryoları
Geliştirilen uzun UI Senaryolarını tek tuşla tetikle:
- `test_search_and_follow`: Bir kullanıcıyı arar ve otomatik takip eder.
- `test_check_ui_issues`: Ekranda tıklanamayacak kadar küçük butonları veya birbirinin üstüne taşan elementleri fiziksel analizle tespit eder.
