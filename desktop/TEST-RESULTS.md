# Testlar natijasi — Reaktiv Control

Yaratilgan: 30.10.2026 08:24 · manba: `./gradlew test` (JUnit XML) · tizim: Linux x86_64.

Jami **357** test: 357 o‘tdi, 0 yiqildi, 0 o‘tkazib yuborildi.

## Topshiriqning 18-bandi: majburiy testlar

| № | Holat | Testlar | Natija |
|---|---|---|---|
| 1 | Yangi reaktiv qo‘shish | `ReagentsServiceTest.newReagentCreatesReagentBatchAndInitialReceipt` — ✅ o‘tdi<br>`PagesUiTest.addReagentValidatesThenSaves` — ✅ o‘tdi | ✅ o‘tdi |
| 2 | Bir xil nomdagi turli seriyali partiyalarni yaratish | `ReagentsServiceTest.sameNameDifferentSeriesCreatesSeparateBatches` — ✅ o‘tdi | ✅ o‘tdi |
| 3 | Turli yaroqlilik muddatlarini alohida ko‘rsatish | `ReagentsServiceTest.differentExpiriesAreSeparateBatchesWithTheirOwnState` — ✅ o‘tdi<br>`PlacesPagesUiTest.batchesDialogShowsEveryBatchWithItsOwnState` — ✅ o‘tdi | ✅ o‘tdi |
| 4 | Bir xil nomdagi reaktivlarning umumiy qoldig‘ini hisoblash (Etil spirti: 19 l / 14 l) | `ReagentsServiceTest.groupTotalOfTheEtilSpirtiExample` — ✅ o‘tdi<br>`ReportsTest.etilSpirtiNumbers` — ✅ o‘tdi | ✅ o‘tdi |
| 5 | Bir xil nomdagi partiyalarni ketma-ket joylashtirish | `ReagentsServiceTest.rowsOfTheSameNameAreAdjacentAndOrderedByExpiry` — ✅ o‘tdi<br>`PagesUiTest.sameNamesAreAdjacentInFefoOrderWithGroupTotal` — ✅ o‘tdi | ✅ o‘tdi |
| 6 | Kirim va sarfni to‘g‘ri hisoblash | `StockServiceTest.receiptAndIssueMath` — ✅ o‘tdi<br>`ReceiptsPageUiTest.extraReceiptForAnExistingBatchAddsToItsRemainder` — ✅ o‘tdi | ✅ o‘tdi |
| 7 | Bir reaktivni bir nechta bo‘limga bir vaqtda berish (200 / 300 / 150 / 100 ml) | `StockServiceTest.oneReagentToFourDepartmentsAtOnceInOneTransaction` — ✅ o‘tdi<br>`IssuesPageUiTest.fourLinesFromALitreBatchAreSavedAsOneOperation` — ✅ o‘tdi | ✅ o‘tdi |
| 8 | Tumanga reaktiv berish | `StockServiceTest.issueToADistrictCreatesTheDistrictByName` — ✅ o‘tdi<br>`IssuesPageUiTest.aNewDistrictTypedInALineIsCreated` — ✅ o‘tdi | ✅ o‘tdi |
| 9 | Turli sanalarda bir tumanga bir necha marta berish | `StockServiceTest.severalIssuesToOneDistrictOnDifferentDates` — ✅ o‘tdi<br>`PlacesPagesUiTest.districtShowsEveryIssueSeparatelyAndPerReagentTotals` — ✅ o‘tdi | ✅ o‘tdi |
| 10 | Omborda yetarli miqdor bo‘lmaganda operatsiyani bloklash | `StockServiceTest.insufficientStockBlocksTheWholeMultiLineOperation` — ✅ o‘tdi<br>`IssuesPageUiTest.insufficientStockOnOneLineSavesNothing` — ✅ o‘tdi | ✅ o‘tdi |
| 11 | Qaytarish operatsiyasi | `StockServiceTest.returnPutsStockBackIntoTheBatch` — ✅ o‘tdi<br>`StockServiceTest.returningMoreThanWasIssuedIsRefused` — ✅ o‘tdi<br>`IssuesPageUiTest.returnDialogIncreasesTheRemainder` — ✅ o‘tdi | ✅ o‘tdi |
| 12 | Yashil, sariq va qizil ranglar chegaralari (91 / 90 / 31 / 30 / 0 / −1 kun) | `ExpiryTest.colourThresholdsAreExactDays` — ✅ o‘tdi | ✅ o‘tdi |
| 13 | Muddati o‘tgan reaktivlarni aniqlash | `ExpiryTest.expiredDetectionAndDaysLeft` — ✅ o‘tdi<br>`QueriesServiceTest.notificationsListExpiredRedBelowMinAndYellow` — ✅ o‘tdi | ✅ o‘tdi |
| 14 | Yangi bo‘lim va yangi tuman qo‘shish | `DirectoryServiceTest.addDepartmentDedupesByKey` — ✅ o‘tdi<br>`DirectoryServiceTest.addDistrictDedupesByKeyAndReactivates` — ✅ o‘tdi<br>`PlacesPagesUiTest.departmentCanBeAddedRenamedDeactivatedAndActivated` — ✅ o‘tdi<br>`PlacesPagesUiTest.districtCanBeAddedRenamedAndDeactivated` — ✅ o‘tdi | ✅ o‘tdi |
| 15 | Excel eksporti | `ExcelExportTest.writesTitleHeaderDatesNumbersAndUzbekText` — ✅ o‘tdi<br>`ReportsPageUiTest.excelSaveWithoutPreviewBuildsAndWritesAReadableFile` — ✅ o‘tdi | ✅ o‘tdi |
| 16 | PDF eksporti | `PdfExportTest.titleOrganisationFiltersAndUzbekLettersAreInTheText` — ✅ o‘tdi<br>`ReportsPageUiTest.pdfSaveWritesAReadablePdfWithTheOrganisation` — ✅ o‘tdi | ✅ o‘tdi |
| 17 | Zaxira nusxa yaratish va tiklash | `BackupServiceTest.createValidateAndRestoreWithAutomaticPreRestoreBackup` — ✅ o‘tdi<br>`BackupSettingsUiTest.restoreFromFileReplacesTheDataAndKeepsThePreviousState` — ✅ o‘tdi | ✅ o‘tdi |
| 18 | Dastur yopilib qayta ochilganda ma’lumotlarning saqlanishi | `DatabaseTest.dataSurvivesClosingAndReopening` — ✅ o‘tdi | ✅ o‘tdi |
| 19 | Bir vaqtda bir necha operatsiyada qoldiqning to‘g‘ri saqlanishi | `StockServiceTest.concurrentOperationsKeepStockExactAndNeverNegative` — ✅ o‘tdi | ✅ o‘tdi |
| 20 | Noto‘g‘ri sana va miqdorlarda xatolarni to‘g‘ri boshqarish | `ReagentsServiceTest.invalidDatesAreRefused` — ✅ o‘tdi<br>`ReagentsServiceTest.invalidQuantitiesAreRefused` — ✅ o‘tdi<br>`UnitsTest.invalidQuantitiesGiveUzbekMessages` — ✅ o‘tdi<br>`StockServiceTest.invalidOperationInputsGiveUzbekMessages` — ✅ o‘tdi | ✅ o‘tdi |
| 21 | Butun dastur o‘zini tekshirishi (--self-test: kirim, sarf, 12 hisobot Excel+PDF, zaxira/tiklash) | `SelfTestTest.selfTestPassesEveryStepAndWritesAUtf8Report` — ✅ o‘tdi | ✅ o‘tdi |

