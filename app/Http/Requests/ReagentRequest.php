<?php

namespace App\Http\Requests;

use App\Models\Reagent;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class ReagentRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    /**
     * "2,5" kabi vergulli sonlarni "2.5" ga aylantiradi.
     */
    protected function prepareForValidation(): void
    {
        $normalized = [];

        foreach (['given_amount', 'stock_amount'] as $field) {
            $value = $this->input($field);

            if (is_string($value)) {
                $normalized[$field] = str_replace([' ', "\u{00A0}", ','], ['', '', '.'], trim($value));
            }
        }

        $this->merge($normalized);
    }

    public function rules(): array
    {
        return [
            'name' => ['required', 'string', 'max:255'],
            'received_at' => ['required', 'date'],
            'given_amount' => ['required', 'numeric', 'min:0', 'max:999999999'],
            'unit' => ['required', Rule::in(array_keys(Reagent::UNITS))],
            'expires_at' => ['required', 'date', 'after_or_equal:received_at'],
            'stock_amount' => ['required', 'numeric', 'min:0', 'max:999999999'],
        ];
    }

    public function attributes(): array
    {
        return [
            'name' => 'Reaktiv nomi',
            'received_at' => 'Kelgan vaqt',
            'given_amount' => 'Berilgan miqdor',
            'unit' => 'O‘lchov birligi',
            'expires_at' => 'Yaroqlilik muddati',
            'stock_amount' => 'Ombordagi miqdor',
        ];
    }

    public function messages(): array
    {
        return [
            'required' => ':attribute kiritilishi shart.',
            'string' => ':attribute matn bo‘lishi kerak.',
            'name.max' => ':attribute :max belgidan oshmasligi kerak.',
            'date' => ':attribute to‘g‘ri sana bo‘lishi kerak.',
            'numeric' => ':attribute son bo‘lishi kerak.',
            'min' => ':attribute manfiy bo‘lishi mumkin emas.',
            'max' => ':attribute juda katta.',
            'in' => ':attribute ro‘yxatdan tanlanishi kerak.',
            'expires_at.after_or_equal' => 'Yaroqlilik muddati kelgan vaqtdan oldin bo‘lishi mumkin emas.',
        ];
    }
}
