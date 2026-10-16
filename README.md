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
