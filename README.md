# Reaktivlar hisobi

Laboratoriya reaktivlarini hisobga olish uchun oddiy Laravel ilova. Qog‘oz daftardagi jadvalning elektron varianti:

| № | Reaktiv nomi | Kelgan vaqt | Berilgan miqdor (gr/ml) | Yaroqlilik muddati | Ombordagi miqdor |
|---|--------------|-------------|-------------------------|--------------------|------------------|

Barcha ustunlar **qo‘lda kiritiladi** va **to‘ldirilishi majburiy** (birortasi bo‘sh bo‘lsa, forma saqlanmaydi va o‘zbekcha xato ko‘rsatiladi). Reaktiv nomi yaroqlilik muddatiga qarab avtomatik bo‘yaladi:

| Rang | Qachon |
|------|--------|
| 🟢 Yashil | muddat tugashiga **3 oy yoki undan ko‘p** qolgan |
| 🟡 Sariq | **1 oydan 3 oygacha** qolgan |
| 🔴 Qizil | **1 oydan kam** qolgan yoki muddati **o‘tib ketgan** |

Oylar kalendar bo‘yicha hisoblanadi (masalan, bugun 16-oktabr bo‘lsa: 16-noyabrgacha qizil, 16-yanvargacha sariq, undan keyin yashil). Ranglar har kuni o‘z-o‘zidan yangilanadi — hech narsa bosish shart emas.

## Imkoniyatlar

- Reaktiv qo‘shish, tahrirlash, o‘chirish (bitta reaktivning har bir kelgan partiyasi alohida qator bo‘lishi mumkin)
- «Saqlash va yana qo‘shish» — daftardagi ro‘yxatni ketma-ket kiritish uchun
- Yaroqlilik muddati kelgan vaqtdan oldin bo‘lsa yoki miqdor manfiy bo‘lsa — xato
- Rang bo‘yicha filtr (har bir rangda nechta reaktiv borligi ko‘rinadi) va nomi bo‘yicha qidiruv
- «Yaroqlilik muddati» ustuni bosilsa — muddati yaqinlari birinchi chiqadi
- Har bir reaktiv uchun «N kun qoldi» / «Muddati N kun oldin o‘tgan» yozuvi
- Miqdorni vergul bilan ham yozish mumkin: `2,5`
- Telefonda kartochka ko‘rinishida, printerda jadval ko‘rinishida (rangi bilan) chiqadi
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

Odatiy Laravel kabi: domen `public/` papkaga qaratiladi, so‘ng

```bash
composer install --no-dev --optimize-autoloader
php artisan migrate --force
php artisan config:cache && php artisan route:cache && php artisan view:cache
```

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
