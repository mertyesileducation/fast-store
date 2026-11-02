package com.kisisel.magaza;

import java.util.HashMap;
import java.util.Map;

/** Android izinlerinin Turkce aciklamalari. */
final class Permissions {
    private Permissions() {}

    static final class Info {
        final String title, desc;
        final boolean sensitive;
        Info(String t, String d, boolean s) { title = t; desc = d; sensitive = s; }
    }

    private static final Map<String, Info> MAP = new HashMap<>();

    private static void p(String name, String title, String desc, boolean sensitive) {
        MAP.put("android.permission." + name, new Info(title, desc, sensitive));
    }

    static {
        p("INTERNET", "İnternet erişimi", "İnternetten veri alabilir ve gönderebilir.", false);
        p("ACCESS_NETWORK_STATE", "Ağ durumunu görme", "Wi-Fi veya mobil veriye bağlı olup olmadığını görebilir.", false);
        p("ACCESS_WIFI_STATE", "Wi-Fi bilgisini görme", "Bağlı olunan Wi-Fi ağı hakkında bilgi alabilir.", false);
        p("POST_NOTIFICATIONS", "Bildirim gösterme", "Bildirim gönderebilir.", false);
        p("VIBRATE", "Titreşim", "Telefonu titretebilir.", false);
        p("RECEIVE_BOOT_COMPLETED", "Açılışta çalışma", "Telefon açıldığında kendini başlatabilir.", false);
        p("WAKE_LOCK", "Uykuyu engelleme", "Telefonun uyku moduna geçmesini geçici olarak engelleyebilir.", false);
        p("FOREGROUND_SERVICE", "Ön planda çalışma", "Bildirim göstererek arka planda çalışmaya devam edebilir.", false);
        p("USE_EXACT_ALARM", "Tam zamanlı alarm", "Belirli bir saatte tam olarak çalışacak alarm kurabilir.", false);
        p("SCHEDULE_EXACT_ALARM", "Tam zamanlı alarm", "Belirli bir saatte tam olarak çalışacak alarm kurabilir.", false);
        p("REQUEST_INSTALL_PACKAGES", "Uygulama yükleme", "Senin onayınla başka uygulamaları yükleyebilir.", true);
        p("REQUEST_DELETE_PACKAGES", "Uygulama kaldırma", "Senin onayınla uygulama kaldırmayı başlatabilir.", false);
        p("UPDATE_PACKAGES_WITHOUT_USER_ACTION", "Onaysız güncelleme", "Kendi yüklediği uygulamaları onay sormadan güncelleyebilir.", false);
        p("QUERY_ALL_PACKAGES", "Yüklü uygulamaları görme", "Telefonda hangi uygulamaların yüklü olduğunu görebilir.", true);
        p("WRITE_EXTERNAL_STORAGE", "Depolamaya yazma", "Eski Android sürümlerinde dosyaları kaydedebilir.", true);
        p("READ_EXTERNAL_STORAGE", "Depolamayı okuma", "Paylaşılan depolamadaki dosyaları okuyabilir.", true);
        p("READ_MEDIA_IMAGES", "Fotoğrafları okuma", "Fotoğraflarına erişebilir.", true);
        p("READ_MEDIA_VIDEO", "Videoları okuma", "Videolarına erişebilir.", true);
        p("READ_MEDIA_AUDIO", "Sesleri okuma", "Müzik ve ses dosyalarına erişebilir.", true);
        p("CAMERA", "Kamera", "Fotoğraf ve video çekebilir.", true);
        p("RECORD_AUDIO", "Mikrofon", "Ses kaydedebilir.", true);
        p("ACCESS_FINE_LOCATION", "Kesin konum", "GPS ile tam konumuna erişebilir.", true);
        p("ACCESS_COARSE_LOCATION", "Yaklaşık konum", "Ağ bilgisiyle yaklaşık konumuna erişebilir.", true);
        p("ACCESS_BACKGROUND_LOCATION", "Arka planda konum", "Uygulama kapalıyken bile konumuna erişebilir.", true);
        p("READ_CONTACTS", "Kişileri okuma", "Rehberindeki kişileri okuyabilir.", true);
        p("WRITE_CONTACTS", "Kişileri değiştirme", "Rehberindeki kişileri değiştirebilir.", true);
        p("READ_CALENDAR", "Takvimi okuma", "Takvim etkinliklerini okuyabilir.", true);
        p("WRITE_CALENDAR", "Takvime yazma", "Takvim etkinliği ekleyebilir veya değiştirebilir.", true);
        p("CALL_PHONE", "Arama yapma", "Senin yerine telefon araması başlatabilir.", true);
        p("READ_PHONE_STATE", "Telefon durumu", "Telefon numarası ve arama durumunu görebilir.", true);
        p("SEND_SMS", "SMS gönderme", "SMS gönderebilir (ücret doğurabilir).", true);
        p("READ_SMS", "SMS okuma", "SMS mesajlarını okuyabilir.", true);
        p("BLUETOOTH_CONNECT", "Bluetooth bağlantısı", "Eşleşmiş Bluetooth cihazlarına bağlanabilir.", false);
        p("BLUETOOTH_SCAN", "Bluetooth tarama", "Yakındaki Bluetooth cihazlarını bulabilir.", true);
        p("NFC", "NFC", "NFC etiketleriyle iletişim kurabilir.", false);
        p("SYSTEM_ALERT_WINDOW", "Diğer uygulamaların üzerinde gösterme", "Başka uygulamaların üstünde pencere açabilir.", true);
        p("BODY_SENSORS", "Vücut sensörleri", "Nabız gibi sağlık sensörlerine erişebilir.", true);
        p("ACTIVITY_RECOGNITION", "Fiziksel etkinlik", "Adım ve hareket bilgisine erişebilir.", true);
    }

    static Info get(String perm) {
        Info i = MAP.get(perm);
        if (i != null) return i;
        String shortName = perm.substring(perm.lastIndexOf('.') + 1).replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return new Info(shortName, perm, false);
    }
}
