# Reaktivlar hisobi

Laboratoriya reaktivlarini hisobga olish uchun oddiy Laravel ilova. Qog‘oz daftardagi jadvalning elektron varianti:

| № | Reaktiv nomi | Kelgan vaqt | Berilgan miqdor (gr/ml) | Yaroqlilik muddati | Ombordagi miqdor |
|---|--------------|-------------|-------------------------|--------------------|------------------|

Barcha ustunlar **qo‘lda kiritiladi** va **to‘ldirilishi majburiy** (birortasi bo‘sh bo‘lsa, forma saqlanmaydi va o‘zbekcha xato ko‘rsatiladi). Reaktiv nomi yaroqlilik muddatiga qarab avtomatik bo‘yaladi:

| Rang | Qachon |
|------|--------|
| 🟢 Yashil | muddat tugashiga **3 oydan ko‘p** qolgan |
| 🟡 Sariq | **1 oydan 3 oygacha** qolgan (roppa-rosa 1 oy va 3 oy ham shu yerda) |
| 🔴 Qizil | **1 oydan kam** qolgan yoki muddati **o‘tib ketgan** |

Oylar kalendar bo‘yicha hisoblanadi. Masalan, bugun 16-oktabr bo‘lsa: muddati 15-noyabrgacha bo‘lganlar qizil, 16-noyabrdan 16-yanvargacha sariq, 17-yanvardan boshlab yashil. Ranglar har kuni o‘z-o‘zidan yangilanadi — hech narsa bosish shart emas.

## Imkoniyatlar

- Reaktiv qo‘shish, tahrirlash, o‘chirish (bitta reaktivning har bir kelgan partiyasi alohida qator bo‘lishi mumkin)
- «Saqlash va yana qo‘shish» — daftardagi ro‘yxatni ketma-ket kiritish uchun
- Yaroqlilik muddati kelgan vaqtdan oldin bo‘lsa yoki miqdor manfiy bo‘lsa — xato
- Rang bo‘yicha filtr (har bir rangda nechta reaktiv borligi ko‘rinadi) va nomi bo‘yicha qidiruv
- «Muddati yaqinlari birinchi» tugmasi — eng shoshilinch reaktivlar tepada chiqadi
- Har bir reaktiv uchun «N kun qoldi» / «Muddati N kun oldin o‘tgan» yozuvi
- Miqdorni vergul bilan ham yozish mumkin: `2,5`
- Telefon va planshetda kartochka ko‘rinishida, printerda (A4, albom) jadval ko‘rinishida rangi bilan chiqadi
- npm / build kerak emas — oddiy PHP hosting yetarli

## O‘rnatish

Talablar: PHP 8.3+, Composer, SQLite (yoki MySQL / PostgreSQL).

```bash
git clone https://github.com/abdu11aev-samandar/reaktiv-ombor.git
cd reaktiv-ombor
composer setup      # composer install, .env, APP_KEY, SQLite baza va migratsiya
php artisan serve
```

So‘ng brauzerda oching: <http://localhost:8000>

### Sozlamalar (`.env`)

| O‘zgaruvchi | Standart | Izoh |
|-------------|----------|------|
| `APP_NAME` | `Reaktivlar hisobi` | Sarlavhada chiqadigan nom |
| `APP_TIMEZONE` | `Asia/Tashkent` | «Bugun» shu vaqt zonasi bo‘yicha hisoblanadi |
| `DB_CONNECTION` | `sqlite` | MySQL uchun `mysql` va `DB_*` qiymatlari |

### Serverga joylash

Odatiy Laravel kabi: domen `public/` papkaga qaratiladi, so‘ng serverda:

```bash
composer install --no-dev --optimize-autoloader
cp .env.example .env
php artisan key:generate
```

`.env` faylda albatta o‘zgartiring:

```env
APP_ENV=production
APP_DEBUG=false
APP_URL=https://sizning-domeningiz.uz
```

`APP_DEBUG=true` qolsa, xato bo‘lganda sahifada server sozlamalari ko‘rinib qoladi.

```bash
touch database/database.sqlite   # SQLite ishlatilsa
php artisan migrate --force
php artisan config:cache && php artisan route:cache && php artisan view:cache
```

