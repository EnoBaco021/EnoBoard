# ⚡ EnoBoard

<div align="center">

![EnoBoard Logo](https://img.shields.io/badge/EnoBoard-Animated%20Scoreboard-00d9ff?style=for-the-badge&logo=minecraft&logoColor=white)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4-62B47A?style=flat-square&logo=minecraft)](https://minecraft.net)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![GitHub](https://img.shields.io/badge/GitHub-EnoBaco021-181717?style=flat-square&logo=github)](https://github.com/EnoBaco021)

**Minecraft sunucunuz için animasyonlu scoreboard ve web tabanlı yönetim paneli!**

[Özellikler](#-özellikler) • [Kurulum](#-kurulum) • [Kullanım](#-kullanım) • [Web Panel](#-web-panel) • [Yapılandırma](#-yapılandırma)

</div>

---

## 🎯 Özellikler

### 🎨 Animasyonlu Scoreboard
- ✨ **Animasyonlu Başlık** - Birden fazla frame ile akıcı animasyonlar
- 🎭 **Minecraft Renk Kodları** - Tüm renk ve format kodları desteklenir
- 📊 **Dinamik Placeholderlar** - Oyuncu bilgileri otomatik güncellenir
- ⚡ **Yüksek Performans** - Optimize edilmiş güncelleme sistemi

### 🌐 Web Yönetim Paneli
- 🔐 **Admin Girişi** - Güvenli session tabanlı kimlik doğrulama
- 👁️ **Canlı Önizleme** - Değişiklikleri anında görün
- 📦 **Hazır Şablonlar** - 6 farklı profesyonel tasarım
- 📱 **Responsive Tasarım** - Mobil uyumlu arayüz

---

## 📥 Kurulum

### Gereksinimler
- Minecraft Server (Spigot/Paper) 1.20.4+
- Java 17 veya üzeri

### Adımlar

1. **JAR dosyasını indirin**
   - [Releases](https://github.com/EnoBaco021/EnoBoard/releases) sayfasından son sürümü indirin

2. **Sunucuya yükleyin**
   ```
   plugins/
   └── EnoBoard-1.0-SNAPSHOT.jar
   ```

3. **Sunucuyu başlatın**
   - Plugin otomatik olarak `config.yml` dosyasını oluşturacak

4. **Web panele erişin**
   ```
   http://localhost:3131
   ```

---

## 🎮 Kullanım

### Komutlar

| Komut | Açıklama | İzin |
|-------|----------|------|
| `/enoboard reload` | Yapılandırmayı yeniden yükler | `enoboard.admin` |
| `/enoboard toggle` | Scoreboard'u açar/kapatır | `enoboard.admin` |
| `/enoboard web` | Web panel adresini gösterir | `enoboard.admin` |

### Kısayollar
- `/eb` veya `/scoreboard` komutlarını da kullanabilirsiniz

---

## 🌐 Web Panel

### Giriş Bilgileri (Varsayılan)
```
Kullanıcı Adı: admin
Şifre: admin123
```

> ⚠️ **Güvenlik Uyarısı:** Sunucunuzu yayına almadan önce şifrenizi değiştirin!

### Panel Özellikleri

<table>
<tr>
<td width="50%">

**⚙️ Genel Ayarlar**
- Scoreboard açma/kapama
- Güncelleme aralığı ayarlama
- Başlık frame'lerini düzenleme
- Scoreboard satırlarını düzenleme

</td>
<td width="50%">

**📦 Hazır Şablonlar**
- 🏛️ Klasik
- ⚔️ Survival
- 🗡️ PvP Arena
- ☁️ Skyblock
- 🌈 Gökkuşağı
- 📝 Minimalist

</td>
</tr>
</table>

---

## 📝 Yapılandırma

### config.yml

```yaml
# Web panel portu
web-port: 3131

# Web Panel Admin Girişi
web-panel:
  username: "admin"
  password: "admin123"

# Scoreboard ayarları
scoreboard:
  enabled: true
  update-interval: 5  # tick (20 tick = 1 saniye)
  
  # Animasyonlu başlık
  title-frames:
    - "&6&l✦ &e&lSunucu &6&l✦"
    - "&e&l✦ &6&lSunucu &e&l✦"
  
  # Scoreboard satırları
  lines:
    - "&7&m----------------"
    - "&e⭐ &fHoşgeldin, &a%player%"
    - "&e👥 &fOnline: &a%online%&7/&a%max%"
    - "&7&m----------------"
```

### 📌 Placeholderlar

| Placeholder | Açıklama |
|-------------|----------|
| `%player%` | Oyuncu adı |
| `%online%` | Online oyuncu sayısı |
| `%max%` | Maksimum oyuncu sayısı |
| `%world%` | Oyuncunun bulunduğu dünya |
| `%health%` | Oyuncu canı |
| `%food%` | Oyuncu açlık seviyesi |
| `%level%` | Oyuncu seviyesi |
| `%x%` `%y%` `%z%` | Oyuncu koordinatları |

### 🎨 Renk Kodları

```
&0 Siyah       &8 Koyu Gri
&1 Koyu Mavi   &9 Mavi
&2 Koyu Yeşil  &a Yeşil
&3 Koyu Aqua   &b Aqua
&4 Koyu Kırmızı &c Kırmızı
&5 Mor         &d Pembe
&6 Altın       &e Sarı
&7 Gri         &f Beyaz

&l Kalın       &o İtalik
&n Altı Çizili &m Üstü Çizili
&r Sıfırla
```

---



## 🔧 Derleme

Projeyi kendiniz derlemek için:

```bash
# Repository'yi klonlayın
git clone https://github.com/EnoBaco021/EnoBoard.git

# Dizine girin
cd EnoBoard

# Maven ile derleyin
mvn clean package

# JAR dosyası target/ klasöründe oluşacak
```

---

## 📄 Lisans

Bu proje MIT Lisansı altında lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakın.

---

## 🤝 Katkıda Bulunma

Katkılarınızı memnuniyetle karşılıyoruz!

1. Bu repository'yi fork edin
2. Feature branch oluşturun (`git checkout -b feature/YeniOzellik`)
3. Değişikliklerinizi commit edin (`git commit -m 'Yeni özellik eklendi'`)
4. Branch'inizi push edin (`git push origin feature/YeniOzellik`)
5. Pull Request açın

---

## 📞 İletişim

<div align="center">

[![GitHub](https://img.shields.io/badge/GitHub-EnoBaco021-181717?style=for-the-badge&logo=github)](https://github.com/EnoBaco021)

**Geliştirici:** EnoBaco021

⭐ Bu projeyi beğendiyseniz yıldız vermeyi unutmayın!

</div>

---

<div align="center">

Made with ❤️ by [EnoBaco021](https://github.com/EnoBaco021)

</div>

