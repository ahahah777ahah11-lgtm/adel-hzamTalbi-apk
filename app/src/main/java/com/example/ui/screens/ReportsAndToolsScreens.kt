package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ColumnMapping
import com.example.data.importexport.ExportDataset
import com.example.data.importexport.ExportFormat
import com.example.data.importexport.PreviewRowStatus
import com.example.data.local.TransferCompany
import com.example.data.local.TransferStatus
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.CollectionDonutChart
import com.example.ui.components.ConfirmSensitiveActionDialog
import com.example.ui.components.DebtVsPaymentBarChart
import com.example.ui.components.DebtVsPaymentColumnChart
import com.example.ui.components.FinancialCard
import com.example.ui.components.StatusBadge
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 13. التقارير
@Composable
fun ReportsScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val cur = settings.currencySymbol

    Scaffold(
        topBar = {
            ArabicTopBar(title = "التقارير والتحليلات المالية", onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FinancialCard {
                    Text("الملخص المحاسبي العام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CollectionDonutChart(summary.totalDebts, summary.totalPayments)
                        DebtVsPaymentBarChart(
                            totalDebts = summary.totalDebts,
                            totalPayments = summary.totalPayments,
                            netBalance = summary.netOutstandingBalance,
                            currency = cur,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الرسم البياني التفاعلي: الديون المستحقة مقابل المدفوعات المستلمة",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DebtVsPaymentColumnChart(
                        totalDebts = summary.totalDebts,
                        totalPayments = summary.totalPayments,
                        netBalance = summary.netOutstandingBalance,
                        currency = cur,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onNavigate(AppRoutes.EXPORT_DATA) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تصدير التقرير إلى Excel / CSV / JSON")
                    }
                }
            }

            item {
                FinancialCard {
                    Text("توزيع الحوالات حسب الشركة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    TransferCompany.entries.forEachIndexed { idx, company ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        val compList = transfers.filter { it.company == company }
                        val confirmedSum = compList.filter { it.status == TransferStatus.CONFIRMED }.sumOf { it.amount }
                        val pendingCount = compList.count { it.status == TransferStatus.PENDING }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(company.titleAr, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "الإجمالي: ${compList.size} | معلقة: $pendingCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                FormatUtils.formatAmount(confirmedSum, cur),
                                fontWeight = FontWeight.Bold,
                                color = financial.success,
                            )
                        }
                    }
                }
            }

            item {
                val topDebtors = customers.filter { !it.isArchived && it.balance > 0.001 }.take(10)
                FinancialCard {
                    Text("أعلى 10 عملاء مديونية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (topDebtors.isEmpty()) {
                        Text("لا توجد مديونيات مستحقة حالياً.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        topDebtors.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${i + 1}. ${c.name}", fontWeight = FontWeight.Medium)
                                Text(FormatUtils.formatAmount(c.balance, cur), fontWeight = FontWeight.Bold, color = financial.danger)
                            }
                        }
                    }
                }
            }

            item {
                FinancialCard {
                    Text("مؤشرات الرقابة والسلامة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• إجمالي العمليات المعتمدة: ${transactions.count { !it.isReversed }}")
                    Text("• العمليات المعكوسة (الموثقة): ${transactions.count { it.isReversed }}")
                    Text("• العملاء المؤرشفون: ${customers.count { it.isArchived }}")
                }
            }
        }
    }
}