Veb-server foydalanuvchisi `storage/`, `bootstrap/cache/` va (SQLite bo‘lsa) `database/` papkalariga yoza olishi kerak.

> **Diqqat:** ilovada login yo‘q. Uni internetga ochiq joylashtirsangiz, kirishni cheklang (masalan, veb-server darajasida parol yoki Laravel autentifikatsiyasi).

## Android ilova

`android/` papkada internetsiz ishlaydigan Android ilova bor (Kotlin, Jetpack Compose). Ma’lumotlar telefonning o‘zida saqlanadi, server kerak emas.

- Ro‘yxat, ranglar, filtr, qidiruv, saralash va validatsiya qoidalari Laravel ilovadagi bilan bir xil
- Sanalar kalendardan tanlanadi, barcha maydonlar majburiy
- **Eslatmalar:** har kuni ertalab soat 9:00 da tekshiradi; reaktiv sariq yoki qizilga o‘tsa yoki muddati tugasa — telefonga xabar keladi (bir bosqich haqida bir marta)
- **Zaxira:** menyu → «Zaxira nusxa olish» (JSON fayl) va «Zaxiradan tiklash». Android’ning Google zaxirasiga ham kiradi
- Android 7.0 va undan yangi telefonlarda ishlaydi

### APK yig‘ish

Talablar: JDK 17+, Android SDK (`local.properties` faylida `sdk.dir=...`).

```bash
cd android
./gradlew testDebugUnitTest assembleRelease
# tayyor fayl: android/app/build/outputs/apk/release/app-release.apk
```

Har push’da GitHub Actions («Android» workflow) testlarni o‘tkazadi va APK’ni yig‘adi — uni Actions sahifasidagi **reaktivlar-apk** artefaktidan yuklab olish mumkin.

### Imzolash kaliti

Yangilangan APK eski ilova ustidan o‘rnatilishi uchun **har doim bir xil kalit** bilan imzolanishi kerak. Kalit (`release.jks`) va paroli gitga qo‘shilmaydi.

- Kompyuterda: `android/release.jks` va `android/keystore.properties` fayllarini qo‘ying:

  ```properties
  storeFile=release.jks
  storePassword=...
  keyAlias=reaktiv
  keyPassword=...
  ```

- GitHub Actions uchun: repo → Settings → Secrets and variables → Actions → quyidagi sirlarni qo‘shing:
  `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.jks` natijasi), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`reaktiv`), `ANDROID_KEY_PASSWORD`.

Kalit topilmasa, APK sinov (debug) kaliti bilan imzolanadi — bunday faylni sinab ko‘rish mumkin, lekin asosiy ilova ustidan yangilanmaydi.

## Windows dasturi

`desktop/` papkada kompyuter uchun dastur bor (Kotlin, Compose Desktop). Ekrani **Excel jadvaliga o‘xshaydi**, internet kerak emas.

- Kataklarga to‘g‘ridan-to‘g‘ri yoziladi (Excel’dagi kabi): **Enter** — keyingi qator (Tab bilan yurilgan bo‘lsa — qator boshidagi ustunga), **Tab** — o‘ngga, strelkalar — yurish, **F2** yoki ikki marta bosish — katakni tahrirlash (yozib turganda F2 yoki katak ichiga bosish — strelkalar matn ichida yuradi), **Esc** — bekor qilish, **Delete** — katakni tozalash
- Filtr, qidiruv yoki saralash yoqilganda tahrirlangan qator joyida qoladi (ro‘yxat filtr qayta tanlanganda yangilanadi)
- **Ctrl+Z / Ctrl+Y** — bekor qilish / qayta bajarish, **Ctrl+C / Ctrl+V** — nusxa olish / qo‘yish (Excel’dan bir nechta qatorni birdaniga qo‘yish mumkin), **Ctrl+;** — bugungi sana, **Ctrl+F** — qidiruv, **Ctrl+−** — qatorni o‘chirish
- Ustun sarlavhasini bosish — saralash (▲/▼), rang bo‘yicha filtr, nom bo‘yicha qidiruv
- Reaktiv nomi muddatga qarab bo‘yaladi (telefon va web ilovadagi bilan bir xil qoida); hali to‘ldirilmagan qator sariq fonda, yetishmayotgan kataklar qizil — sichqoncha bilan ustiga borilsa sababi chiqadi
- Har bir o‘zgarish avtomatik saqlanadi: `%APPDATA%\Reaktivlar\reaktivlar.json` (oldingi holat — `reaktivlar.bak.json`)
- **Fayl** menyusi: telefon zaxirasini ochish, telefon uchun zaxira saqlash (telefonda ⋮ → «Zaxiradan tiklash»; tugallanmagan qatorlar ham faylda qoladi, ularni faqat kompyuter o‘qiydi), Excel uchun CSV
- Excel’dan qo‘yilgan «/» li sanalar kompyuterning mintaqaviy formati bo‘yicha o‘qiladi: «English (United States)» bo‘lsa oy/kun/yil, aks holda kun/oy/yil
- Kompyuter o‘chirilganda yoki qayta yuklanganda ham yozib turilgan katak saqlanadi
- Ma’lumotlar fayli telefon zaxirasi formatida — uni to‘g‘ridan-to‘g‘ri telefonga ko‘chirib tiklash ham mumkin

