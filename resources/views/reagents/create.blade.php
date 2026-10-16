@extends('layouts.app')

@section('title', 'Reaktiv qo‘shish')

@section('content')
    <div class="page-head">
        <h1>Reaktiv qo‘shish</h1>
        <a href="{{ route('reagents.index') }}" class="btn btn--ghost">← Ro‘yxatga qaytish</a>
    </div>

    <form method="POST" action="{{ route('reagents.store') }}" class="card">
        @if ($errors->any())
            <div class="alert alert--error" role="alert">Saqlanmadi: qizil bilan belgilangan maydonlarni to‘g‘rilang.</div>
        @endif
        @include('reagents._form')

        <div class="form-actions">
            <button type="submit" class="btn btn--primary">Saqlash</button>
            <button type="submit" name="action" value="save_and_new" class="btn">Saqlash va yana qo‘shish</button>
            <a href="{{ route('reagents.index') }}" class="btn btn--ghost">Bekor qilish</a>
        </div>
    </form>
@endsection
