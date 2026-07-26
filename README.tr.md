<p align="center">
  <strong>🌐 <a href="README.md">English</a> · <a href="#turkce">Türkçe</a></strong>
</p>

---

<a id="turkce"></a>

<p align="center">
  <img src="https://img.shields.io/badge/ZipperMC-1.4.0-4CAF50?style=for-the-badge" alt="ZipperMC">
  <img src="https://img.shields.io/github/v/release/monkeyapes/ZipperMC?style=for-the-badge&label=s%C3%BCr%C3%BCm" alt="Release">
  <img src="https://img.shields.io/badge/license-MIT-4CAF50?style=for-the-badge" alt="Lisans">
</p>

**ZipperMC**, Minecraft PE (Bedrock) için addon dosyalarını (`.mcaddon`, `.mcpack`, `.zip`, `.mcworld`, vb.) **hiçbir kullanıcı kurulumu gerektirmeden** yükleyen bir Android uygulamasıdır. İzin yok, klasör seçme yok, ayar değiştirme yok. Uygulamayı açın ve çalışır.

---

## Kurulum

**Hiçbir kurulum gerekmez.** Zaten amaç bu.

1. [APK'yı indirin](https://github.com/monkeyapes/ZipperMC/releases/latest)
2. Telefonunuza kurun (cihazınız sorarsa *Bilinmeyen uygulamalardan yükle* seçeneğini etkinleştirin)
3. Uygulamayı açın

**İzin vermeniz gerekmez. Klasör seçmeniz gerekmez. Ayarları değiştirmeniz gerekmez.**

---

## Kullanım

### Otomatik mod (önerilen)

1. ZipperMC'yi açın
2. Uygulama otomatik olarak **İndirilenler**, **APK'lar** ve **Minecraft klasörlerinizi** tarar
3. Bulunan dosyalar ana ekranda listelenir
4. İlk bulunan dosya **otomatik olarak analiz edilir**
5. ZipperMC yüklü Minecraft sürümünüzü algılar ve addon'un uyumluluğunu **otomatik ayarlar**
6. **Minecraft'a Yükle** butonuna dokunun — önce doğrudan yüklemeyi dener. Olmazsa **Minecraft'a Gönder**'i kullanın

### Dosya yöneticinizden

1. Herhangi bir `.mcaddon`, `.mcpack` veya `.zip` dosyası indirin
2. Dosya yöneticinizde dosyaya dokunun
3. Uygulama seçiciden **ZipperMC**'yi seçin
4. Dosya ZipperMC'de açılır, yüklemeye hazırdır

### Sürüm Düzenleyici

Bir addon farklı bir Minecraft sürümü için yapılmışsa:

1. Analizden sonra herhangi bir pakette **Sürüm** butonuna dokunun
2. **Min Motor Sürümü** ve/veya **Paket Sürümü**'nü değiştirin
3. **Kaydet ve Devam Et**'e dokunun
4. ZipperMC yüklemeden önce addon'u otomatik olarak düzeltir

> **İpucu:** Android 11+'da doğrudan dosya erişimi kısıtlanmıştır. ZipperMC bunu otomatik halleder — Minecraft'ın dosyayı kendisinin içe aktarması için **Minecraft'a Gönder**'i kullanın.

---

## Adım adım çalışma şekli

```
Uygulama açılır → İndirilenler, APK'lar, Minecraft klasörlerini tarar
     ↓ .mcaddon/.mcpack/.zip bulur
     ↓ içeriği analiz eder (manifest.json tespiti)
     ↓ Minecraft sürümünüzü algılar (1.21.x vb.)
     ↓ addon sürümünü Minecraft'ınıza göre ayarlar
     ↓ games/com.mojang/ klasörüne doğrudan yüklemeyi dener
     ↓ engellenirse → "Minecraft'a Gönder"e dokunun
     ↓ Minecraft açılır ve addon'u içe aktarır
```

---

## Desteklenen dosya türleri

| Biçim | Açıklama |
|--------|-----------|
| `.mcaddon` | Kaynak + davranış paketi içeren addon |
| `.mcpack` | Tek kaynak veya davranış paketi |
| `.mcworld` | Minecraft dünyası |
| `.mctemplate` | Dünya şablonu |
| `.mcskin` | Skin paketi |
| `.zip` | Yukarıdakilerden herhangi biri (veya içerik tespiti) |

---

## SSS

**S: Depolama izni vermem gerekiyor mu?**  
C: **Hayır.** ZipperMC asla depolama izni istemez. Önbelleğe yazar veya dosyayı doğrudan Minecraft'a gönderir.

**S: "Minecraft'a Gönder" neden Minecraft'ı açıyor?**  
C: Android böyle çalışır — bir uygulama diğerine dosya gönderir. Minecraft dosyayı içe aktarır ve işlem tamamlanır.

**S: Android 14/15'te çalışır mı?**  
C: Evet. ZipperMC, Android 14 ve 15 dahil, Android 8.0 (API 26) ve üzerinde çalışır.

**S: Yükleme sonrası Minecraft addon'u görmüyor?**  
C: Bunun yerine **Minecraft'a Gönder**'i deneyin. Bu, dosyayı doğrudan Minecraft'a gönderir ve içe aktarma işlemini Minecraft yapar.

**S: Addon sürümünü düzenleyebilir miyim?**  
C: Evet. Herhangi bir pakette **Sürüm**'e dokunarak min_engine_version ve pack_version'ı değiştirebilirsiniz.

---

## Kaynak koddan derleme

```bash
git clone https://github.com/monkeyapes/ZipperMC.git
cd ZipperMC
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

**Gereksinimler:** Android SDK 35, JDK 17

## Lisans

MIT