## Barcha testlar

<details><summary><b>AddReagentImportUiTest</b> — 5/5</summary>

- ✅ o‘tdi `cleanFileImportsWithoutConfirmationAndShowsTheResult`
- ✅ o‘tdi `headerShortcutScrollsToTheImportSection`
- ✅ o‘tdi `templateThenFilledFileThenPreviewThenImport`
- ✅ o‘tdi `unchangedTemplateShowsOnlyTheExampleRowAndImportsNothing`
- ✅ o‘tdi `unreadableFileShowsAnErrorAndNoPreview`

</details>

<details><summary><b>AppStateTest</b> — 9/9</summary>

- ✅ o‘tdi `confirmableErrorsReachTheCallerFirst`
- ✅ o‘tdi `demoDataMatchesItsDescription`
- ✅ o‘tdi `fieldErrorsShownInTheFormAreNotRepeatedBelow`
- ✅ o‘tdi `messagesAreCappedAndKeyed`
- ✅ o‘tdi `navigateCarriesIntentAndBumpsSerial`
- ✅ o‘tdi `successfulRunRefreshesAndCallsOnSuccess`
- ✅ o‘tdi `tableLayoutRoundTripsThroughSettings`
- ✅ o‘tdi `unexpectedErrorIsLoggedAndShownInDialog`
- ✅ o‘tdi `userErrorGoesToFormAndMessage`

</details>

<details><summary><b>BackupLegacyNotesUiTest</b> — 2/2</summary>

- ✅ o‘tdi `noStoredNotesMeansNoNotesButton`
- ✅ o‘tdi `startupMessageOpensTheBackupPageWhereAllNotesOfTheLastImportAreShown`

</details>

<details><summary><b>BackupScreenshotTest</b> — 7/7</summary>

- ✅ o‘tdi `backupCreateAndRestore`
- ✅ o‘tdi `backupEmpty`
- ✅ o‘tdi `backupLegacyImport`
- ✅ o‘tdi `backupPage`
- ✅ o‘tdi `excelImport`
- ✅ o‘tdi `settingsErrorsAndReset`
- ✅ o‘tdi `settingsPage`

</details>

<details><summary><b>BackupServiceTest</b> — 10/10</summary>