### O‘rnatish

GitHub → **Actions** → «Windows» → oxirgi muvaffaqiyatli ishga tushirish → **Reaktivlar-Windows-msi** artefaktini yuklab oling, ichidagi `.msi` ni oching. Administrator huquqi kerak emas. Har bir yig‘ishning versiyasi yangi (`1.0.<raqam>`), shuning uchun yangisi eskisining ustiga o‘rnatiladi, ma’lumotlar saqlanib qoladi. O‘rnatuvchi ishlamasa: **Reaktivlar-Windows-portable** artefaktini yuklab oling, zip faylni sichqonchaning o‘ng tugmasi bilan bosib **«Извлечь все» / «Extract All»** ni tanlang, so‘ng chiqarilgan `Reaktivlar` papkasidagi `Reaktivlar.exe` ni ishga tushiring. Zip ichidan to‘g‘ridan-to‘g‘ri ishga tushirilsa, dastur ochilmaydi. Windows «noma’lum nashriyotchi» deb ogohlantirsa: **Batafsil → Baribir ishga tushirish**.

### Linux (Kali, Debian, Ubuntu, Mint)

GitHub → **Actions** → «Windows» → **Reaktivlar-Linux-deb** artefaktidagi `.deb` faylni o‘rnating:

```bash
sudo apt install ./reaktivlar_*_amd64.deb
```

Dastur menyuning «Ofis» bo‘limida chiqadi (yoki `/opt/reaktivlar/bin/Reaktivlar`). Ma’lumotlar: `~/.local/share/reaktivlar/reaktivlar.json`. O‘chirish: `sudo apt remove reaktivlar` (ma’lumotlar qoladi).

### Yig‘ish

```bash
cd desktop
./gradlew test          # testlar
./gradlew run           # dasturni ishga tushirish
./gradlew packageMsi    # Windows o‘rnatuvchisi (faqat Windows’da)
./gradlew packageDeb    # Linux .deb (Debian/Ubuntu’da; Temurin JDK 17 bilan yig‘ing)
```

## Testlar

```bash
php artisan test
```

Ranglar chegaralari (`tests/Unit/ExpiryStatusTest.php`) va barcha sahifalar (`tests/Feature/ReagentTest.php`) testlangan.

## Kod tuzilishi

| Fayl | Vazifasi |
|------|----------|
| `app/Enums/ExpiryStatus.php` | Rang qoidasi (3 oy / 1 oy chegaralari) |
| `app/Models/Reagent.php` | Reaktiv modeli, sana va miqdorlarni formatlash |
| `app/Http/Requests/ReagentRequest.php` | Validatsiya va o‘zbekcha xato xabarlari |
| `app/Http/Controllers/ReagentController.php` | Ro‘yxat, qo‘shish, tahrirlash, o‘chirish |
| `resources/views/reagents/` | Sahifalar (Blade) |
| `public/css/app.css` | Dizayn (build talab qilmaydi) |
