@extends('layouts.app')

@section('title', 'Tahrirlash: '.$reagent->name)

@section('content')
    <div class="page-head">
        <h1>Tahrirlash</h1>
        <a href="{{ route('reagents.index') }}" class="btn btn--ghost">← Ro‘yxatga qaytish</a>
    </div>

    <form method="POST" action="{{ route('reagents.update', $reagent) }}" class="card">
        @method('PUT')
        @if ($errors->any())
            <div class="alert alert--error" role="alert">Saqlanmadi: qizil bilan belgilangan maydonlarni to‘g‘rilang.</div>
        @endif
        @include('reagents._form')

        <div class="form-actions">
            <button type="submit" class="btn btn--primary">Saqlash</button>
            <a href="{{ route('reagents.index') }}" class="btn btn--ghost">Bekor qilish</a>
        </div>
    </form>
@endsection
