package uz.reaktivombor.desktop.core

/**
 * Foydalanuvchi tuzata oladigan xato: xabari (o‘zbekcha) ekranda o‘zgarishsiz ko‘rsatiladi.
 * [fields] — forma maydonlari bo‘yicha xabarlar (maydon nomi → xabar), xabarni maydon yonida ko‘rsatish uchun.
 */
open class UserError(message: String, val fields: Map<String, String> = emptyMap()) : RuntimeException(message) {
    constructor(message: String, field: String?) : this(message, if (field == null) emptyMap() else mapOf(field to message))

    override val message: String get() = super.message ?: ""
}

/**
 * Bunday partiya allaqachon bor. [exact] — nom, seriya, muddat va birlik to‘liq bir xil (yangi partiya ochib bo‘lmaydi,
 * miqdor shu partiyaga qo‘shimcha kirim bo‘ladi). false — seriya bir xil, muddat yoki birlik boshqa: tasdiqlansa saqlanadi.
 */
class DuplicateBatchError(val existingBatchId: Long, val exact: Boolean, message: String) : UserError(message)

/** Partiyada yetarli qoldiq yo‘q — operatsiyaning hech bir qatori saqlanmaydi. Miqdorlar asosiy birlikda. */
class InsufficientStockError(
    val batchId: Long,
    val available: Long,
    val requested: Long,
    val unit: MeasureUnit,
    message: String,
) : UserError(message)

/** Muddati o‘tgan partiyadan berish — alohida tasdiq talab qilinadi. */
class ExpiredBatchError(val batchId: Long, message: String) : UserError(message)

/** Xuddi shunday operatsiya hozirgina saqlangan (ikki marta bosilgan bo‘lishi mumkin) — tasdiqlansa saqlanadi. */
class DuplicateOperationError(message: String) : UserError(message)
