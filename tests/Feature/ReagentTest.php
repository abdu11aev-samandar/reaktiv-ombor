<?php

namespace Tests\Feature;

use App\Models\Reagent;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Tests\TestCase;

class ReagentTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        $this->travelTo(Carbon::parse('2026-10-16 10:00'));
    }

    private function validData(array $overrides = []): array
    {
        return array_merge([
            'name' => 'Etil spirti',
            'received_at' => '2026-09-01',
            'given_amount' => '1000',
            'unit' => 'ml',
            'expires_at' => '2027-09-01',
            'stock_amount' => '750',
        ], $overrides);
    }

    public function test_home_redirects_to_reagent_list(): void
    {
        $this->get('/')->assertRedirect('/reaktivlar');
    }

    public function test_empty_list_invites_to_add_first_reagent(): void
    {
        $this->get(route('reagents.index'))
            ->assertOk()
            ->assertSee('Hozircha reaktiv kiritilmagan');
    }

    public function test_reagent_name_is_coloured_by_expiry_date(): void
    {
        Reagent::factory()->create(['name' => 'Xloroform', 'expires_at' => '2027-03-01']);     // 4,5 oy
        Reagent::factory()->create(['name' => 'Formalin', 'expires_at' => '2026-12-01']);      // 1,5 oy
        Reagent::factory()->create(['name' => 'Ammiak', 'expires_at' => '2026-11-01']);        // 16 kun
        Reagent::factory()->create(['name' => 'Jelatin', 'expires_at' => '2026-10-01']);       // o‘tgan

        $response = $this->get(route('reagents.index'))->assertOk();

        $response->assertSee('name--green" title="3 oydan ko‘p">Xloroform', false);
        $response->assertSee('name--yellow" title="1 – 3 oy">Formalin', false);
        $response->assertSee('name--red" title="1 oydan kam">Ammiak', false);
        $response->assertSee('name--red" title="1 oydan kam">Jelatin', false);

        $response->assertSee('16 kun qoldi');
        $response->assertSee('Muddati 15 kun oldin o‘tgan');
    }

    public function test_list_shows_all_columns_formatted(): void
    {
        Reagent::factory()->create([
            'name' => 'Natriy karbonat',
            'received_at' => '2026-09-05',
            'given_amount' => '1250.5',
            'unit' => 'gr',
            'expires_at' => '2027-09-05',
            'stock_amount' => '800',
        ]);

        $this->get(route('reagents.index'))
            ->assertOk()
            ->assertSeeInOrder(['Natriy karbonat', '05.09.2026', '1 250,5 gr', '05.09.2027', '800 gr']);
    }

    public function test_reagent_can_be_created(): void
    {
        $this->post(route('reagents.store'), $this->validData())
            ->assertRedirect(route('reagents.index'))
            ->assertSessionHas('success');

        $reagent = Reagent::sole();
        $this->assertSame('Etil spirti', $reagent->name);
        $this->assertSame('2026-09-01', $reagent->received_at->toDateString());
        $this->assertSame('2027-09-01', $reagent->expires_at->toDateString());
        $this->assertEquals(1000, $reagent->given_amount);
        $this->assertEquals(750, $reagent->stock_amount);
        $this->assertSame('ml', $reagent->unit);
    }

    public function test_all_fields_are_required(): void
    {
        $this->post(route('reagents.store'), ['name' => 'Shavel kislota', 'unit' => 'gr'])
            ->assertSessionHasErrors([
                'received_at' => 'Kelgan vaqt kiritilishi shart.',
                'given_amount' => 'Berilgan miqdor kiritilishi shart.',
                'expires_at' => 'Yaroqlilik muddati kiritilishi shart.',
                'stock_amount' => 'Ombordagi miqdor kiritilishi shart.',
            ]);

        $this->assertDatabaseCount('reagents', 0);
    }

    public function test_missing_received_date_reports_only_the_missing_field(): void
    {
        $this->post(route('reagents.store'), $this->validData(['received_at' => null]))
            ->assertSessionHasErrors(['received_at'])
            ->assertSessionDoesntHaveErrors(['expires_at']);
    }

    public function test_save_and_new_returns_to_create_form(): void
    {
        $this->post(route('reagents.store'), $this->validData(['action' => 'save_and_new']))
            ->assertRedirect(route('reagents.create'));
    }

    public function test_decimal_comma_is_accepted(): void
    {
        $this->post(route('reagents.store'), $this->validData(['given_amount' => '2,5', 'stock_amount' => ' 1 200,75 ']))
            ->assertSessionHasNoErrors();

        $reagent = Reagent::sole();
        $this->assertEquals(2.5, $reagent->given_amount);
        $this->assertEquals(1200.75, $reagent->stock_amount);
    }

    public function test_validation_errors_are_in_uzbek(): void
    {
        $this->from(route('reagents.create'))
            ->post(route('reagents.store'), $this->validData([
                'name' => '',
                'given_amount' => 'ko‘p',
                'stock_amount' => '-5',
                'unit' => 'tonna',
                'received_at' => '2026-10-10',
                'expires_at' => '2026-10-01',
            ]))
            ->assertRedirect(route('reagents.create'))
            ->assertSessionHasErrors([
                'name' => 'Reaktiv nomi kiritilishi shart.',
                'given_amount' => 'Berilgan miqdor son bo‘lishi kerak.',
                'stock_amount' => 'Ombordagi miqdor manfiy bo‘lishi mumkin emas.',
                'unit' => 'O‘lchov birligi ro‘yxatdan tanlanishi kerak.',
                'expires_at' => 'Yaroqlilik muddati kelgan vaqtdan oldin bo‘lishi mumkin emas.',
            ]);

        $this->assertDatabaseCount('reagents', 0);
    }

    public function test_create_and_edit_forms_render(): void
    {
        $reagent = Reagent::factory()->create(['name' => 'Kaliy gidroksid', 'given_amount' => '500', 'unit' => 'gr']);

        $this->get(route('reagents.create'))
            ->assertOk()
            ->assertSee('Reaktiv qo‘shish')
            ->assertSee('<option value="Kaliy gidroksid">', false);

        $this->get(route('reagents.edit', $reagent))
            ->assertOk()
            ->assertSee('value="Kaliy gidroksid"', false)
            ->assertSee('value="500"', false);
    }

    public function test_reagent_can_be_updated(): void
    {
        $reagent = Reagent::factory()->create(['name' => 'Etilatsetat']);

        $this->put(route('reagents.update', $reagent), $this->validData(['name' => 'Etilatsetat', 'stock_amount' => '120']))
            ->assertRedirect(route('reagents.index'));

        $this->assertEquals(120, $reagent->fresh()->stock_amount);
    }

    public function test_reagent_can_be_deleted(): void
    {
        $reagent = Reagent::factory()->create();

        $this->delete(route('reagents.destroy', $reagent))
            ->assertRedirect(route('reagents.index'));

        $this->assertModelMissing($reagent);
    }

    public function test_list_can_be_filtered_by_colour(): void
    {
        Reagent::factory()->create(['name' => 'Yashil reaktiv', 'expires_at' => '2027-06-01']);
        Reagent::factory()->create(['name' => 'Qizil reaktiv', 'expires_at' => '2026-10-20']);

        $this->get(route('reagents.index', ['status' => 'red']))
            ->assertOk()
            ->assertSee('Qizil reaktiv')
            ->assertDontSee('Yashil reaktiv');
    }

    public function test_list_can_be_searched_by_name(): void
    {
        Reagent::factory()->create(['name' => 'Natriy sulfat']);
        Reagent::factory()->create(['name' => 'Ammiak']);

        $this->get(route('reagents.index', ['q' => 'natriy']))
            ->assertOk()
            ->assertSee('Natriy sulfat')
            ->assertDontSee('Ammiak');
    }

    public function test_list_can_be_sorted_by_expiry(): void
    {
        Reagent::factory()->create(['name' => 'Kech', 'expires_at' => '2028-01-01']);
        Reagent::factory()->create(['name' => 'Orta', 'expires_at' => '2027-02-01']);
        Reagent::factory()->create(['name' => 'Erta', 'expires_at' => '2026-11-01']);

        $this->get(route('reagents.index', ['sort' => 'expiry']))
            ->assertOk()
            ->assertSeeInOrder(['>Erta<', '>Orta<', '>Kech<'], false);
    }
}