- ✅ o‘tdi `aBackupFromANewerVersionIsRefused`
- ✅ o‘tdi `aRestoreThatFailsAfterTheSwapRollsBackAndCleansUp`
- ✅ o‘tdi `autoBackupOncePerDayKeepsOnlyTheNewest`
- ✅ o‘tdi `badFilesAreRefusedAndTheDatabaseStaysIntact`
- ✅ o‘tdi `createValidateAndRestoreWithAutomaticPreRestoreBackup`
- ✅ o‘tdi `recoveryWithoutAValidBackupChangesNothing`
- ✅ o‘tdi `restoreStillWorksWhenTheLiveDatabaseIsCorrupted`
- ✅ o‘tdi `restoringFromAReadOnlyBackupLeavesAWritableDatabase`
- ✅ o‘tdi `startupRecoveryUsesTheNewestValidBackupAndKeepsTheBrokenFile`
- ✅ o‘tdi `whenTheRollbackItselfFailsTheOldDataIsKeptAndItsLocationIsReported`

</details>

<details><summary><b>BackupSettingsUiTest</b> — 13/13</summary>

- ✅ o‘tdi `backupListShowsKindsAndRestoresFromARow`
- ✅ o‘tdi `cancelledSaveDialogDoesNothing`
- ✅ o‘tdi `createBackupWritesAValidatedFile`
- ✅ o‘tdi `legacyFileFoundOnThisComputerIsOfferedUntilImported`
- ✅ o‘tdi `legacyImportShowsCountsAndRefusesTheSameFileTwice`
- ✅ o‘tdi `locationsShowDataFolderDatabaseAndLogs`
- ✅ o‘tdi `resetTableLayoutsClearsEverySavedLayout`
- ✅ o‘tdi `restoreCanBeCancelledAndInvalidFilesChangeNothing`
- ✅ o‘tdi `restoreFromFileReplacesTheDataAndKeepsThePreviousState`
- ✅ o‘tdi `settingsSaveOrganisationAndStaffNameUsedByForms`
- ✅ o‘tdi `settingsShowFieldErrorsAndSaveNothingWhenInvalid`
- ✅ o‘tdi `settingsShowThresholdsAndAboutInformation`
- ✅ o‘tdi `writeButtonsAreDisabledWhileBusy`

</details>

<details><summary><b>DataDirAndLoggingTest</b> — 2/2</summary>

- ✅ o‘tdi `dataDirCanBeOverriddenAndHasFixedLayout`
- ✅ o‘tdi `loggingWritesRotatingFilesIntoTheLogsFolder`

</details>

<details><summary><b>DataTableUiTest</b> — 11/11</summary>

- ✅ o‘tdi `actionButtonsAndOverflowMenu`
- ✅ o‘tdi `clickSelectsDoubleClickOpensAndKeysMove`
- ✅ o‘tdi `columnMenuHidesMovesAndResets`
- ✅ o‘tdi `draggingTheBorderResizesAndDoubleClickRestores`
- ✅ o‘tdi `emptyTableShowsTheMessage`
- ✅ o‘tdi `emptyValuesStayLastInBothDirections`
- ✅ o‘tdi `groupsExpandCollapseAndKeepTheirNumbers`
- ✅ o‘tdi `headerClickSortsAscendingDescendingAndBack`
- ✅ o‘tdi `rightClickOnAHeaderOpensItsMenuWithoutSorting`
- ✅ o‘tdi `savedLayoutIsAppliedOnOpen`
- ✅ o‘tdi `sortingKeepsGroupsTogether`

</details>

<details><summary><b>DatabaseTest</b> — 8/8</summary>

- ✅ o‘tdi `aDatabaseFromANewerVersionIsRefused`
- ✅ o‘tdi `aFailedFileOperationLeavesTheDatabaseOpenAndUsable`
- ✅ o‘tdi `aVersion1DatabaseIsUpgradedInPlace`
- ✅ o‘tdi `dataSurvivesClosingAndReopening`
- ✅ o‘tdi `databaseItselfRefusesNegativeStockAndDoubleCounting`
- ✅ o‘tdi `failedWriteRollsBackEverythingAndNestedWriteOnlyItsOwnPart`
- ✅ o‘tdi `ledgerAndAuditAreAppendOnly`
- ✅ o‘tdi `schemaVersionSeedsAndPragmas`

</details>

<details><summary><b>DirectoryServiceTest</b> — 6/6</summary>

- ✅ o‘tdi `addDepartmentDedupesByKey`
- ✅ o‘tdi `addDistrictDedupesByKeyAndReactivates`
- ✅ o‘tdi `deactivatedDepartmentIsHiddenAndCannotReceiveNewIssues`
- ✅ o‘tdi `invalidPlaceNamesAreRefused`
- ✅ o‘tdi `renameToAnotherExistingNameIsRefused`
- ✅ o‘tdi `seededDepartmentsInSpecOrder`

</details>

<details><summary><b>ExcelExportTest</b> — 5/5</summary>

- ✅ o‘tdi `emptyReportAndRewritingAnExistingFile`
- ✅ o‘tdi `headerIsFrozenFilteredStyledAndTheZipIsStandard`
- ✅ o‘tdi `sheetNamesAreSanitised`
- ✅ o‘tdi `syntheticDataWithEveryColumnType`
- ✅ o‘tdi `writesTitleHeaderDatesNumbersAndUzbekText`

</details>

