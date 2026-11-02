# Fast Store: Kişisel Android Uygulama Mağazası

Kendi APK'larını listeleyen, indiren, kuran ve güncelleyen bir Android uygulama mağazası.

## Özellikler (2.0)

### Keşfet
- **Vitrin:** öne çıkan uygulamalar kayan afişlerle gösterilir (otomatik ilerler, parmakla kaydırılır).
- **Kategoriler:** kategori çipleri ve her kategori için yatay raf.
- **Yeni eklenenler** ve **Son güncellenenler** rafları, ayrıca tüm uygulamalar listesi.
- Yüklenirken iskelet animasyonu; hata durumunda "Tekrar dene" ekranı.

### Ara
- Puanlı sıralama: ad, etiket, kategori, geliştirici ve açıklamada arar. Türkçe karakter fark etmez (ş/s, ı/i…).
- Son aramalar ve kategorilere göz atma.

### Uygulama sayfası
- Afiş ve ekran görüntüleri; tam ekran, kaydırılabilir görüntüleyici.
- Sürüm, boyut, gereken Android sürümü, kategori ve güncelleme tarihi.
- "Bu uygulama hakkında" (genişletilebilir), etiketler ve **Yenilikler**.
- **İzinler:** APK'dan otomatik okunur, Türkçe açıklanır; hassas izinler işaretlenir.
- **Fast Protect** kartı: dosya özeti, geliştirici imza parmak izi ve paket kimliği. Yüklü sürümün imzası da karşılaştırılır.
- Geliştirici iletişimi (web, e-posta, gizlilik politikası), aynı geliştiricinin diğer uygulamaları ve benzer uygulamalar.
- Paylaş, indirme bağlantısını kopyala, kaldır, sistem uygulama ayarları.

### Güncellemeler ve Kitaplık
- Güncelleme sayısı alt menüde rozet olarak görünür; tek dokunuşla "Tümünü güncelle".
- **Otomatik güncelleme:** kapalı / sadece Wi-Fi / her zaman. Android 12 ve üzerinde Fast Store'un yüklediği uygulamalar arka planda onaysız güncellenir.
- **Kitaplık:** yüklü uygulamalar, kapladıkları alan ve kurulum/güncelleme/kaldırma geçmişi.

### Güvenlik: Fast Protect
Her kurulumdan önce:
1. Cihaz uyumu (minimum Android sürümü) ve yeterli boş alan kontrol edilir.
2. İndirilen dosyanın **SHA-256** özeti katalogla karşılaştırılır.
3. APK'daki **paket adı** katalogla eşleşmelidir.
4. APK'nın **geliştirici imza sertifikası** katalogdaki parmak iziyle eşleşmelidir.
5. Uygulama zaten yüklüyse, **yüklü sürümün imzası** da aynı olmalıdır.

Herhangi biri tutmazsa kurulum durdurulur.

### Diğer
- İndirme ilerlemesi (hız ve boyutla) bildirimde görünür. Kurulum bitince "Açmak için dokun" bildirimi gelir.
- Mobil veride büyük indirmeler için uyarı.
- Açık / koyu / sistem teması.
- Yeni mağaza ikonu.

## Kurulum
1. [Releases](../../releases) sayfasından en son `FastStore-x.y.apk` dosyasını indirip telefonda aç.
2. Telefon "bilinmeyen kaynak" uyarısı verirse, dosyayı açtığın uygulamaya izin ver.
3. Play Protect "tanınmayan geliştirici" uyarısı gösterebilir. Bunun nedeni uygulamanın kendi anahtarınla imzalanmış olması; **"Yine de yükle"** seçeneğini kullan.
4. Fast Store'u aç; uygulamalar sabit katalogdan otomatik gelir.

## Kendi mağazanı yayınlama
1. APK'larını bir klasöre koy (örnek için `katalog/` klasörüne bak).
2. İsteğe bağlı olarak `<paket.adı>.png` adıyla ikon ve `bilgiler.json` içine açıklamalar ekle.
3. Katalog dosyasını oluştur:
   ```bash
   python3 katalog_olustur.py katalog --ad "Fast Store"
   ```
4. Klasörü internette erişilebilir bir yere yükle. En kolay yol bir GitHub deposu:
   `https://raw.githubusercontent.com/KULLANICI/DEPO/main/katalog.json`
   (GitHub Pages, kendi sunucun, Netlify vb. de olur.)
5. Kendi katalog adresini kullanmak için `src/com/kisisel/magaza/CatalogRepo.java` içindeki `CATALOG_URL` değerini değiştirip yeniden derle.

APK adresleri `katalog.json` dosyasının konumuna göre çözülür. Yani APK'lar ve ikonlar aynı klasördeyse sadece dosya adını yazman yeterli.

Yeni bir sürüm çıkardığında APK'yı klasöre koy (eskisini silebilirsin), `katalog_olustur.py`'yi tekrar çalıştır ve yükle. Fast Store güncellemeyi kendisi bulur.

**İpucu:** Fast Store'un kendi APK'sını da kataloğa ekle. Böylece mağaza kendini de güncelleyebilir.

### Hazır katalog
Bu depodaki `katalog/` klasörü Fast Store ve MY Eğitim'i içerir. Fast Store 1.1 ve sonrası **yalnızca bu katalogu kullanır**; adres uygulamaya gömülüdür ve değiştirilemez (`CatalogRepo.CATALOG_URL`):
```
https://raw.githubusercontent.com/mertyesileducation/fast-store/main/katalog/katalog.json
```
Yeni bir APK eklemek için onu `katalog/` klasörüne koy, eski sürümü sil, `python3 katalog_olustur.py katalog --ad "Fast Store"` komutunu çalıştır ve gönder.

`katalog_olustur.py` izinleri, minimum/hedef Android sürümünü ve imza parmak izini APK'dan kendisi okur. Vitrin afişleri için `python3 scripts/vitrin_uret.py katalog` komutunu kullan (Pillow gerekir). `bilgiler.json` dosyasında `shortDescription`, `tags`, `featured`, `website`, `email`, `privacy` ve `screenshots` alanları da kullanılabilir.

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

## Otomatik sürüm (GitHub Actions)
1. **Bir kerelik kurulum:** Deponun *Settings → Secrets and variables → Actions* bölümüne iki secret ekle:
   - `KEYSTORE_BASE64`: `keystore/magazam.p12` dosyasının base64 hâli (`base64 -w0 keystore/magazam.p12`)
   - `KEYSTORE_PASSWORD`: `keystore/sifre.txt` dosyasının içeriği
2. Yeni sürüm için `AndroidManifest.xml`'deki `versionCode` ve `versionName` değerlerini artırıp gönder.
3. *Actions → Release → Run workflow* ile çalıştır. APK derlenir, imzalanır ve `v<sürüm>` etiketiyle Releases sayfasına eklenir.
