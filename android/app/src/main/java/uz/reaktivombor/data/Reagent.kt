package uz.reaktivombor.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/** Daftardagi bitta qator: bitta reaktivning bitta kelgan partiyasi. */
@Entity(tableName = "reagents")
data class Reagent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val receivedAt: LocalDate,
    val givenAmount: BigDecimal,
    val unit: String,
    val expiresAt: LocalDate,
    val stockAmount: BigDecimal,
    val createdAt: Long,
    /** Oxirgi yuborilgan eslatma bosqichi (AlertStage.ordinal) — bir xil xabar qayta kelmasligi uchun. */
    val notifiedStage: Int = 0,
)