<details><summary><b>ExcelImportTest</b> — 6/6</summary>

- ✅ o‘tdi `duplicatesAreReportedPerRow`
- ✅ o‘tdi `excelNumbersAreReadExactly`
- ✅ o‘tdi `headersAreFoundBelowATitleAndMissingColumnsAreNamed`
- ✅ o‘tdi `importIsAllOrNothing`
- ✅ o‘tdi `previewReportsErrorsPerRowAndWritesNothing`
- ✅ o‘tdi `templateHasTheExactHeadersAnExampleRowAndAGuideSheet`

</details>

<details><summary><b>ExpiryTest</b> — 3/3</summary>

- ✅ o‘tdi `colourThresholdsAreExactDays`
- ✅ o‘tdi `expiredDetectionAndDaysLeft`
- ✅ o‘tdi `statusTextsAreUzbek`

</details>

<details><summary><b>FieldsUiTest</b> — 12/12</summary>

- ✅ o‘tdi `calendarDoesNotPickOutOfRangeDays`
- ✅ o‘tdi `calendarPicksTodayAMonthAndClears`
- ✅ o‘tdi `comboFiltersAndPicksWithTheKeyboard`
- ✅ o‘tdi `comboOffersTheNewItem`
- ✅ o‘tdi `comboWithoutNewItemsAndTheArrowShowsAll`
- ✅ o‘tdi `dateFieldParsesTypedInput`
- ✅ o‘tdi `dateRangeWarnsWhenReversed`
- ✅ o‘tdi `dateTextChecks`
- ✅ o‘tdi `filterBarBannerAndConfirmDialog`
- ✅ o‘tdi `reasonDialogRequiresAReason`
- ✅ o‘tdi `selectAndQtyFields`
- ✅ o‘tdi `suggestionFilterAndNewValue`

</details>

<details><summary><b>HistoryPageUiTest</b> — 9/9</summary>

- ✅ o‘tdi `auditEntryDialogOpensTheBatchLedger`
- ✅ o‘tdi `auditLogFiltersByActionTypeAndText`
- ✅ o‘tdi `batchExportSaysWhenTheSeriesFilterAlsoMatchesAnotherBatch`
- ✅ o‘tdi `batchHistoryDialogOpensThisPage`
- ✅ o‘tdi `ledgerExportWithoutReagentShowsTheFieldError`
- ✅ o‘tdi `ledgerExportsTheHistoryReportToExcelAndPdf`
- ✅ o‘tdi `ledgerRunningBalanceMatchesTheRemainder`
- ✅ o‘tdi `moreButtonLoadsOlderEntries`
- ✅ o‘tdi `pickingAReagentAndABatchFiltersTheLedger`

</details>

<details><summary><b>IssuesPageUiTest</b> — 14/14</summary>

- ✅ o‘tdi `aMissingStockIntentIsExplained`
- ✅ o‘tdi `aNewDistrictTypedInALineIsCreated`
- ✅ o‘tdi `anExpiredBatchNeedsAnExplicitConfirmation`
- ✅ o‘tdi `cancelAnIssueItemAndThenAReturn`
- ✅ o‘tdi `cancellingAnItemWithReturnsIsRefusedWithAMessage`
- ✅ o‘tdi `correctAnIssueItemReplacesIt`
- ✅ o‘tdi `duplicateIssueNeedsConfirmation`
- ✅ o‘tdi `fefoRecommendationIsPreselectedAndAnOverrideIsWarnedAndFlagged`
- ✅ o‘tdi `fefoSplitReplacesTheLinesAndSaves`
- ✅ o‘tdi `fourLinesFromALitreBatchAreSavedAsOneOperation`
- ✅ o‘tdi `insufficientStockOnOneLineSavesNothing`
- ✅ o‘tdi `linesOnTheSameBatchAreSummedInTheLiveCheck`
- ✅ o‘tdi `returnDialogIncreasesTheRemainder`
- ✅ o‘tdi `writeOffNeedsAReason`

</details>

<details><summary><b>LegacyImportTest</b> — 11/11</summary>

- ✅ o‘tdi `aNewerOldAppFileNextToTheSpreadsheetIsMentioned`
- ✅ o‘tdi `aVersion1FileAndTheVersion2FileMadeFromItShareRows`
- ✅ o‘tdi `anOutOfRangeOldReceivedDateIsClampedLikeTheCreationDate`
- ✅ o‘tdi `autoImportIsSkippedWhenTheDatabaseAlreadyHasBatches`
- ✅ o‘tdi `autoImportRunsOnlyOnAnEmptyDatabaseAndOnlyOnce`
- ✅ o‘tdi `importsAVersion1PhoneBackup`
- ✅ o‘tdi `importsAVersion2SpreadsheetFile`
- ✅ o‘tdi `negativeOrHugeOldAmountsAreNamedCorrectly`
- ✅ o‘tdi `rowsWithoutAUsableUnitAreInferredOrRecordedAndUnmappedValuesAreKept`
- ✅ o‘tdi `theSameFileIsNeverImportedTwice`
- ✅ o‘tdi `theSameRowsFromAnotherFileAreNeverImportedTwice`

