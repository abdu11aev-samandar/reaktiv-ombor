<!DOCTYPE html>
<html lang="uz">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title', 'Reaktivlar') — {{ config('app.name') }}</title>
    <link rel="stylesheet" href="{{ asset('css/app.css') }}?v={{ filemtime(public_path('css/app.css')) }}">
</head>
<body>
    <header class="topbar">
        <div class="container topbar__inner">
            <a href="{{ route('reagents.index') }}" class="brand">
                <span class="brand__icon" aria-hidden="true">⚗</span>
                {{ config('app.name') }}
            </a>
            <span class="topbar__date">Bugun: {{ today()->format('d.m.Y') }}</span>
        </div>
    </header>

    <main class="container">
        @if (session('success'))
            <div class="alert alert--success" role="status">{{ session('success') }}</div>
        @endif

        @yield('content')
    </main>
</body>
</html>
