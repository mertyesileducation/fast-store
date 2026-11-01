import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * APK imzalama ve dogrulama (Google'in apksig kutuphanesi ile, APK Signature Scheme v2).
 * imzala:   java Signer sign <keystore.p12> <sifre> <takma_ad> <giris.apk> <cikis.apk>
 * dogrula:  java Signer verify <apk>
 */
public class Signer {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("sign")) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(a[1])) {
                ks.load(in, a[2].toCharArray());
            }
            PrivateKey key = (PrivateKey) ks.getKey(a[3], a[2].toCharArray());
            X509Certificate cert = (X509Certificate) ks.getCertificate(a[3]);
            ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("CERT", key,
                    Collections.singletonList(cert)).build();
            new ApkSigner.Builder(Collections.singletonList(cfg))
                    .setInputApk(new File(a[4]))
                    .setOutputApk(new File(a[5]))
                    .setMinSdkVersion(26)
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .setCreatedBy("Fast Store build")
                    .build()
                    .sign();
            System.out.println("Imzalandi: " + a[5]);
        } else {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).setMinCheckedPlatformVersion(26).build().verify();
            System.out.println("Dogrulandi: " + r.isVerified() + "  (v1=" + r.isVerifiedUsingV1Scheme()
                    + ", v2=" + r.isVerifiedUsingV2Scheme() + ")");
            for (Object e : r.getErrors()) System.out.println("HATA: " + e);
            for (Object w : r.getWarnings()) System.out.println("UYARI: " + w);
            for (X509Certificate c : r.getSignerCertificates()) {
                System.out.println("Sertifika: " + c.getSubjectX500Principal());
                byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
                StringBuilder sb = new StringBuilder();
                for (byte b : d) sb.append(String.format("%02X:", b));
                System.out.println("SHA-256 parmak izi: " + sb.substring(0, sb.length() - 1));
            }
            if (!r.isVerified()) System.exit(1);
        }
    }
}
