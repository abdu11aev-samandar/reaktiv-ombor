<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('reagents', function (Blueprint $table) {
            $table->id();
            $table->string('name');                                // Reaktiv nomi
            $table->date('received_at');                           // Kelgan vaqt
            $table->decimal('given_amount', 12, 3);                // Berilgan miqdor
            $table->string('unit', 10)->default('gr');             // gr / ml / ...
            $table->date('expires_at')->index();                   // Yaroqlilik muddati
            $table->decimal('stock_amount', 12, 3);                // Ombordagi miqdor
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('reagents');
    }
};
