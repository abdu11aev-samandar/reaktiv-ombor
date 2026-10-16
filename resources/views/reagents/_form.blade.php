@csrf

@php
    // old() massiv qaytarsa (buzilgan so‘rov), uni e’tiborsiz qoldiramiz.
    $old = fn (string $key, $default = null) => is_array($value = old($key, $default)) ? $default : $value;
@endphp

<div class="form-grid">
    <div class="field field--wide">
        <label for="name" class="field__label">Reaktiv nomi <span class="req" aria-hidden="true">*</span></label>
        <input type="text" id="name" name="name" value="{{ $old('name', $reagent->name) }}"
               class="input" maxlength="255" required @if (! $errors->any() || $errors->has('name')) autofocus @endif autocomplete="off" list="known-names"
               @error('name') aria-invalid="true" aria-describedby="name-error" @enderror>
        @if ($knownNames->isNotEmpty())
            <datalist id="known-names">
                @foreach ($knownNames as $knownName)
                    <option value="{{ $knownName }}"></option>
                @endforeach
            </datalist>
        @endif
        @error('name') <p class="field__error" id="name-error">{{ $message }}</p> @enderror
    </div>

    <div class="field">
        <label for="received_at" class="field__label">Kelgan vaqt <span class="req" aria-hidden="true">*</span></label>
        <input type="date" id="received_at" name="received_at" class="input" required
               value="{{ $old('received_at', $reagent->received_at?->format('Y-m-d')) }}"
               @error('received_at') aria-invalid="true" aria-describedby="received_at-error" @enderror>
        @error('received_at') <p class="field__error" id="received_at-error">{{ $message }}</p> @enderror
    </div>

    <div class="field">
        <label for="expires_at" class="field__label">Yaroqlilik muddati <span class="req" aria-hidden="true">*</span></label>
        <input type="date" id="expires_at" name="expires_at" class="input" required
               value="{{ $old('expires_at', $reagent->expires_at?->format('Y-m-d')) }}"
               @error('expires_at') aria-invalid="true" aria-describedby="expires_at-error" @enderror>
        @error('expires_at') <p class="field__error" id="expires_at-error">{{ $message }}</p> @enderror
    </div>

    <div class="field">
        <label for="given_amount" class="field__label">Berilgan miqdor <span class="req" aria-hidden="true">*</span></label>
        <input type="text" id="given_amount" name="given_amount" class="input" inputmode="decimal" required
               value="{{ $old('given_amount', $reagent->inputAmount('given_amount')) }}" placeholder="masalan: 500"
               @error('given_amount') aria-invalid="true" aria-describedby="given_amount-error" @enderror>
        @error('given_amount') <p class="field__error" id="given_amount-error">{{ $message }}</p> @enderror
    </div>

    <div class="field">
        <label for="stock_amount" class="field__label">Ombordagi miqdor <span class="req" aria-hidden="true">*</span></label>
        <input type="text" id="stock_amount" name="stock_amount" class="input" inputmode="decimal" required
               value="{{ $old('stock_amount', $reagent->inputAmount('stock_amount')) }}" placeholder="masalan: 250"
               @error('stock_amount') aria-invalid="true" aria-describedby="stock_amount-error" @enderror>
        @error('stock_amount') <p class="field__error" id="stock_amount-error">{{ $message }}</p> @enderror
    </div>

    <div class="field">
        <label for="unit" class="field__label">O‘lchov birligi <span class="req" aria-hidden="true">*</span></label>
        <select id="unit" name="unit" class="input" required
                @error('unit') aria-invalid="true" aria-describedby="unit-error" @enderror>
            @foreach (\App\Models\Reagent::UNITS as $value => $label)
                <option value="{{ $value }}" @selected($old('unit', $reagent->unit) === $value)>{{ $label }}</option>
            @endforeach
        </select>
        @error('unit') <p class="field__error" id="unit-error">{{ $message }}</p> @enderror
    </div>
</div>

<p class="form-note">
    <span class="req" aria-hidden="true">*</span> — barcha maydonlar to‘ldirilishi shart.
    Rang yaroqlilik muddatiga qarab avtomatik qo‘yiladi:
    <span class="dot dot--green" aria-hidden="true"></span> 3 oydan ko‘p,
    <span class="dot dot--yellow" aria-hidden="true"></span> 1–3 oy,
    <span class="dot dot--red" aria-hidden="true"></span> 1 oydan kam yoki o‘tgan.
</p>
