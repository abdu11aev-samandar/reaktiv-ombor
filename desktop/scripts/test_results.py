#!/usr/bin/env python3
"""Gradle JUnit natijalaridan (build/test-results/test/*.xml) TEST-RESULTS.md yaratadi.

Topshiriqning 18-bandidagi har bir majburiy holat uchun uni tekshiradigan testlar va ularning HAQIQIY natijasi yoziladi.
Ishga tushmagan test «o‘tdi» deb ko‘rsatilmaydi — «ishga tushmagan» deb yoziladi.

    cd desktop && ./gradlew test && python3 scripts/test_results.py > TEST-RESULTS.md
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime

# 18-band: holat → uni tekshiradigan testlar (Sinf.metod).
SPEC_CASES = [
    ("Yangi reaktiv qo‘shish", [
        "ReagentsServiceTest.newReagentCreatesReagentBatchAndInitialReceipt",
        "PagesUiTest.addReagentValidatesThenSaves"]),
    ("Bir xil nomdagi turli seriyali partiyalarni yaratish", [
        "ReagentsServiceTest.sameNameDifferentSeriesCreatesSeparateBatches"]),
    ("Turli yaroqlilik muddatlarini alohida ko‘rsatish", [
        "ReagentsServiceTest.differentExpiriesAreSeparateBatchesWithTheirOwnState",
        "PlacesPagesUiTest.batchesDialogShowsEveryBatchWithItsOwnState"]),
    ("Bir xil nomdagi reaktivlarning umumiy qoldig‘ini hisoblash (Etil spirti: 19 l / 14 l)", [
        "ReagentsServiceTest.groupTotalOfTheEtilSpirtiExample",
        "ReportsTest.etilSpirtiNumbers"]),
    ("Bir xil nomdagi partiyalarni ketma-ket joylashtirish", [
        "ReagentsServiceTest.rowsOfTheSameNameAreAdjacentAndOrderedByExpiry",
        "PagesUiTest.sameNamesAreAdjacentInFefoOrderWithGroupTotal"]),
    ("Kirim va sarfni to‘g‘ri hisoblash", [
        "StockServiceTest.receiptAndIssueMath",
        "ReceiptsPageUiTest.extraReceiptForAnExistingBatchAddsToItsRemainder"]),
    ("Bir reaktivni bir nechta bo‘limga bir vaqtda berish (200 / 300 / 150 / 100 ml)", [
        "StockServiceTest.oneReagentToFourDepartmentsAtOnceInOneTransaction",
        "IssuesPageUiTest.fourLinesFromALitreBatchAreSavedAsOneOperation"]),
    ("Tumanga reaktiv berish", [
        "StockServiceTest.issueToADistrictCreatesTheDistrictByName",
        "IssuesPageUiTest.aNewDistrictTypedInALineIsCreated"]),
    ("Turli sanalarda bir tumanga bir necha marta berish", [
        "StockServiceTest.severalIssuesToOneDistrictOnDifferentDates",
        "PlacesPagesUiTest.districtShowsEveryIssueSeparatelyAndPerReagentTotals"]),
    ("Omborda yetarli miqdor bo‘lmaganda operatsiyani bloklash", [
        "StockServiceTest.insufficientStockBlocksTheWholeMultiLineOperation",
        "IssuesPageUiTest.insufficientStockOnOneLineSavesNothing"]),
    ("Qaytarish operatsiyasi", [
        "StockServiceTest.returnPutsStockBackIntoTheBatch",
        "StockServiceTest.returningMoreThanWasIssuedIsRefused",
        "IssuesPageUiTest.returnDialogIncreasesTheRemainder"]),
    ("Yashil, sariq va qizil ranglar chegaralari (91 / 90 / 31 / 30 / 0 / −1 kun)", [
        "ExpiryTest.colourThresholdsAreExactDays"]),
    ("Muddati o‘tgan reaktivlarni aniqlash", [
        "ExpiryTest.expiredDetectionAndDaysLeft",
        "QueriesServiceTest.notificationsListExpiredRedBelowMinAndYellow"]),
    ("Yangi bo‘lim va yangi tuman qo‘shish", [
        "DirectoryServiceTest.addDepartmentDedupesByKey",
        "DirectoryServiceTest.addDistrictDedupesByKeyAndReactivates",
        "PlacesPagesUiTest.departmentCanBeAddedRenamedDeactivatedAndActivated",
        "PlacesPagesUiTest.districtCanBeAddedRenamedAndDeactivated"]),
    ("Excel eksporti", [
        "ExcelExportTest.writesTitleHeaderDatesNumbersAndUzbekText",
        "ReportsPageUiTest.excelSaveWithoutPreviewBuildsAndWritesAReadableFile"]),
    ("PDF eksporti", [
        "PdfExportTest.titleOrganisationFiltersAndUzbekLettersAreInTheText",
        "ReportsPageUiTest.pdfSaveWritesAReadablePdfWithTheOrganisation"]),
    ("Zaxira nusxa yaratish va tiklash", [
        "BackupServiceTest.createValidateAndRestoreWithAutomaticPreRestoreBackup",
        "BackupSettingsUiTest.restoreFromFileReplacesTheDataAndKeepsThePreviousState"]),
    ("Dastur yopilib qayta ochilganda ma’lumotlarning saqlanishi", [
        "DatabaseTest.dataSurvivesClosingAndReopening"]),
    ("Bir vaqtda bir necha operatsiyada qoldiqning to‘g‘ri saqlanishi", [
        "StockServiceTest.concurrentOperationsKeepStockExactAndNeverNegative"]),
    ("Noto‘g‘ri sana va miqdorlarda xatolarni to‘g‘ri boshqarish", [
        "ReagentsServiceTest.invalidDatesAreRefused",
        "ReagentsServiceTest.invalidQuantitiesAreRefused",
        "UnitsTest.invalidQuantitiesGiveUzbekMessages",
        "StockServiceTest.invalidOperationInputsGiveUzbekMessages"]),
    ("Butun dastur o‘zini tekshirishi (--self-test: kirim, sarf, 12 hisobot Excel+PDF, zaxira/tiklash)", [
        "SelfTestTest.selfTestPassesEveryStepAndWritesAUtf8Report"]),
]

STATUS = {"passed": "✅ o‘tdi", "failed": "❌ yiqildi", "skipped": "⏭ o‘tkazib yuborildi", "missing": "⚠ ishga tushmagan"}


def load(dirs):
    results = {}
    for d in dirs:
        for path in glob.glob(os.path.join(d, "TEST-*.xml")):
            suite = ET.parse(path).getroot()
            cls = suite.get("name").split(".")[-1]
            for case in suite.findall("testcase"):
                key = f"{cls}.{case.get('name')}"
                if case.find("failure") is not None or case.find("error") is not None:
                    status = "failed"
                elif case.find("skipped") is not None:
                    status = "skipped"
                else:
                    status = "passed"
                # Bir nechta katalog (masalan, Linux va Windows) bo‘lsa — eng yomoni olinadi.
                previous = results.get(key)
                if previous is None or previous == "passed":
                    results[key] = status
    return results


def main():
    dirs = sys.argv[1:] or [os.path.join(os.path.dirname(__file__), "..", "build", "test-results", "test")]
    results = load(dirs)
    if not results:
        sys.exit("Test natijalari topilmadi: avval ./gradlew test ni bajaring.")
    counts = {k: sum(1 for v in results.values() if v == k) for k in ("passed", "failed", "skipped")}
    out = []
    out.append("# Testlar natijasi — Reaktiv Control\n")
    out.append(f"Yaratilgan: {datetime.now().strftime('%d.%m.%Y %H:%M')} · manba: `./gradlew test` (JUnit XML).\n")
    out.append(f"Jami **{len(results)}** test: {counts['passed']} o‘tdi, {counts['failed']} yiqildi, {counts['skipped']} o‘tkazib yuborildi.\n")
    out.append("## Topshiriqning 18-bandi: majburiy testlar\n")
    out.append("| № | Holat | Testlar | Natija |")
    out.append("|---|---|---|---|")
    for i, (title, tests) in enumerate(SPEC_CASES, 1):
        statuses = [results.get(t, "missing") for t in tests]
        overall = "passed" if all(s == "passed" for s in statuses) else next(s for s in ("failed", "missing", "skipped") if s in statuses)
        names = "<br>".join(f"`{t}` — {STATUS[s]}" for t, s in zip(tests, statuses))
        out.append(f"| {i} | {title} | {names} | {STATUS[overall]} |")
    out.append("\n## Barcha testlar\n")
    by_class = {}
    for key, status in sorted(results.items()):
        cls, name = key.split(".", 1)
        by_class.setdefault(cls, []).append((name, status))
    for cls, cases in by_class.items():
        ok = sum(1 for _, s in cases if s == "passed")
        out.append(f"<details><summary><b>{cls}</b> — {ok}/{len(cases)}</summary>\n")
        for name, status in cases:
            out.append(f"- {STATUS[status]} `{name}`")
        out.append("\n</details>\n")
    print("\n".join(out))


if __name__ == "__main__":
    main()
