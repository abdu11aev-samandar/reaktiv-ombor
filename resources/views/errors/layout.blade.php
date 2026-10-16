<!DOCTYPE html>
<html lang="uz">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title') — {{ config('app.name') }}</title>
    <link rel="stylesheet" href="{{ asset('css/app.css') }}">
</head>
<body>
    <header class="topbar">
        <div class="container topbar__inner">
            <a href="{{ url('/') }}" class="brand">
                <span class="brand__icon" aria-hidden="true">⚗</span>
                {{ config('app.name') }}
            </a>
        </div>
    </header>

    <main class="container">
        <div class="empty error-page">
            <p class="error-page__code">@yield('code')</p>
            <h1>@yield('title')</h1>
            <p>@yield('message')</p>
            <a href="{{ url('/') }}" class="btn btn--primary">Ro‘yxatga qaytish</a>
        </div>
    </main>
</body>
</html>
