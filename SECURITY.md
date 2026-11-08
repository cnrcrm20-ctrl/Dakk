# Güvenlik Politikası (SECURITY)

## Desteklenen Sürümler
| Sürüm | Destekleniyor |
| --- | --- |
| 1.0.x | Evet |

## Güvenlik Bildirimi
Dakk projesinde potansiyel bir güvenlik açığı tespit ederseniz, lütfen herkese açık bir "Issue" açmak yerine doğrudan proje yöneticileri ile iletişime geçin.

## Kriptografik Standartlar
- **Kimlik**: 512 karakterlik `SecureRandom` tabanlı kod.
- **Asimetrik Anahtarlar**: EC secp256r1 (P-256) KeyPair.
- **Anahtar Değişimi**: ECDH (Elliptic Curve Diffie-Hellman) -> SHA-256 türetimi.
- **Simetrik Şifreleme**: AES-256-GCM (12-byte rastgele IV, 128-bit authentication tag).
- **Dijital İmza**: SHA256withECDSA.
- **Ağ Katmanı**: WebRTC DTLS/SRTP (UDP P2P).
