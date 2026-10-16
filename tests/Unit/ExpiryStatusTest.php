<?php

namespace Tests\Unit;

use App\Enums\ExpiryStatus;
use Illuminate\Support\Carbon;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

class ExpiryStatusTest extends TestCase
{
    /**
     * @return array<string, array{string, string, ExpiryStatus}>
     */
    public static function boundaries(): array
    {
        return [
            'muddati o‘tgan' => ['2026-10-16', '2026-10-15', ExpiryStatus::Red],
            'bugun tugaydi' => ['2026-10-16', '2026-10-16', ExpiryStatus::Red],
            '1 oydan 1 kun kam' => ['2026-10-16', '2026-11-15', ExpiryStatus::Red],
            'roppa-rosa 1 oy' => ['2026-10-16', '2026-11-16', ExpiryStatus::Yellow],
            '2 oy' => ['2026-10-16', '2026-12-16', ExpiryStatus::Yellow],
            '3 oydan 1 kun kam' => ['2026-10-16', '2027-01-15', ExpiryStatus::Yellow],
            'roppa-rosa 3 oy' => ['2026-10-16', '2027-01-16', ExpiryStatus::Yellow],
            '3 oydan 1 kun ko‘p' => ['2026-10-16', '2027-01-17', ExpiryStatus::Green],
            '1 yil' => ['2026-10-16', '2027-10-16', ExpiryStatus::Green],

            // Oy oxiri: 30-noyabr + 3 oy = 28-fevral (2-martga “oshib ketmaydi”).
            'oy oxiri: roppa-rosa 3 oy' => ['2026-11-30', '2027-02-28', ExpiryStatus::Yellow],
            'oy oxiri: 3 oydan 1 kun ko‘p' => ['2026-11-30', '2027-03-01', ExpiryStatus::Green],
            'oy oxiri: 1 oydan 1 kun kam' => ['2027-01-31', '2027-02-27', ExpiryStatus::Red],
            'oy oxiri: roppa-rosa 1 oy' => ['2027-01-31', '2027-02-28', ExpiryStatus::Yellow],
        ];
    }

    #[DataProvider('boundaries')]
    public function test_status_boundaries(string $today, string $expiresAt, ExpiryStatus $expected): void
    {
        $this->assertSame($expected, ExpiryStatus::fromDate(Carbon::parse($expiresAt), Carbon::parse($today)));
    }

    public function test_time_of_day_is_ignored(): void
    {
        $this->assertSame(
            ExpiryStatus::Yellow,
            ExpiryStatus::fromDate(Carbon::parse('2026-11-16 00:00'), Carbon::parse('2026-10-16 23:59')),
        );
    }
}
