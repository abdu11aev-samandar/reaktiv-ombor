<?php

namespace App\Models;

use App\Enums\ExpiryStatus;
use Database\Factories\ReagentFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Carbon;

/**
 * @property int $id
 * @property string $name
 * @property Carbon $received_at
 * @property string $given_amount
 * @property string $unit
 * @property Carbon $expires_at
 * @property string $stock_amount
 */
class Reagent extends Model
{
    /** @use HasFactory<ReagentFactory> */
    use HasFactory;

    /** O‘lchov birliklari: qiymat => ko‘rinadigan nom. */
    public const UNITS = [
        'gr' => 'gr',
        'ml' => 'ml',
        'kg' => 'kg',
        'l' => 'l',
        'dona' => 'dona',
    ];

    protected $fillable = [
        'name',
        'received_at',
        'given_amount',
        'unit',
        'expires_at',
        'stock_amount',
    ];

    protected $attributes = [
        'unit' => 'gr',
    ];

    protected function casts(): array
    {
        return [
            'received_at' => 'date',
            'expires_at' => 'date',
            'given_amount' => 'decimal:3',
            'stock_amount' => 'decimal:3',
        ];
    }

    public function expiryStatus(): ExpiryStatus
    {
        return ExpiryStatus::fromDate($this->expires_at);
    }

    /** Muddat tugashiga necha kun qolgani (o‘tgan bo‘lsa manfiy). */
    public function daysUntilExpiry(): int
    {
        return (int) round(Carbon::today()->diffInDays($this->expires_at->copy()->startOfDay(), false));
    }

    public function expiryHint(): string
    {
        $days = $this->daysUntilExpiry();

        return match (true) {
            $days > 0 => $days.' kun qoldi',
            $days === 0 => 'Bugun tugaydi',
            default => 'Muddati '.abs($days).' kun oldin o‘tgan',
        };
    }

    /** "1 250,5 gr" ko‘rinishida; qiymat bo‘lmasa null. */
    public function formatAmount(string $attribute): ?string
    {
        $value = $this->getAttribute($attribute);

        if ($value === null || $value === '') {
            return null;
        }

        $number = rtrim(rtrim(number_format((float) $value, 3, ',', ' '), '0'), ',');

        return $number.' '.(self::UNITS[$this->unit] ?? $this->unit);
    }

    /** Forma uchun: "500.000" → "500", "2.500" → "2.5". */
    public function inputAmount(string $attribute): ?string
    {
        $value = $this->getAttribute($attribute);

        if ($value === null || $value === '') {
            return null;
        }

        return str_contains((string) $value, '.') ? rtrim(rtrim((string) $value, '0'), '.') : (string) $value;
    }
}
