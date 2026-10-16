<?php

namespace Database\Factories;

use App\Models\Reagent;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Reagent>
 */
class ReagentFactory extends Factory
{
    public function definition(): array
    {
        $given = fake()->numberBetween(100, 2000);

        return [
            'name' => fake()->unique()->word().' '.fake()->numberBetween(1, 99),
            'received_at' => today()->subMonths(2),
            'given_amount' => $given,
            'unit' => fake()->randomElement(array_keys(Reagent::UNITS)),
            'expires_at' => today()->addYear(),
            'stock_amount' => fake()->numberBetween(0, $given),
        ];
    }

    public function expiresIn(string $interval): static
    {
        return $this->state(fn () => ['expires_at' => today()->add($interval)]);
    }
}