</details>

<details><summary><b>MainStartupTest</b> — 8/8</summary>

- ✅ o‘tdi `asciiTempOtherSystemsAndAnExplicitSettingAreLeftAlone`
- ✅ o‘tdi `legacyMessageWithManyNotesSaysHowManyMoreAndOpensTheBackupPage`
- ✅ o‘tdi `nativeLibraryFailureIsRecognisedAnywhereInTheCauseChain`
- ✅ o‘tdi `nonAsciiTempOnWindowsMovesTheSqliteLibraryToAnAsciiFolder`
- ✅ o‘tdi `selfTestWithoutTheSqliteLibraryReportsTheRootCause`
- ✅ o‘tdi `startupWithoutTheSqliteLibraryNamesTheLibraryAndTouchesNoFiles`
- ✅ o‘tdi `unwritableTempIsRedirectedAndUnusableFoldersFallBackOrGiveUp`
- ✅ o‘tdi `userFolderNamesAreAsciiAndDistinct`

</details>

<details><summary><b>PagesScreenshotTest</b> — 6/6</summary>

- ✅ o‘tdi `addReagent`
- ✅ o‘tdi `addReagentDuplicateAndSuccess`
- ✅ o‘tdi `dashboard`
- ✅ o‘tdi `reagents`
- ✅ o‘tdi `reagentsEditAndHistoryDialogs`
- ✅ o‘tdi `reagentsFiltered`

</details>

<details><summary><b>PagesUiTest</b> — 17/17</summary>

- ✅ o‘tdi `addReagentValidatesThenSaves`
- ✅ o‘tdi `archiveHidesTheBatchAndArchivedFilterShowsIt`
- ✅ o‘tdi `dashboardNumbersComeFromTheQuery`
- ✅ o‘tdi `deleteFollowsTheHistoryRule`
- ✅ o‘tdi `editDialogValidatesAndSaves`
- ✅ o‘tdi `everyNavigationItemOpensItsPage`
- ✅ o‘tdi `exactDuplicateOffersAnExtraReceipt`
- ✅ o‘tdi `historyDialogShowsRunningBalance`
- ✅ o‘tdi `newDepartmentCanBeAddedFromTheForm`
- ✅ o‘tdi `notificationOpensTheExpiryPageWithItsBatch`
- ✅ o‘tdi `reagentIntentFiltersToOneName`
- ✅ o‘tdi `rowActionsNavigateWithIntents`
- ✅ o‘tdi `sameNamesAreAdjacentInFefoOrderWithGroupTotal`
- ✅ o‘tdi `sameSeriesOtherExpiryNeedsConfirmation`
- ✅ o‘tdi `secondWriteInTheSameFrameIsIgnored`
- ✅ o‘tdi `statusFilterShowsOnlyMatchingBatches`
- ✅ o‘tdi `textAndDepartmentFiltersAndClearing`

</details>

<details><summary><b>PdfExportTest</b> — 4/4</summary>

- ✅ o‘tdi `emptyReportSaysThereIsNoData`
- ✅ o‘tdi `everyUzbekLetterFormRendersAndIsExtractable`
- ✅ o‘tdi `multiPageTableRepeatsTheHeaderAndNumbersPages`
- ✅ o‘tdi `titleOrganisationFiltersAndUzbekLettersAreInTheText`

</details>

<details><summary><b>PlacesPagesUiTest</b> — 16/16</summary>

- ✅ o‘tdi `archiveFromTheExpiryPageNeedsConfirmation`
- ✅ o‘tdi `batchesDialogShowsEveryBatchWithItsOwnState`
- ✅ o‘tdi `departmentCanBeAddedRenamedDeactivatedAndActivated`
- ✅ o‘tdi `departmentReagentFilterNarrowsEverything`
- ✅ o‘tdi `departmentReportIsSavedAsExcelAndPdf`
- ✅ o‘tdi `departmentSummaryMatchesTheDemoData`
- ✅ o‘tdi `districtCanBeAddedRenamedAndDeactivated`
- ✅ o‘tdi `districtReportUsesTheCurrentFilters`
- ✅ o‘tdi `districtReturnValidatesAndSaves`
- ✅ o‘tdi `districtShowsEveryIssueSeparatelyAndPerReagentTotals`
- ✅ o‘tdi `expiryChipsShowCountsAndDefaultFilter`
- ✅ o‘tdi `minimumStockDialogSavesOnlyTheMinimum`
- ✅ o‘tdi `notificationIntentPreselectsTheStateAndHighlightsTheBatch`
- ✅ o‘tdi `stockIntentFiltersToOneReagent`
- ✅ o‘tdi `stockTotalsAndBelowMinFilter`
- ✅ o‘tdi `writeOffNeedsAReasonAndReducesTheRemainder`

</details>

<details><summary><b>PlacesScreenshotTest</b> — 10/10</summary>

