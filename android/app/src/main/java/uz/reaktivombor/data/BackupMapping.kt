package uz.reaktivombor.data

import uz.reaktivombor.domain.AlertStage
import uz.reaktivombor.domain.BackupItem
import java.time.LocalDate

fun Reagent.toBackupItem() = BackupItem(
    name = name,
    receivedAt = receivedAt,
    givenAmount = givenAmount,
    unit = unit,
    expiresAt = expiresAt,
    stockAmount = stockAmount,
    createdAt = createdAt,
)

/** Tiklangan qator uchun eski eslatmalar qayta kelmasin — hozirgi bosqich “xabar berilgan” deb belgilanadi. */
fun BackupItem.toReagent(today: LocalDate) = Reagent(
    name = name,
    receivedAt = receivedAt,
    givenAmount = givenAmount,
    unit = unit,
    expiresAt = expiresAt,
    stockAmount = stockAmount,
    createdAt = createdAt,
    notifiedStage = AlertStage.of(expiresAt, today).ordinal,
)
