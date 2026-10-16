@extends('layouts.app')

@section('title', 'Reaktivlar ro‘yxati')

@use('App\Enums\ExpiryStatus')

@php
    $currentQuery = array_filter([
        'q' => $search !== '' ? $search : null,
        'status' => $status?->value,
        'sort' => $sortByExpiry ? 'expiry' : null,
    ]);

    $urlWith = fn (array $changes) => route('reagents.index', array_filter(array_merge($currentQuery, $changes)));
@endphp

@section('content')
    <div class="page-head">
        <h1>Reaktivlar ro‘yxati</h1>
        <a href="{{ route('reagents.create') }}" class="btn btn--primary">+ Reaktiv qo‘shish</a>
    </div>

    <form method="GET" action="{{ route('reagents.index') }}" class="toolbar" role="search">
        @if ($status)
            <input type="hidden" name="status" value="{{ $status->value }}">
        @endif
        @if ($sortByExpiry)
            <input type="hidden" name="sort" value="expiry">
        @endif
        <input type="search" name="q" value="{{ $search }}" class="input" placeholder="Nomi bo‘yicha qidirish…" aria-label="Nomi bo‘yicha qidirish">
        <button type="submit" class="btn">Qidirish</button>
        @if ($search !== '')
            <a href="{{ $urlWith(['q' => null]) }}" class="btn btn--ghost">Tozalash</a>
        @endif
    </form>

    <nav class="filters" aria-label="Yaroqlilik muddati bo‘yicha filtr">
        <a href="{{ $urlWith(['status' => null]) }}" @class(['chip', 'chip--active' => $status === null]) @if ($status === null) aria-current="true" @endif>
            Hammasi <span class="chip__count">{{ $total }}</span>
        </a>
        @foreach (ExpiryStatus::ordered() as $option)
            <a href="{{ $urlWith(['status' => $option->value]) }}" @class(['chip', 'chip--active' => $status === $option]) @if ($status === $option) aria-current="true" @endif>
                <span class="dot dot--{{ $option->value }}" aria-hidden="true"></span>
                {{ $option->label() }}
                <span class="chip__count">{{ $counts[$option->value] ?? 0 }}</span>
            </a>
        @endforeach

        <a href="{{ $urlWith(['sort' => $sortByExpiry ? null : 'expiry']) }}" @class(['chip', 'chip--sort', 'chip--active' => $sortByExpiry]) @if ($sortByExpiry) aria-current="true" @endif>
            <span aria-hidden="true">{{ $sortByExpiry ? '▲' : '↕' }}</span>
            Muddati yaqinlari birinchi
        </a>
    </nav>

    @if ($reagents->isEmpty())
        <div class="empty">
            @if ($total === 0 && $search === '')
                <p>Hozircha reaktiv kiritilmagan.</p>
                <a href="{{ route('reagents.create') }}" class="btn btn--primary">Birinchi reaktivni qo‘shish</a>
            @else
                <p>Bu filtr bo‘yicha reaktiv topilmadi.</p>
            @endif
        </div>
    @else
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th class="col-num">№</th>
                        <th>Reaktiv nomi</th>
                        <th>Kelgan vaqt</th>
                        <th>Berilgan miqdor</th>
                        <th @if ($sortByExpiry) aria-sort="ascending" @endif>
                            <a href="{{ $urlWith(['sort' => $sortByExpiry ? null : 'expiry']) }}" class="sort-link"
                               title="{{ $sortByExpiry ? 'Kiritilgan tartibda ko‘rsatish' : 'Muddati yaqinlari birinchi' }}">
                                Yaroqlilik muddati <span aria-hidden="true">{{ $sortByExpiry ? '▲' : '↕' }}</span>
                            </a>
                        </th>
                        <th>Ombordagi miqdor</th>
                        <th class="col-actions"><span class="sr-only">Amallar</span></th>
                    </tr>
                </thead>
                <tbody>
                    @foreach ($reagents as $reagent)
                        @php($state = $reagent->expiryStatus())
                        <tr>
                            <td class="col-num" data-label="№">{{ $loop->iteration }}</td>
                            <td class="col-name" data-label="Reaktiv nomi">
                                <span class="name name--{{ $state->value }}" title="{{ $state->label() }}">{{ $reagent->name }}</span>
                            </td>
                            <td data-label="Kelgan vaqt">{{ $reagent->received_at->format('d.m.Y') }}</td>
                            <td data-label="Berilgan miqdor">{{ $reagent->formatAmount('given_amount') }}</td>
                            <td data-label="Yaroqlilik muddati">
                                <span>
                                    {{ $reagent->expires_at->format('d.m.Y') }}
                                    <small class="hint hint--{{ $state->value }}">{{ $reagent->expiryHint() }}</small>
                                </span>
                            </td>
                            <td data-label="Ombordagi miqdor">{{ $reagent->formatAmount('stock_amount') }}</td>
                            <td class="col-actions">
                                <a href="{{ route('reagents.edit', $reagent) }}" class="btn btn--small">Tahrirlash</a>
                                <form method="POST" action="{{ route('reagents.destroy', $reagent) }}"
                                      onsubmit="return confirm(@js('«'.$reagent->name.'» o‘chirilsinmi?'))">
                                    @csrf
                                    @method('DELETE')
                                    <button type="submit" class="btn btn--small btn--danger">O‘chirish</button>
                                </form>
                            </td>
                        </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
    @endif
@endsection