// 14. استيراد البيانات
@Composable
fun ImportDataScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val inspection by viewModel.importInspection.collectAsStateWithLifecycle()
    val report by viewModel.importReport.collectAsStateWithLifecycle()
    val isBusy by viewModel.importBusy.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current

    var createSafetyBackup by remember { mutableStateOf(true) }
    var showConfirmImport by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectImportUri(uri)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "استيراد قواعد البيانات والملفات", onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("import_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FinancialCard {
                    Text(
                        "استيراد آمن لقواعد البيانات والجداول",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "يدعم تطبيقات المحاسبة العربية وخصوصاً (دفتر الحسابات) و(المحاسب الذكي برو) عبر قراءة الملفات دون تعديل الأصل ودون استبدال قاعدة بيانات التطبيق.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "الصيغ المدعومة: SQLite (.db / .sqlite / .sqlite3) • Excel (.xlsx / .xls) • CSV • JSON • النسخ الاحتياطية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pick_import_file_button"),
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("اختيار ملف للاستيراد (منتقي الملفات)")
                    }
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            report?.let { rep ->
                item {
                    FinancialCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("تقرير تنفيذ الاستيراد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• المصدر المكتشف: ${rep.sourceApp}")
                        Text("• العملاء المستوردون: ${rep.importedCustomersCount}", color = financial.success, fontWeight = FontWeight.Bold)
                        Text("• العمليات / الأرصدة الافتتاحية المسجلة: ${rep.importedTransactionsCount}")
                        Text("• السجلات المكررة المتجاهلة: ${rep.skippedDuplicatesCount}", color = financial.orange)
                        Text("• الأخطاء المكتشفة: ${rep.errorRowsCount}", color = if (rep.errorRowsCount > 0) financial.danger else financial.success)
                        rep.backupFilePath?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• تم إنشاء نسخة أمان تلقائية قبل الاستيراد.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (rep.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            rep.errors.take(5).forEach { err ->
                                Text("- $err", style = MaterialTheme.typography.bodySmall, color = financial.danger)
                            }
                        }
                    }
                }
            }

            inspection?.let { insp ->
                item {
                    FinancialCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الملف: ${insp.fileName}", fontWeight = FontWeight.Bold)
                                Text("الصيغة: ${insp.format.labelAr}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusBadge(insp.detectedSourceApp, MaterialTheme.colorScheme.secondary)
                        }

                        if (insp.tables.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("الجداول المكتشفة:", style = MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(insp.tables) { tbl ->
                                    FilterChip(
                                        selected = tbl.name == insp.selectedTableName,
                                        onClick = {
                                            val newMap = viewModel.inspector.suggestColumnMapping(tbl.columns)
                                            viewModel.updateImportMapping(tbl.name, newMap)
                                        },
                                        label = { Text("${tbl.name} (${tbl.rows.size})") },
                                    )
                                }
                            }
                        }

                        val activeTable = insp.tables.find { it.name == insp.selectedTableName }
                        if (activeTable != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("مطابقة الأعمدة التلقائية (قابلة للتعديل):", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            ColumnMappingRow("عمود الاسم *", insp.mapping.nameCol, activeTable.columns) { col ->
                                viewModel.updateImportMapping(activeTable.name, insp.mapping.copy(nameCol = col))
                            }
                            ColumnMappingRow("عمود الهاتف", insp.mapping.phoneCol, activeTable.columns) { col ->
                                viewModel.updateImportMapping(activeTable.name, insp.mapping.copy(phoneCol = col))
                            }
                            ColumnMappingRow("عمود الرصيد", insp.mapping.balanceCol, activeTable.columns) { col ->
                                viewModel.updateImportMapping(activeTable.name, insp.mapping.copy(balanceCol = col))
                            }
                            ColumnMappingRow("عمود الديون", insp.mapping.debtCol, activeTable.columns) { col ->
                                viewModel.updateImportMapping(activeTable.name, insp.mapping.copy(debtCol = col))
                            }
                            ColumnMappingRow("عمود المدفوعات", insp.mapping.paymentCol, activeTable.columns) { col ->
                                viewModel.updateImportMapping(activeTable.name, insp.mapping.copy(paymentCol = col))
                            }
                        }
                    }
                }

                item {
                    val readyCount = insp.previewRows.count { it.status == PreviewRowStatus.READY_NEW }
                    val dupCount = insp.previewRows.count { it.status == PreviewRowStatus.DUPLICATE_EXISTING || it.status == PreviewRowStatus.DUPLICATE_IN_FILE }
                    val errCount = insp.previewRows.count { it.status == PreviewRowStatus.INVALID_ERROR }

                    FinancialCard {
                        Text("معاينة قبل الحفظ (${insp.previewRows.size} سجل)", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge("جاهز: $readyCount", financial.success)
                            StatusBadge("مكرر: $dupCount", financial.orange)
                            StatusBadge("أخطاء: $errCount", financial.danger)
                        }
                        if (insp.linkedTransactionsCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تم اكتشاف ${insp.linkedTransactionsCount} عملية مالية مرتبطة سيتم استيرادها تفصيلياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = createSafetyBackup,
                                onCheckedChange = { createSafetyBackup = it },
                            )
                            Text("إنشاء نسخة احتياطية تلقائية قبل الحفظ (موصى به)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showConfirmImport = true },
                                enabled = readyCount > 0 && !isBusy,
                                modifier = Modifier.weight(1f).testTag("confirm_import_button"),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("تأكيد واستيراد ($readyCount)")
                            }
                            OutlinedButton(onClick = { viewModel.clearImportState() }) {
                                Text("إلغاء")
                            }
                        }
                    }
                }

                items(insp.previewRows.take(50)) { row ->
                    val statusColor = when (row.status) {
                        PreviewRowStatus.READY_NEW -> financial.success
                        PreviewRowStatus.DUPLICATE_EXISTING, PreviewRowStatus.DUPLICATE_IN_FILE -> financial.orange
                        PreviewRowStatus.INVALID_ERROR -> financial.danger
                    }
                    FinancialCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("#${row.rowNumber} ${row.name}", fontWeight = FontWeight.Bold)
                            StatusBadge(row.status.labelAr, statusColor)
                        }
                        if (row.phone.isNotBlank()) {
                            Text("الهاتف: ${row.phone}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (row.hasExplicitBreakdown) {
                            Text("ديون: ${FormatUtils.formatAmount(row.totalDebts)} | مدفوعات: ${FormatUtils.formatAmount(row.totalPayments)}", style = MaterialTheme.typography.bodySmall)
                        } else if (row.openingBalance > 0.0) {
                            Text("رصيد افتتاحي موثق: ${FormatUtils.formatAmount(row.openingBalance)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (row.reason.isNotBlank()) {
                            Text(row.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmImport) {
        ConfirmSensitiveActionDialog(
            title = "تأكيد استيراد البيانات",
            message = "سيتم حفظ العملاء الجدد وأرصدتهم الموثقة في قاعدة البيانات المحلية${if (createSafetyBackup) " بعد أخذ نسخة احتياطية تلقائية" else ""}. هل تريد المتابعة؟",
            confirmText = "استيراد الآن",
            onConfirm = {
                showConfirmImport = false
                viewModel.confirmAndExecuteImport(createSafetyBackup)
            },
            onDismiss = { showConfirmImport = false },
        )
    }
}

@Composable
private fun ColumnMappingRow(
    targetLabel: String,
    selectedCol: String?,
    availableCols: List<String>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(targetLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedCol ?: "غير محدد")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("غير محدد") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                availableCols.forEach { col ->
                    DropdownMenuItem(
                        text = { Text(col) },
                        onClick = {
                            onSelect(col)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// 15. تصدير البيانات
@Composable
fun ExportDataScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    var selectedDataset by remember { mutableStateOf(ExportDataset.CUSTOMERS) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.XLSX) }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(selectedFormat.mimeType)
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(selectedDataset, selectedFormat, uri)
        }
    }

    val exportSqliteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSqliteDatabaseToUri(uri)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "تصدير البيانات والتقارير", onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FinancialCard {
                    Text("1. اختر البيانات المراد تصديرها:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportDataset.entries.forEach { ds ->
                        FilterChip(
                            selected = selectedDataset == ds,
                            onClick = { selectedDataset = ds },
                            label = { Text(ds.titleAr) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("2. اختر صيغة الملف:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.titleAr) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val fileName = "SmartDebt_${selectedDataset.name.lowercase()}.${selectedFormat.extension}"
                            createDocLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("export_save_button"),
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("حفظ الملف عبر منتقي الملفات")
                    }
                }
            }

            item {
                FinancialCard {
                    Text("تصدير نسخة قاعدة بيانات SQLite (.db) الكاملة", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "تصدير ملف SQLite الكامل إلى وحدة تخزين الجهاز لحفظ جميع الجداول والأرصدة والتذكيرات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { exportSqliteLauncher.launch(BackupManager.SQLITE_BACKUP_FILENAME) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_sqlite_from_export_screen_button"),
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تصدير ملف SQLite (.db)")
                    }
                }
            }
        }
    }
}

// 16. النسخ الاحتياطي والاستعادة
@Composable
fun BackupRestoreScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val validation by viewModel.backupValidation.collectAsStateWithLifecycle()
    val sqliteValidation by viewModel.sqliteValidation.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current

    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showSqliteRestoreConfirm by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.saveBackupToUri(uri)
        }
    }

    val pickRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupFromUri(uri)
        }
    }

    val exportSqliteDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSqliteDatabaseToUri(uri)
        }
    }

    val importSqliteDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectSqliteBackupUri(uri)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "النسخ الاحتياطي والاستعادة", onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FinancialCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("تصدير واستيراد قاعدة بيانات SQLite (.db) محلياً", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "احفظ نسخة كاملة من ملف قاعدة البيانات (${BackupManager.SQLITE_BACKUP_FILENAME}) على جهازك أو استعدها مع فحص سلامة الجداول (PRAGMA quick_check).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { exportSqliteDbLauncher.launch(BackupManager.SQLITE_BACKUP_FILENAME) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_sqlite_db_button"),
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تصدير قاعدة بيانات SQLite إلى ملف (.db)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.saveLocalSqliteBackup() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_local_sqlite_button"),
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("حفظ نسخة SQLite سريعة داخل الجهاز")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { importSqliteDbLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("import_sqlite_backup_button"),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("استيراد واستعادة ملف SQLite (.db)")
                    }
                }
            }

            sqliteValidation?.let { sqlVal ->
                item {
                    FinancialCard(
                        containerColor = if (sqlVal.isValid) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (sqlVal.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (sqlVal.isValid) financial.success else financial.danger,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (sqlVal.isValid) "تم فحص ملف SQLite بنجاح" else "ملف SQLite غير صالح للاستعادة",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!sqlVal.isValid) {
                            Text(sqlVal.errorMessage ?: "خطأ غير معروف", color = financial.danger)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.clearSqliteValidation() }) {
                                Text("إغلاق")
                            }
                        } else {
                            Text("• فحص السلامة (PRAGMA quick_check): سليم 100%")
                            Text("• العملاء: ${sqlVal.customersCount} | العمليات: ${sqlVal.transactionsCount}")
                            Text("• التذكيرات: ${sqlVal.remindersCount} | الحوالات: ${sqlVal.transfersCount}")
                            Text("• الحجم: ${sqlVal.fileSizeBytes / 1024} KB")
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showSqliteRestoreConfirm = true },
                                    modifier = Modifier.weight(1f).testTag("confirm_sqlite_restore_button"),
                                ) {
                                    Text("استعادة قاعدة SQLite الآن")
                                }
                                OutlinedButton(onClick = { viewModel.clearSqliteValidation() }) {
                                    Text("إلغاء")
                                }
                            }
                        }
                    }
                }
            }

            item {
                FinancialCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = financial.success)
                        Spacer(Modifier.width(8.dp))
                        Text("النسخة الاحتياطية المشفرة JSON (${BackupManager.BACKUP_FILENAME})", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "يشمل العملاء، الديون، المدفوعات، الأرصدة الافتتاحية، الحوالات، التذكيرات، الإعدادات، وسجل العمليات مع بصمة تحقق SHA-256.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { createBackupLauncher.launch(BackupManager.BACKUP_FILENAME) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_backup_button"),
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إنشاء وحفظ ${BackupManager.BACKUP_FILENAME}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pick_restore_backup_button"),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("اختيار نسخة JSON للاستعادة والفحص")
                    }
                }
            }

            validation?.let { valRes ->
                item {
                    FinancialCard(
                        containerColor = if (valRes.isValid) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (valRes.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (valRes.isValid) financial.success else financial.danger,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (valRes.isValid) "تم التحقق من سلامة النسخة بنجاح" else "ملف النسخة الاحتياطية غير صالح",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!valRes.isValid) {
                            Text(valRes.errorMessage ?: "خطأ غير معروف", color = financial.danger)
                        } else if (valRes.data != null) {
                            val d = valRes.data
                            Text("• التحقق من SHA-256: ${if (valRes.checksumVerified) "سليم 100%" else "غير متوفر"}")
                            Text("• تاريخ النسخة: ${FormatUtils.formatDateTime(valRes.createdAt)}")
                            Text("• العملاء: ${d.customers.size} | العمليات: ${d.transactions.size}")
                            Text("• الحوالات: ${d.transfers.size} | التذكيرات: ${d.reminders.size}")
                            valRes.warnings.forEach { w ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = financial.orange, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(w, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showRestoreConfirm = true },
                                    modifier = Modifier.weight(1f).testTag("confirm_restore_button"),
                                ) {
                                    Text("استعادة البيانات الآن")
                                }
                                OutlinedButton(onClick = { viewModel.clearBackupValidation() }) {
                                    Text("إغلاق")
                                }
                            }
                        }
                    }
                }
            }

            if (localBackups.isNotEmpty()) {
                item {
                    Text(
                        "النسخ المحفوظة محلياً (${localBackups.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(localBackups, key = { it.absolutePath }) { file ->
                    FinancialCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${FormatUtils.formatDateTime(file.lastModified())} • ${file.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { viewModel.inspectLocalBackupFile(file) }) {
                                Text("فحص")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRestoreConfirm) {
        ConfirmSensitiveActionDialog(
            title = "تأكيد استعادة النسخة الاحتياطية",
            message = "ستحل بيانات النسخة الاحتياطية المفحوصة محل البيانات الحالية بشكل كامل وآمن. هل أنت متأكد؟",
            confirmText = "استعادة الآن",
            onConfirm = {
                showRestoreConfirm = false
                viewModel.confirmRestoreBackup()
            },
            onDismiss = { showRestoreConfirm = false },
        )
    }

    if (showSqliteRestoreConfirm) {
        ConfirmSensitiveActionDialog(
            title = "تأكيد استعادة قاعدة بيانات SQLite",
            message = "سيتم إنشاء نسخة أمان تلقائية للبيانات الحالية ثم استبدالها ببيانات ملف SQLite المفحوص. هل تريد المتابعة؟",
            confirmText = "استعادة قاعدة البيانات",
            onConfirm = {
                showSqliteRestoreConfirm = false
                viewModel.confirmRestoreSqliteBackup()
            },
            onDismiss = { showSqliteRestoreConfirm = false },
        )
    }
}
