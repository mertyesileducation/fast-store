# Fast Store: Kişisel Android Uygulama Mağazası

Kendi APK'larını listeleyen, indiren, kuran ve güncelleyen bir Android uygulama mağazası.

## Özellikler
- **Keşfet / Güncellemeler / Yüklü** sekmeleri
- Arama ve kategori filtreleri
- Uygulama detay sayfası: ikon, ekran görüntüleri, açıklama, "Yenilikler", sürüm bilgileri
- İndirme sırasında ilerleme çubuğu ve iptal düğmesi
- **Güvenlik:** her APK, SHA-256 özeti ve paket adı kontrol edildikten sonra kurulur
- **Tümünü güncelle** düğmesi
- Arka planda otomatik güncelleme kontrolü ve bildirim (yaklaşık 6 saatte bir, istersen sadece Wi-Fi'de)
- Android 12 ve üzerinde, mağazanın kurduğu uygulamalar onay sorulmadan güncellenebilir
- Birden fazla katalog adresi eklenebilir; kataloglar tek listede birleştirilir
- Kaldırma düğmesi, açık/koyu tema desteği
- Android 8.0 ve üzeri (minSdk 26, targetSdk 34)

## Kurulum
1. [Releases](../../releases) sayfasından en son `FastStore-x.y.apk` dosyasını indirip telefonda aç.
2. Telefon "bilinmeyen kaynak" uyarısı verirse, dosyayı açtığın uygulamaya izin ver.
3. Play Protect "tanınmayan geliştirici" uyarısı gösterebilir. Bunun nedeni uygulamanın kendi anahtarınla imzalanmış olması; **"Yine de yükle"** seçeneğini kullan.
4. Fast Store'u aç, sağ üstteki menüden **Ayarlar**'a gir ve katalog adresini yaz.

## Kendi mağazanı yayınlama
1. APK'larını bir klasöre koy (örnek için `ornek-magaza/` klasörüne bak).
2. İsteğe bağlı olarak `<paket.adı>.png` adıyla ikon ve `bilgiler.json` içine açıklamalar ekle.
3. Katalog dosyasını oluştur:
   ```bash
   python3 katalog_olustur.py ornek-magaza --ad "Fast Store"
   ```
4. Klasörü internette erişilebilir bir yere yükle. En kolay yol bir GitHub deposu:
   `https://raw.githubusercontent.com/KULLANICI/DEPO/main/katalog.json`
   (GitHub Pages, kendi sunucun, Netlify vb. de olur.)
5. Bu adresi Fast Store'un Ayarlar ekranına gir.

APK adresleri `katalog.json` dosyasının konumuna göre çözülür. Yani APK'lar ve ikonlar aynı klasördeyse sadece dosya adını yazman yeterli.

Yeni bir sürüm çıkardığında APK'yı klasöre koy (eskisini silebilirsin), `katalog_olustur.py`'yi tekrar çalıştır ve yükle. Fast Store güncellemeyi kendisi bulur.

**İpucu:** Fast Store'un kendi APK'sını da kataloğa ekle (örnekte eklenmiş durumda). Böylece mağaza kendini de güncelleyebilir.

### katalog.json alanları
| Alan | Zorunlu | Açıklama |
|---|---|---|
| `packageName` | ✓ | Paket adı (ör. `com.ornek.notlar`) |
| `apkUrl` | ✓ | APK adresi (göreli veya tam) |
| `versionCode` | ✓ | Sayısal sürüm; güncelleme kontrolü bununla yapılır |
| `name`, `versionName`, `author`, `category`, `description`, `changelog` | | Görünen bilgiler |
| `sha256`, `size` | önerilir | Doğrulama ve boyut bilgisi |
| `iconUrl`, `screenshots` | | Görseller |

## ⚠️ İmza anahtarı: ÇOK ÖNEMLİ
`keystore/magazam.p12` ve `keystore/sifre.txt` Fast Store'un imza anahtarıdır.
- **Bu iki dosyayı güvenli bir yere yedekle.** Kaybedersen Fast Store'un yeni sürümlerini eski sürümün üzerine kuramazsın; Android aynı imzayı ister.
- **Asla herkese açık bir yere (ör. açık GitHub deposu) yükleme.** `.gitignore` dosyası bunları zaten hariç tutuyor.

İmza bilgileri: APK Signature Scheme v2, RSA 4096, 10.000 gün geçerli.
SHA-256 sertifika parmak izi, derleme çıktısının sonunda yazdırılır.

## Kaynaktan derleme
Android SDK veya Gradle gerekmez. Gerekenler: **JDK 17+** ve **Python 3**.
```bash
./build.sh
```
Çıktı `dist/FastStore-<sürüm>.apk` olur. Yeni sürüm için `AndroidManifest.xml` içindeki `versionCode` değerini 1 artır, `versionName` değerini de değiştir.

Derleme araçları depoda tutulmaz; `build.sh` ilk çalıştırmada bunları `tools/indir.sh` ile indirir:
- `dalvik-dx`: DEX derleyicisi (Maven Central)
- `apksig`: Google'ın APK imzalama kütüphanesi (Maven Central)
- `android.jar`: Android 14 API'si, sadece derleme için kullanılır

`build/mini_aapt.py`, aapt2'nin yerine manifesti ve kaynak tablosunu derler.
