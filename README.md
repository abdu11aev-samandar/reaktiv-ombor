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

## Windows dasturi — Reaktiv Control

`desktop/` papkada **Reaktiv Control — Forensic Laboratory Management System** bor: sud-tibbiy ekspertiza laboratoriyalari uchun
reaktivlar nazorati dasturi (Kotlin, Compose Desktop). Internet kerak emas — hamma narsa kompyuterning o‘zida ishlaydi.
Texnik topshiriq bo‘yicha arxitektura va qarorlar: [`desktop/DESIGN.md`](desktop/DESIGN.md).

### Imkoniyatlar

Chap menyuda 13 bo‘lim, har biri ishlaydigan sahifa:

| Bo‘lim | Nima qiladi |
|---|---|
| Bosh sahifa | Reaktiv turlari, faol partiyalar, muddati yaqinlashgan / o‘tgan partiyalar, minimal qoldiqdan kamlar; holat, bo‘limlar va tumanlar diagrammalari; so‘nggi kirim va sarflar — hammasi bazadan |
| Barcha reaktivlar | Har bir partiya alohida qator; bir xil nomlilar ketma-ket, guruhlanadi (ochib-yopiladi), guruhda umumiy qoldiq; butun qator muddat rangida + holat matni; saralash, qidiruv, barcha filtrlar (kalendar bilan), ustunlarni yashirish / kengligi / tartibi + standart holatga qaytarish; tahrirlash, kirim, berish, tarix, arxivlash |
| Reaktiv qo‘shish | Reaktiv kartasi (nom avtomatik taklif bilan, formula, ishlab chiqaruvchi, seriya, sanalar, miqdor va birlik, bo‘lim, saqlash joyi, mas’ul, minimal qoldiq, izoh) + Excel shablon orqali import |
| Kirim operatsiyalari | Yangi partiya yoki mavjud partiyaga qo‘shimcha kirim; kirimlar ro‘yxati; bekor qilish / tuzatish (tarix saqlanadi) |
| Sarf va berish | Bitta amalda bir nechta reaktivni bir nechta bo‘lim/tumanga berish (bitta tranzaksiya — qisman saqlanmaydi); qoldiq yetmasa saqlanmaydi; FEFO tavsiyasi va boshqa partiya tanlansa ogohlantirish; muddati o‘tgan partiya faqat alohida tasdiq bilan; qaytarish, bekor qilish, tuzatish, hisobdan chiqarish |
| Bo‘limlar bo‘yicha hisob | Bo‘lim qo‘shish / nomini o‘zgartirish / faol-nofaol; har bo‘lim uchun qabul qilingan, berilgan, joriy qoldiq, partiyalar, sarf tarixi, hisobot |
| Tumanlarga berilgan reaktivlar | Tumanlar ro‘yxati (yangi nom yozilsa qo‘shiladi); har bir berish alohida; tuman va reaktiv kesimida jami, sanalar, partiyalar va muddatlar; davr bo‘yicha hisobot; qaytarish |
| Yaroqlilik muddati nazorati | Partiyalar qolgan kun bo‘yicha; holat bo‘yicha filtr; muddati o‘tganlar haqida ogohlantirish; hisobdan chiqarish |
| Umumiy zaxiralar | Har bir reaktiv nomi bo‘yicha jami (faqat mos birliklar qo‘shiladi: l + ml, kg + g; litr va kilogramm hech qachon qo‘shilmaydi), minimal qoldiq nazorati |
| Hisobotlar | 12 tur — Excel (.xlsx) va PDF; sana oralig‘i, nom, seriya, bo‘lim, tuman filtrlari; hisobotda yaratilgan vaqt va filtrlar |
| Operatsiyalar tarixi | Barcha amallar jurnali + har bir reaktiv / partiyaning qoldiqli to‘liq tarixi |
| Ma’lumotlarni zaxiralash | Zaxira nusxa yaratish, undan tiklash (oldin joriy baza avtomatik saqlanadi), avtomatik nusxalar ro‘yxati, eski dasturdan ko‘chirish |
| Sozlamalar | Tashkilot nomi (hisobotlarda), xodim nomi, nechta avtomatik nusxa saqlanishi, jadval ustunlarini tiklash, dastur haqida |

Ranglar — aniq kunlar bo‘yicha (qolgan kunlar = yaroqlilik muddati − bugun):

| Rang | Qolgan kun |
|---|---|
| 🟢 Yashil — «Yaroqli» | 90 kundan ko‘p |
| 🟡 Sariq — «1–3 oy qoldi» | 31–90 kun |
| 🔴 Qizil — «1 oy yoki kam qoldi» | 0–30 kun |
| ⛔ To‘q qizil — «Muddati o‘tgan» | 0 dan kam (o‘chirilmaydi, ishlatishdan oldin ogohlantiriladi) |

### Ma’lumotlar qayerda saqlanadi

- Baza (SQLite): `%APPDATA%\ReaktivControl\reaktiv-control.db` — dastur papkasidan alohida, yangilanganda yoki dastur boshqa joyga
  ko‘chirilganda yo‘qolmaydi. Linux: `~/.local/share/reaktiv-control/`.
