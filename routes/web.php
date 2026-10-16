<?php

use App\Http\Controllers\ReagentController;
use Illuminate\Support\Facades\Route;

Route::redirect('/', '/reaktivlar');

Route::resource('reaktivlar', ReagentController::class)
    ->except('show')
    ->parameters(['reaktivlar' => 'reagent'])
    ->names('reagents');
