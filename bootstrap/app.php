<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Symfony\Component\HttpKernel\Exception\HttpException;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        //
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );

        // Forma uzoq ochiq qolib, sessiya eskirgan bo‘lsa (419) — kiritilganlar yo‘qolmasin.
        $exceptions->render(function (HttpException $e, Request $request) {
            if ($e->getStatusCode() === 419 && ! $request->isMethod('GET') && ! $request->expectsJson()) {
                return back()
                    ->withInput($request->except(['_token', '_method']))
                    ->with('error', 'Sahifa uzoq ochiq turgani uchun amal bajarilmadi. Qayta urinib ko‘ring — kiritgan ma’lumotlaringiz joyida.');
            }
        });
    })->create();