- Avtomatik zaxira nusxalar: `…\ReaktivControl\backups\` (kuniga bitta, oxirgi 14 tasi; tiklashdan oldingi nusxalar ham shu yerda).
- Xatolar jurnali: `…\ReaktivControl\logs\reaktiv-control.0.log`.
- Har bir amal tranzaksiyada bajariladi; qoldiq faqat harakatlar jurnalidan hisoblanadi (bitta amal ikki marta hisoblanmaydi);
  xato amal o‘chirilmaydi — bekor qilinadi yoki tuzatiladi, tarix saqlanadi. Baza buzilsa, dastur ochilishda oxirgi zaxiradan tiklashni taklif qiladi.
- **Oldingi versiyadan (Reaktivlar 1.0.x) o‘tish:** birinchi ochilishda eski jadval (`%APPDATA%\Reaktivlar\reaktivlar-jadval.json`)
  avtomatik ko‘chiriladi; eski fayl o‘zgartirilmaydi. Bir xil qatorlar ikki marta ko‘chirilmaydi.

### O‘rnatish

GitHub → **Actions** → «Windows» → oxirgi muvaffaqiyatli ishga tushirish → **ReaktivControl-Windows-msi** artefaktini yuklab oling,
ichidagi `.msi` ni oching. Administrator huquqi kerak emas. Har yig‘ishning versiyasi yangi (`2.0.<raqam>`), yangisi eskisining (shu
jumladan eski «Reaktivlar» dasturining) ustiga o‘rnatiladi, ma’lumotlar saqlanib qoladi. O‘rnatuvchi ishlamasa:
**ReaktivControl-Windows-portable** artefaktini yuklab oling, zip faylni sichqonchaning o‘ng tugmasi bilan bosib
**«Извлечь все» / «Extract All»** ni tanlang, so‘ng `ReaktivControl` papkasidagi `ReaktivControl.exe` ni ishga tushiring (zip ichidan
to‘g‘ridan-to‘g‘ri ochilmaydi). Windows «noma’lum nashriyotchi» deb ogohlantirsa: **Batafsil → Baribir ishga tushirish**.

Linux (Debian, Ubuntu, Kali, Mint): **ReaktivControl-Linux-deb** artefaktidagi faylni o‘rnating —
`sudo apt install ./reaktivcontrol_*_amd64.deb` (eski `reaktivlar` paketi almashtiriladi). Menyuda «Ofis» bo‘limida yoki
`/opt/reaktivcontrol/bin/ReaktivControl`.

### Yig‘ish va tekshirish

```bash
cd desktop
./gradlew test                 # barcha testlar (Linux va Windows’da CI’da ham)
./gradlew run                  # dasturni ishga tushirish
./gradlew createDistributable  # o‘rnatuvchisiz dastur papkasi (build/compose/binaries/main/app/ReaktivControl)
./gradlew packageMsi           # Windows o‘rnatuvchisi — faqat Windows’da (JDK 17 + WiX avtomatik yuklanadi)
./gradlew packageDeb           # Linux .deb (Debian/Ubuntu’da)
```

Windows’da EXE yig‘ish (o‘z kompyuteringizda): [Temurin JDK 17](https://adoptium.net) ni o‘rnating, repozitoriyni yuklab oling,
`desktop` papkasida `gradlew.bat packageMsi createDistributable` ni bajaring. Natija: `build\compose\binaries\main\msi\*.msi` va
`build\compose\binaries\main\app\ReaktivControl\ReaktivControl.exe`.

O‘zini tekshirish: `ReaktivControl.exe --self-test natija.txt` — vaqtinchalik papkada bazani yaratadi, kirim/sarf, bo‘lim va
tumanlarga berish, qaytarish, bekor qilish, 12 hisobotni Excel va PDF ga, zaxira va tiklashni bajarib, natijani faylga yozadi
(foydalanuvchi ma’lumotlariga tegmaydi). CI har yig‘ishda buni portable EXE va o‘rnatilgan MSI uchun Windows’da bajaradi.
Testlar ro‘yxati va natijalari: [`desktop/TEST-RESULTS.md`](desktop/TEST-RESULTS.md).

### Cheklovlar

- Bir kompyuterda bitta foydalanuvchi uchun (tarmoq orqali bir nechta kompyuterdan bitta bazaga yozish yo‘q). Login/parol yo‘q.
- Dastur tili — o‘zbekcha (lotin).
- Eski (1.0.x) jadval dasturidagi «Excel kabi» kataklarga yozish o‘rniga endi forma va jadvallar ishlatiladi.

### Kod tuzilishi (desktop/src/main/kotlin/uz/reaktivombor/desktop)

| Paket | Vazifasi |
|---|---|
| `core/` | O‘lchov birliklari (aniq butun sonli hisob), muddat ranglari, matn yordamchilari, xatolar, ma’lumotlar papkasi, jurnal |
| `db/` | SQLite ulanishi, tranzaksiyalar, sxema va migratsiyalar (jadvallar, indekslar, cheklovlar, triggerlar) |
| `service/` | Reaktivlar va partiyalar, kirim/sarf/qaytarish, bo‘lim va tumanlar, so‘rovlar, audit, sozlamalar, zaxira, eski ma’lumotlarni ko‘chirish, Excel import |
| `report/` | 12 hisobot turi, Excel va PDF eksport |
| `ui/` | Mavzu, menyu va yuqori panel, umumiy komponentlar (jadval, kalendar, diagrammalar), 13 sahifa (`ui/pages/`) |
| `selftest/` | `--self-test` rejimi |
| `Main.kt` | Ishga tushirish: papka, qulf, jurnal, baza, avtomatik zaxira, oyna |

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
