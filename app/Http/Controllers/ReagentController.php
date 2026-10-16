<?php

namespace App\Http\Controllers;

use App\Enums\ExpiryStatus;
use App\Http\Requests\ReagentRequest;
use App\Models\Reagent;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\View\View;

class ReagentController extends Controller
{
    public function index(Request $request): View
    {
        $search = trim((string) $request->query('q', ''));
        $status = ExpiryStatus::tryFrom((string) $request->query('status', ''));
        $sortByExpiry = $request->query('sort') === 'expiry';

        $reagents = Reagent::query()
            ->when($search !== '', fn ($query) => $query->where('name', 'like', '%'.$search.'%'))
            ->orderBy('id')
            ->get();

        $counts = $reagents->countBy(fn (Reagent $reagent) => $reagent->expiryStatus()->value);

        $visible = $reagents
            ->when($status, fn ($items) => $items->filter(
                fn (Reagent $reagent) => $reagent->expiryStatus() === $status
            ))
            ->when($sortByExpiry, fn ($items) => $items->sortBy(
                fn (Reagent $reagent) => $reagent->expires_at->getTimestamp()
            ))
            ->values();

        return view('reagents.index', [
            'reagents' => $visible,
            'total' => $reagents->count(),
            'counts' => $counts,
            'search' => $search,
            'status' => $status,
            'sortByExpiry' => $sortByExpiry,
        ]);
    }

    public function create(): View
    {
        return view('reagents.create', [
            'reagent' => new Reagent,
            'knownNames' => $this->knownNames(),
        ]);
    }

    public function store(ReagentRequest $request): RedirectResponse
    {
        $reagent = Reagent::create($request->validated());

        $message = '«'.$reagent->name.'» qo‘shildi.';

        if ($request->input('action') === 'save_and_new') {
            return to_route('reagents.create')->with('success', $message);
        }

        return to_route('reagents.index')->with('success', $message);
    }

    public function edit(Reagent $reagent): View
    {
        return view('reagents.edit', [
            'reagent' => $reagent,
            'knownNames' => $this->knownNames(),
        ]);
    }

    public function update(ReagentRequest $request, Reagent $reagent): RedirectResponse
    {
        $reagent->update($request->validated());

        return to_route('reagents.index')->with('success', '«'.$reagent->name.'» saqlandi.');
    }

    public function destroy(Reagent $reagent): RedirectResponse
    {
        $reagent->delete();

        return to_route('reagents.index')->with('success', '«'.$reagent->name.'» o‘chirildi.');
    }

    /**
     * Avval kiritilgan nomlar — formada taklif sifatida chiqadi (yozish baribir qo‘lda).
     *
     * @return Collection<int, string>
     */
    private function knownNames(): Collection
    {
        return Reagent::query()->distinct()->orderBy('name')->pluck('name');
    }
}