- ✅ o‘tdi `departments`
- ✅ o‘tdi `departmentsSudKimyoAndDialogs`
- ✅ o‘tdi `districtChilonzorAndReturn`
- ✅ o‘tdi `districts`
- ✅ o‘tdi `expiry`
- ✅ o‘tdi `expiryAllAndIntent`
- ✅ o‘tdi `expiryFromNotification`
- ✅ o‘tdi `inactiveDepartmentInTheList`
- ✅ o‘tdi `stock`
- ✅ o‘tdi `stockMinAndFilter`

</details>

<details><summary><b>QueriesServiceTest</b> — 7/7</summary>

- ✅ o‘tdi `batchFiltersWorkOnEveryParameter`
- ✅ o‘tdi `dashboardCounts`
- ✅ o‘tdi `departmentSummaryShowsOwnedBatchesAndIssuedTotals`
- ✅ o‘tdi `ledgerBalanceStaysNonNegativeWhenAnIssueIsMovedToAnEarlierDate`
- ✅ o‘tdi `ledgerShowsARunningBalancePerBatch`
- ✅ o‘tdi `notificationsListExpiredRedBelowMinAndYellow`
- ✅ o‘tdi `stockTotalsAndAuditLog`

</details>

<details><summary><b>ReagentsExportUiTest</b> — 3/3</summary>

- ✅ o‘tdi `excelExportUsesThePageFiltersAndSaysWhichApplied`
- ✅ o‘tdi `pdfExportWithoutFiltersListsEveryBatch`
- ✅ o‘tdi `selectedReagentBecomesTheNameFilter`

</details>

<details><summary><b>ReagentsServiceTest</b> — 18/18</summary>

- ✅ o‘tdi `archivedBatchLeavesTheGroupTotalAndCannotBeUsed`
- ✅ o‘tdi `changingTheBatchReceivedDateMovesTheInitialReceipt`
- ✅ o‘tdi `deleteIsAllowedOnlyWithoutHistory`
- ✅ o‘tdi `differentExpiriesAreSeparateBatchesWithTheirOwnState`
- ✅ o‘tdi `exactDuplicateBatchIsRefusedAndPointsToTheExistingBatch`
- ✅ o‘tdi `groupTotalOfTheEtilSpirtiExample`
- ✅ o‘tdi `invalidDatesAreRefused`
- ✅ o‘tdi `invalidQuantitiesAreRefused`
- ✅ o‘tdi `minimumStockInAnotherDimensionIsRefusedWhenItCanNeverApply`
- ✅ o‘tdi `missingRequiredFieldsGiveUzbekMessages`
- ✅ o‘tdi `movingTheReceivedDateLaterIsRefusedWhenItWouldMakeEarlierStockNegative`
- ✅ o‘tdi `nameSuggestionsComeFromExistingReagents`
- ✅ o‘tdi `newReagentCreatesReagentBatchAndInitialReceipt`
- ✅ o‘tdi `renamingAReagentToAnotherReagentsNameIsRefused`
- ✅ o‘tdi `rowsOfTheSameNameAreAdjacentAndOrderedByExpiry`
- ✅ o‘tdi `sameNameDifferentSeriesCreatesSeparateBatches`
- ✅ o‘tdi `sameSeriesWithAnotherExpiryNeedsConfirmation`
- ✅ o‘tdi `updateBatchChangesDescriptiveFieldsOnly`

</details>

<details><summary><b>ReceiptsIssuesScreenshotTest</b> — 7/7</summary>

- ✅ o‘tdi `issuesDialogs`
- ✅ o‘tdi `issuesEmpty`
- ✅ o‘tdi `issuesExpiredConfirmation`
- ✅ o‘tdi `issuesMultiLineWithWarnings`
- ✅ o‘tdi `receipts`
- ✅ o‘tdi `receiptsErrorsAndDialogs`
- ✅ o‘tdi `receiptsStatusColumns`

</details>

<details><summary><b>ReceiptsPageUiTest</b> — 9/9</summary>

- ✅ o‘tdi `cancelAReceiptWithAReason`
- ✅ o‘tdi `cancellingAReceiptWhoseStockWasIssuedIsRefusedWithAnExplanation`
- ✅ o‘tdi `correctAReceiptReplacesItWithANewOne`
- ✅ o‘tdi `duplicateReceiptNeedsConfirmation`
- ✅ o‘tdi `extraReceiptForAnExistingBatchAddsToItsRemainder`
- ✅ o‘tdi `filtersAndRowActionsNavigate`
- ✅ o‘tdi `missingBatchAndQuantityAreShownNextToTheFields`
- ✅ o‘tdi `receiptForIntentPreselectsTheBatchAndAcceptsMillilitres`
- ✅ o‘tdi `typingAnotherTextDropsTheChosenBatch`

</details>

<details><summary><b>ReportsPageUiTest</b> — 9/9</summary>

- ✅ o‘tdi `batchesPreviewHasSummaryRowsAndStatusChips`
- ✅ o‘tdi `cancellingTheSaveDialogWritesNothing`
- ✅ o‘tdi `departmentAndDistrictFiltersAndStalePreview`
- ✅ o‘tdi `everyKindPreviewsWithoutErrors`
- ✅ o‘tdi `excelSaveWithoutPreviewBuildsAndWritesAReadableFile`
- ✅ o‘tdi `historyWithoutNameShowsTheErrorNextToTheField`
- ✅ o‘tdi `pdfSaveWritesAReadablePdfWithTheOrganisation`
- ✅ o‘tdi `periodStartAfterEndIsRefusedAtTheField`
- ✅ o‘tdi `unwritableTargetShowsAnErrorMessageNotACrash`

