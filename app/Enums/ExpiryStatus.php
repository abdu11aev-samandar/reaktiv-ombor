<?php

namespace App\Enums;

use Carbon\CarbonInterface;
use Illuminate\Support\Carbon;

/**
 * Reaktivning yaroqlilik muddati bo‘yicha holati (rangi).
 *
 *  - 3 oy va undan ko‘p qolgan       → yashil
 *  - 1 oydan 3 oygacha qolgan        → sariq
 *  - 1 oydan kam qolgan yoki o‘tgan  → qizil
 */
enum ExpiryStatus: string
{
    case Green = 'green';
    case Yellow = 'yellow';
    case Red = 'red';

    /** Shuncha oydan ko‘p qolgan bo‘lsa — yashil. */
    public const GREEN_FROM_MONTHS = 3;

    /** Shuncha oydan kam qolgan bo‘lsa — qizil. */
    public const RED_BELOW_MONTHS = 1;

    public static function fromDate(CarbonInterface $expiresAt, ?CarbonInterface $today = null): self
    {
        $today = Carbon::instance($today ?? Carbon::today())->startOfDay();
        $expiresAt = Carbon::instance($expiresAt)->startOfDay();

        // addMonthsNoOverflow: 30-noyabr + 3 oy = 28-fevral (2-mart emas).
        if ($expiresAt->lt($today->copy()->addMonthsNoOverflow(self::RED_BELOW_MONTHS))) {
            return self::Red;
        }

        if ($expiresAt->lt($today->copy()->addMonthsNoOverflow(self::GREEN_FROM_MONTHS))) {
            return self::Yellow;
        }

        return self::Green;
    }

    public function label(): string
    {
        return match ($this) {
            self::Green => '3 oydan ko‘p',
            self::Yellow => '1 – 3 oy',
            self::Red => '1 oydan kam',
        };
    }

    /**
     * Filtr tugmalari tartibi.
     *
     * @return list<self>
     */
    public static function ordered(): array
    {
        return [self::Red, self::Yellow, self::Green];
    }
}