</details>

<details><summary><b>ReportsScreenshotTest</b> — 8/8</summary>

- ✅ o‘tdi `auditDialog`
- ✅ o‘tdi `auditLog`
- ✅ o‘tdi `ledger`
- ✅ o‘tdi `ledgerEmpty`
- ✅ o‘tdi `ledgerWithCancelAndCorrection`
- ✅ o‘tdi `reagentsExport`
- ✅ o‘tdi `reports`
- ✅ o‘tdi `reportsDepartmentAndExpired`

</details>

<details><summary><b>ReportsTest</b> — 11/11</summary>

- ✅ o‘tdi `byDepartmentShowsOwnedRemainderAndIssuedTotals`
- ✅ o‘tdi `decimalFormattingAndSuggestedFileName`
- ✅ o‘tdi `etilSpirtiNumbers`
- ✅ o‘tdi `everyKindBuildsWithConsistentRows`
- ✅ o‘tdi `expiringExpiredAndBelowMinLists`
- ✅ o‘tdi `filtersAreAppliedAndDescribed`
- ✅ o‘tdi `historyListsEveryOperationWithARunningBalance`
- ✅ o‘tdi `historyWithoutANameIsRefused`
- ✅ o‘tdi `movementShowsOpeningFlowsAndClosingPerBatch`
- ✅ o‘tdi `quantitiesStayInTheRowUnitAndTotalsNeverMixDimensions`
- ✅ o‘tdi `reportTitlesAreTheUzbekTitlesOfTheSpecification`

</details>

<details><summary><b>SelfTestTest</b> — 2/2</summary>

- ✅ o‘tdi `selfTestPassesEveryStepAndWritesAUtf8Report`
- ✅ o‘tdi `unwritableOutputGivesExitCode3`

</details>

<details><summary><b>SheetStoreTest</b> — 16/16</summary>

- ✅ o‘tdi `aBrokenDepartmentListIsCleanedUp`
- ✅ o‘tdi `aBrokenFileFallsBackToTheDepartmentsOfThePreviousSave`
- ✅ o‘tdi `aFileWithoutADepartmentListGetsTheDefaultsAndItsOwnNames`
- ✅ o‘tdi `aPhoneBackupIsMovedIntoTheNewTable`
- ✅ o‘tdi `anOlderStateNeverOverwritesANewerOne`
- ✅ o‘tdi `backupCopyProblemDoesNotBlockSaving`
- ✅ o‘tdi `brokenFileFallsBackToPreviousSave`
- ✅ o‘tdi `csvOpensInExcel`
- ✅ o‘tdi `csvShowsTheExpiryColourOfAnUnfinishedRow`
- ✅ o‘tdi `dataFileIsVersion2WithMachineReadableValues`
- ✅ o‘tdi `equalTimestampsKeepTheirOrderAfterReload`
- ✅ o‘tdi `runningTheOldVersionAgainCannotEmptyTheNewTable`
- ✅ o‘tdi `saveAndLoadKeepsCompleteRowsAndDrafts`
- ✅ o‘tdi `simultaneousSavesNeverBreakTheFile`
- ✅ o‘tdi `theDepartmentListIsSavedWithTheTable`
- ✅ o‘tdi `theOldDataFileIsMigratedButNeverWritten`

</details>

<details><summary><b>SheetTest</b> — 21/21</summary>

- ✅ o‘tdi `aManufactureDateInTheFutureIsFlaggedOnItsOwnCell`
- ✅ o‘tdi `aNewRowAsksForItsUnit`
- ✅ o‘tdi `ambiguousSlashDates`
- ✅ o‘tdi `amountsDisplayLikeThePhone`
- ✅ o‘tdi `anAliasNeverBringsBackARenamedOrRemovedDefault`
- ✅ o‘tdi `anOldRowWithOnlyItsArrivalDateIsNotBlank`
- ✅ o‘tdi `colourComesFromTheExpiryDateEvenForAnUnfinishedRow`
- ✅ o‘tdi `completeRowGetsColourAndComputedColumns`
- ✅ o‘tdi `daysLeftText`
- ✅ o‘tdi `departmentPrefixPicksTheFirstInListOrderButAnExactNameWins`
- ✅ o‘tdi `draftRowListsMissingAndWrongFields`
- ✅ o‘tdi `everyApostropheAndSpaceSpellingIsTheSameDepartment`
- ✅ o‘tdi `expiryBeforeProductionIsAnError`
- ✅ o‘tdi `issuingIsOptionalButDepartmentAndAmountGoTogether`
- ✅ o‘tdi `monthFirstFollowsTheComputersRegionalFormat`
- ✅ o‘tdi `parsesTypicalDateSpellings`
- ✅ o‘tdi `rowsFromAnotherTableGetOneSpellingPerDepartment`
- ✅ o‘tdi `slashDatesThatCanOnlyBeMonthFirstAreAccepted`
- ✅ o‘tdi `theDepartmentListKeepsUsedNamesWithoutDuplicates`
- ✅ o‘tdi `theOldArrivalDateIsShownWhereTheProductionDateIsMissing`
- ✅ o‘tdi `unitsAndDepartmentsAreNormalised`

</details>

<details><summary><b>ShellUiTest</b> — 7/7</summary>

- ✅ o‘tdi `customNotificationHandler`
- ✅ o‘tdi `notificationBadgeCountsAndAnItemNavigates`
- ✅ o‘tdi `railScrollsInsteadOfClippingOnALowWindow`
- ✅ o‘tdi `railShowsEveryPageAndSwitches`
- ✅ o‘tdi `statusStylesMapTheFiveExpiryStates`
- ✅ o‘tdi `thirteenPagesInTheSpecifiedOrder`
- ✅ o‘tdi `todayIsShownWithItsUzbekWeekday`

</details>

<details><summary><b>StockServiceTest</b> — 23/23</summary>

- ✅ o‘tdi `aReturnIntoAnArchivedBatchUnarchivesIt`
- ✅ o‘tdi `cancelAndCorrectKeepHistoryAndRecomputeTheRemainder`
- ✅ o‘tdi `cancellingAReceiptThatWouldMakeStockNegativeIsRefused`
- ✅ o‘tdi `cancellingAnIssueWithActiveReturnsIsRefused`
- ✅ o‘tdi `cancellingOrCorrectingAnIssueOfAnArchivedBatchUnarchivesBatchAndReagent`
- ✅ o‘tdi `concurrentOperationsKeepStockExactAndNeverNegative`
- ✅ o‘tdi `correctingTheInitialReceiptDateAlsoMovesTheBatchReceivedDate`
- ✅ o‘tdi `duplicateGuardWithinTwoMinutes`
- ✅ o‘tdi `expiredBatchNeedsExplicitConfirmation`
- ✅ o‘tdi `expiredConfirmationNamesEveryExpiredLineOfTheOperation`
- ✅ o‘tdi `fefoFlagUsesStockAsOfTheIssueDate`
- ✅ o‘tdi `fefoRecommendationAndOverrideFlag`
- ✅ o‘tdi `insufficientStockBlocksTheWholeMultiLineOperation`
- ✅ o‘tdi `invalidOperationInputsGiveUzbekMessages`
- ✅ o‘tdi `issueDateRules`
- ✅ o‘tdi `issueToADistrictCreatesTheDistrictByName`
- ✅ o‘tdi `oneReagentToFourDepartmentsAtOnceInOneTransaction`
- ✅ o‘tdi `receiptAndIssueMath`
- ✅ o‘tdi `returnPutsStockBackIntoTheBatch`
- ✅ o‘tdi `returningMoreThanWasIssuedIsRefused`
- ✅ o‘tdi `returnsHaveTheSameDuplicateGuardAsReceiptsAndIssues`
- ✅ o‘tdi `severalIssuesToOneDistrictOnDifferentDates`
- ✅ o‘tdi `unitsConvertInsideADimensionAndDimensionsAreNeverSummed`

</details>

<details><summary><b>TableLayoutTest</b> — 8/8</summary>

- ✅ o‘tdi `brokenJsonIsIgnored`
- ✅ o‘tdi `defaultsFollowTheColumns`
- ✅ o‘tdi `jsonRoundTripKeepsEverything`
- ✅ o‘tdi `naturalOrderComparesNumbersAsNumbers`
- ✅ o‘tdi `resetRestoresTheDefaultsAndSaves`
- ✅ o‘tdi `sortCyclesAscendingDescendingNone`
- ✅ o‘tdi `theLastVisibleColumnCannotBeHidden`
- ✅ o‘tdi `unknownColumnsAreDroppedAndNewOnesTakeTheirDefaultPlace`

</details>

<details><summary><b>UiScreenshotTest</b> — 8/8</summary>

- ✅ o‘tdi `comboSuggestionsAndDialog`
- ✅ o‘tdi `comboSuggestionsOpen`
- ✅ o‘tdi `dashboardAt1366x768`
- ✅ o‘tdi `errorDialogWithDetails`
- ✅ o‘tdi `formFieldsAndCalendar`
- ✅ o‘tdi `icons`
- ✅ o‘tdi `railScrollsOnALowWindow`
- ✅ o‘tdi `shellWithGroupedTableAt1366x768`

</details>

<details><summary><b>UnitsTest</b> — 6/6</summary>

- ✅ o‘tdi `abbreviationsWithATrailingDotAreRecognised`
- ✅ o‘tdi `conversionOnlyInsideOneDimension`
- ✅ o‘tdi `formatGroupsThousandsAndUsesDecimalComma`
- ✅ o‘tdi `invalidQuantitiesGiveUzbekMessages`
- ✅ o‘tdi `quantitiesAreStoredAsExactBaseUnits`
- ✅ o‘tdi `unitAliasesAreRecognised`

</details>

