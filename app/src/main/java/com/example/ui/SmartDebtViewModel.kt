package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.assistant.AssistantMessage
import com.example.data.assistant.LocalSmartAssistant
import com.example.data.importexport.BackupManager
import com.example.data.importexport.BackupValidationResult
import com.example.data.importexport.ColumnMapping
import com.example.data.importexport.ExportDataset
import com.example.data.importexport.ExportFormat
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.importexport.ImportExecutionReport
import com.example.data.importexport.ImportInspectionResult
import com.example.data.importexport.PaymentReceiptPdfGenerator
import com.example.data.importexport.SqliteBackupValidationResult
import com.example.data.local.AccountingRepository
import com.example.data.local.AccountingResult
import com.example.data.local.AuditLogEntity
import com.example.data.local.CustomerWithBalance
import com.example.data.local.DashboardSummary
import com.example.data.local.MessageLogEntity
import com.example.data.local.MessageStatus
import com.example.data.local.ReminderStatus
import com.example.data.local.ReminderWithCustomer
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.SmsTemplateEntity
import com.example.data.local.TransactionType
import com.example.data.local.TransactionWithCustomer
import com.example.data.local.TransferCompany
import com.example.data.local.TransferWithCustomer
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.sms.DirectSmsSender
import com.example.data.sms.SmsDispatchResult
import com.example.data.sms.SmsTemplateEngine
import com.example.ui.theme.AppColorPalette
import com.example.ui.theme.AppThemeMode
import com.example.worker.OverdueReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiFeedback(val message: String, val isError: Boolean = false)

class SmartDebtViewModel(
    application: Application,
    val repository: AccountingRepository,
    val settingsDataStore: SettingsDataStore,
    val backupManager: BackupManager,
    val exportManager: ExportManager,
    val inspector: ExternalDbInspector,
) : AndroidViewModel(application) {

    private val assistant = LocalSmartAssistant()

    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val dashboardSummary: StateFlow<DashboardSummary> = repository.observeDashboardSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    val customers: StateFlow<List<CustomerWithBalance>> = repository.observeCustomersWithBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionWithCustomer>> = repository.observeAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderWithCustomer>> = repository.observeAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transfers: StateFlow<List<TransferWithCustomer>> = repository.observeAllTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messageLogs: StateFlow<List<MessageLogEntity>> = repository.observeMessageLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsTemplates: StateFlow<List<SmsTemplateEntity>> = repository.observeSmsTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _feedback = MutableSharedFlow<UiFeedback>()
    val feedback: SharedFlow<UiFeedback> = _feedback.asSharedFlow()

    // Import State
    private val _importInspection = MutableStateFlow<ImportInspectionResult?>(null)
    val importInspection: StateFlow<ImportInspectionResult?> = _importInspection.asStateFlow()

    private val _importReport = MutableStateFlow<ImportExecutionReport?>(null)
    val importReport: StateFlow<ImportExecutionReport?> = _importReport.asStateFlow()

    private val _importBusy = MutableStateFlow(false)
    val importBusy: StateFlow<Boolean> = _importBusy.asStateFlow()

    // Backup State
    private val _backupValidation = MutableStateFlow<BackupValidationResult?>(null)
    val backupValidation: StateFlow<BackupValidationResult?> = _backupValidation.asStateFlow()

    private val _sqliteValidation = MutableStateFlow<SqliteBackupValidationResult?>(null)
    val sqliteValidation: StateFlow<SqliteBackupValidationResult?> = _sqliteValidation.asStateFlow()

    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    // Biometric App Lock State (starts locked; only enforced when settings.biometricLockEnabled == true)
    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    // Local Assistant Chat State
    private val _assistantMessages = MutableStateFlow(
        listOf(
            AssistantMessage(
                isUser = false,
                text = "أهلاً بك في المساعد الذكي المحلي! أعمل بالكامل دون إنترنت على قاعدة بياناتك المحلية لتلخيص الديون، رصد المتأخرين، تحليل الحوالات، والإجابة عن أي عميل.",
            )
        )
    )
    val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshOverdueStatuses()
            refreshLocalBackups()
        }
    }

    fun emitFeedback(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _feedback.emit(UiFeedback(message, isError))
        }
    }

    fun createCustomer(
        name: String,
        phone: String,
        accountNumber: String,
        creditLimit: Double,
        notes: String,
        openingBalance: Double,
        onSuccess: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.createCustomer(name, phone, accountNumber, creditLimit, notes, openingBalance)) {
                is AccountingResult.Success -> {
                    emitFeedback(res.message)
                    onSuccess(res.data)
                }
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun updateCustomer(
        customerId: Long,
        name: String,
        phone: String,
        accountNumber: String,
        creditLimit: Double,
        notes: String,
        isArchived: Boolean,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.updateCustomer(customerId, name, phone, accountNumber, creditLimit, notes, isArchived)) {
                is AccountingResult.Success -> {
                    emitFeedback(res.message)
                    onSuccess()
                }
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun recordTransaction(
        customerId: Long,
        type: TransactionType,
        amount: Double,
        description: String,
        dateMillis: Long,
        referenceNumber: String? = null,
        onSuccess: () -> Unit,
    ) {
        recordTransactionWithId(
            customerId = customerId,
            type = type,
            amount = amount,
            description = description,
            dateMillis = dateMillis,
            referenceNumber = referenceNumber,
            onSuccess = { _ -> onSuccess() },
        )
    }

    fun recordTransactionWithId(
        customerId: Long,
        type: TransactionType,
        amount: Double,
        description: String,
        dateMillis: Long,
        referenceNumber: String? = null,
        onSuccess: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val curSettings = settings.value
            val res = repository.recordTransaction(
                customerId = customerId,
                type = type,
                amount = amount,
                description = description,
                transactionDate = dateMillis,
                referenceNumber = referenceNumber,
                allowOverpayment = curSettings.allowOverpayment,
                duplicateWindowMinutes = curSettings.duplicateWindowMinutes,
            )
            when (res) {
                is AccountingResult.Success -> {
                    emitFeedback(res.message)
                    onSuccess(res.data)
                }
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    // PDF Receipt actions (Android PdfDocument + FileProvider + PrintManager)
    fun sharePaymentReceipt(context: Context, tx: TransactionWithCustomer, customer: CustomerWithBalance?) {
        viewModelScope.launch {
            val curSettings = settings.value
            val fileRes = withContext(Dispatchers.IO) {
                PaymentReceiptPdfGenerator.createTempReceiptFile(context, tx, customer, curSettings)
            }
            fileRes.onSuccess { file ->
                val shared = PaymentReceiptPdfGenerator.shareReceiptPdf(context, file, tx.customerName)
                if (shared) {
                    emitFeedback("تم تجهيز إيصال السداد PDF لمشاركته (${tx.customerName})")
                } else {
                    emitFeedback("تعذر فتح نافذة المشاركة على هذا الجهاز.", isError = true)
                }
            }.onFailure { err ->
                emitFeedback("فشل إنشاء ملف PDF: ${err.message}", isError = true)
            }
        }
    }

    fun savePaymentReceiptToUri(uri: Uri, tx: TransactionWithCustomer, customer: CustomerWithBalance?) {
        viewModelScope.launch {
            val curSettings = settings.value
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        PaymentReceiptPdfGenerator.writeReceiptPdf(tx, customer, curSettings, out).getOrThrow()
                    } ?: error("تعذر فتح مسار حفظ ملف PDF")
                }
            }
            res.onSuccess {
                emitFeedback("تم حفظ إيصال السداد PDF بنجاح")
            }.onFailure { err ->
                emitFeedback("فشل حفظ إيصال PDF: ${err.message}", isError = true)
            }
        }
    }

    fun printPaymentReceipt(context: Context, tx: TransactionWithCustomer, customer: CustomerWithBalance?) {
        val launched = PaymentReceiptPdfGenerator.printReceiptPdf(context, tx, customer, settings.value)
        if (launched) {
            emitFeedback("تم فتح واجهة الطباعة لنظام أندرويد")
        } else {
            emitFeedback("خدمة الطباعة غير متوفرة على هذا الجهاز.", isError = true)
        }
    }

    fun reverseTransaction(transactionId: Long, reason: String) {
        viewModelScope.launch {
            when (val res = repository.reverseTransaction(transactionId, reason)) {
                is AccountingResult.Success -> emitFeedback(res.message)
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun addTransfer(
        company: TransferCompany,
        referenceNumber: String,
        amount: Double,
        senderName: String,
        transferDate: Long,
        customerId: Long?,
        notes: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.addTransfer(company, referenceNumber, amount, senderName, transferDate, customerId, notes)) {
                is AccountingResult.Success -> {
                    emitFeedback(res.message)
                    onSuccess()
                }
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun confirmTransferAsPayment(transferId: Long, customerId: Long) {
        viewModelScope.launch {
            val res = repository.confirmTransferAsPayment(
                transferId = transferId,
                targetCustomerId = customerId,
                allowOverpayment = settings.value.allowOverpayment,
            )
            when (res) {
                is AccountingResult.Success -> emitFeedback(res.message)
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun cancelTransfer(transferId: Long) {
        viewModelScope.launch {
            when (val res = repository.cancelTransfer(transferId)) {
                is AccountingResult.Success -> emitFeedback(res.message)
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun addReminder(customerId: Long, title: String, dueDate: Long, amountDue: Double, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val res = repository.addReminder(customerId, title, dueDate, amountDue, notes)) {
                is AccountingResult.Success -> {
                    emitFeedback(res.message)
                    onSuccess()
                }
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun updateReminderStatus(id: Long, status: ReminderStatus) {
        viewModelScope.launch {
            repository.updateReminderStatus(id, status)
            emitFeedback("تم تحديث حالة التذكير")
        }
    }

    fun snoozeReminder(id: Long, days: Int = 3) {
        viewModelScope.launch {
            repository.snoozeReminder(id, days)
            emitFeedback("تم تأجيل التذكير لمدة $days أيام")
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
            emitFeedback("تم حذف التذكير")
        }
    }

    fun openSmsIntentForCustomer(
        context: Context,
        customer: CustomerWithBalance,
        messageBody: String,
        templateName: String,
    ) {
        if (customer.phone.isBlank()) {
            emitFeedback("العميل ${customer.name} لا يملك رقم هاتف مسجل.", isError = true)
            return
        }
        viewModelScope.launch {
            val logId = repository.logSmsIntentOpened(
                customerId = customer.id,
                customerName = customer.name,
                phone = customer.phone,
                messageBody = messageBody,
                templateName = templateName,
            )
            val launched = SmsTemplateEngine.launchSmsApp(context, customer.phone, messageBody)
            if (launched) {
                emitFeedback("تم فتح تطبيق الرسائل (${customer.name}). يرجى تأكيد الإرسال يدوياً بعد الإرسال من تطبيق الرسائل.")
            } else {
                repository.updateMessageStatus(logId, MessageStatus.CANCELLED)
                emitFeedback("لم يتم العثور على تطبيق رسائل نصية على الجهاز.", isError = true)
            }
        }
    }

    fun sendDirectDebtAlertViaSmsManager(
        context: Context,
        customer: CustomerWithBalance,
        messageBody: String,
        templateName: String,
        onNeedPermission: () -> Unit,
    ) {
        if (!DirectSmsSender.hasSendSmsPermission(context)) {
            onNeedPermission()
            return
        }
        if (customer.phone.isBlank()) {
            emitFeedback("العميل ${customer.name} لا يملك رقم هاتف مسجل.", isError = true)
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                DirectSmsSender.sendDebtAlert(
                    context = context,
                    phone = customer.phone,
                    messageBody = messageBody,
                    logId = customer.id,
                )
            }
            when (result) {
                is SmsDispatchResult.Dispatched -> {
                    repository.logDirectSmsDispatch(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = result.normalizedPhone,
                        messageBody = messageBody,
                        templateName = templateName,
                        status = MessageStatus.SENT_VIA_SMS_MANAGER,
                        details = "أجزاء الرسالة: ${result.partsCount}",
                    )
                    emitFeedback("تم إرسال تنبيه الدين لـ ${customer.name} عبر SmsManager بنجاح")
                }
                SmsDispatchResult.PermissionDenied -> {
                    onNeedPermission()
                    emitFeedback("يرجى منح صلاحية إرسال الرسائل النصية (SEND_SMS) أولاً.", isError = true)
                }
                SmsDispatchResult.NoTelephonySupport -> {
                    repository.logDirectSmsDispatch(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = customer.phone,
                        messageBody = messageBody,
                        templateName = templateName,
                        status = MessageStatus.SEND_FAILED,
                        details = "الجهاز لا يدعم خدمة الإرسال الخلوي المباشر",
                    )
                    emitFeedback("الجهاز لا يدعم SmsManager؛ يمكنك استخدام زر فتح تطبيق الرسائل كبديل.", isError = true)
                }
                is SmsDispatchResult.InvalidInput -> {
                    emitFeedback(result.reason, isError = true)
                }
                is SmsDispatchResult.Failed -> {
                    repository.logDirectSmsDispatch(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = customer.phone,
                        messageBody = messageBody,
                        templateName = templateName,
                        status = MessageStatus.SEND_FAILED,
                        details = result.reason,
                    )
                    emitFeedback("فشل الإرسال لـ ${customer.name}: ${result.reason}", isError = true)
                }
            }
        }
    }

    fun sendBulkDebtAlertsViaSmsManager(
        context: Context,
        recipients: List<CustomerWithBalance>,
        templateBody: String,
        templateName: String,
        onNeedPermission: () -> Unit,
    ) {
        if (!DirectSmsSender.hasSendSmsPermission(context)) {
            onNeedPermission()
            return
        }
        if (recipients.isEmpty()) {
            emitFeedback("يرجى تحديد عميل واحد على الأقل.", isError = true)
            return
        }
        viewModelScope.launch {
            val curSettings = settings.value
            var sentCount = 0
            var failedCount = 0
            var skippedNoPhone = 0
            for (customer in recipients) {
                if (customer.phone.isBlank()) {
                    skippedNoPhone++
                    continue
                }
                val body = SmsTemplateEngine.renderMessage(templateBody, customer, curSettings)
                val result = withContext(Dispatchers.IO) {
                    DirectSmsSender.sendDebtAlert(
                        context = context,
                        phone = customer.phone,
                        messageBody = body,
                        logId = customer.id,
                    )
                }
                when (result) {
                    is SmsDispatchResult.Dispatched -> {
                        sentCount++
                        repository.logDirectSmsDispatch(
                            customerId = customer.id,
                            customerName = customer.name,
                            phone = result.normalizedPhone,
                            messageBody = body,
                            templateName = templateName,
                            status = MessageStatus.SENT_VIA_SMS_MANAGER,
                            details = "إرسال جماعي (${result.partsCount} جزء)",
                        )
                    }
                    SmsDispatchResult.PermissionDenied -> {
                        onNeedPermission()
                        emitFeedback("توقف الإرسال لعدم توفر صلاحية SEND_SMS.", isError = true)
                        return@launch
                    }
                    else -> {
                        failedCount++
                        val reason = when (result) {
                            is SmsDispatchResult.Failed -> result.reason
                            is SmsDispatchResult.InvalidInput -> result.reason
                            SmsDispatchResult.NoTelephonySupport -> "لا يوجد دعم SmsManager"
                            else -> "خطأ غير معروف"
                        }
                        repository.logDirectSmsDispatch(
                            customerId = customer.id,
                            customerName = customer.name,
                            phone = customer.phone,
                            messageBody = body,
                            templateName = templateName,
                            status = MessageStatus.SEND_FAILED,
                            details = reason,
                        )
                    }
                }
            }
            emitFeedback(
                "نتائج الإرسال عبر SmsManager: أُرسلت $sentCount، تعذرت $failedCount، بدون رقم $skippedNoPhone",
                isError = sentCount == 0 && failedCount > 0,
            )
        }
    }

    fun updateMessageStatus(logId: Long, status: MessageStatus) {
        viewModelScope.launch {
            repository.updateMessageStatus(logId, status)
            emitFeedback("تم تحديث حالة الرسالة: ${status.labelAr}")
        }
    }

    fun saveSmsTemplate(id: Long?, name: String, body: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val res = repository.saveSmsTemplate(id, name, body)) {
                is AccountingResult.Success -> {
                    emitFeedback(res.message)
                    onSuccess()
                }
                is AccountingResult.Error -> emitFeedback(res.reason, isError = true)
            }
        }
    }

    fun deleteSmsTemplate(id: Long) {
        viewModelScope.launch {
            repository.deleteSmsTemplate(id)
            emitFeedback("تم حذف القالب")
        }
    }

    // Import workflow
    fun inspectImportUri(uri: Uri) {
        viewModelScope.launch {
            _importBusy.value = true
            _importReport.value = null
            val res = withContext(Dispatchers.IO) {
                inspector.inspectUri(getApplication(), uri)
            }
            res.onSuccess {
                _importInspection.value = it
                emitFeedback("تم فحص الملف بنجاح: ${it.previewRows.size} سجل مكتشف")
            }.onFailure { err ->
                emitFeedback(err.message ?: "فشل قراءة الملف", isError = true)
            }
            _importBusy.value = false
        }
    }

    fun updateImportMapping(tableName: String, mapping: ColumnMapping) {
        val current = _importInspection.value ?: return
        viewModelScope.launch {
            _importBusy.value = true
            val updated = withContext(Dispatchers.IO) {
                inspector.rebuildPreview(current, tableName, mapping)
            }
            _importInspection.value = updated
            _importBusy.value = false
        }
    }

    fun confirmAndExecuteImport(createSafetyBackup: Boolean) {
        val current = _importInspection.value ?: return
        viewModelScope.launch {
            _importBusy.value = true
            val report = withContext(Dispatchers.IO) {
                inspector.executeImport(getApplication(), current, createSafetyBackup)
            }
            _importReport.value = report
            _importInspection.value = null
            refreshLocalBackups()
            emitFeedback("اكتمل الاستيراد: ${report.importedCustomersCount} عميل، ${report.importedTransactionsCount} عملية")
            _importBusy.value = false
        }
    }

    fun clearImportState() {
        _importInspection.value = null
        _importReport.value = null
    }

    // Export workflow
    fun exportToUri(dataset: ExportDataset, format: ExportFormat, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val (title, table) = when (dataset) {
                        ExportDataset.CUSTOMERS -> "العملاء" to exportManager.buildCustomersTable(customers.value)
                        ExportDataset.TRANSACTIONS -> "العمليات" to exportManager.buildTransactionsTable(transactions.value)
                        ExportDataset.TRANSFERS -> "الحوالات" to exportManager.buildTransfersTable(transfers.value)
                        ExportDataset.FULL_REPORT -> "التقرير_الشامل" to exportManager.buildCustomersTable(customers.value)
                    }
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        when (format) {
                            ExportFormat.CSV -> exportManager.writeCsv(table.first, table.second, out)
                            ExportFormat.XLSX -> exportManager.writeXlsx(title, table.first, table.second, out)
                            ExportFormat.JSON -> exportManager.writeJson(dataset.titleAr, table.first, table.second, out)
                        }
                    } ?: error("تعذر فتح مسار الحفظ")
                }
            }
            result.onSuccess { emitFeedback("تم تصدير (${dataset.titleAr}) بصيغة ${format.extension.uppercase()} بنجاح") }
                .onFailure { emitFeedback("فشل التصدير: ${it.message}", isError = true) }
        }
    }

    fun exportCustomerStatementToUri(customer: CustomerWithBalance, txs: List<TransactionWithCustomer>, format: ExportFormat, uri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val table = exportManager.buildStatementTable(customer, txs)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        when (format) {
                            ExportFormat.CSV -> exportManager.writeCsv(table.first, table.second, out)
                            ExportFormat.XLSX -> exportManager.writeXlsx("كشف_${customer.name}", table.first, table.second, out)
                            ExportFormat.JSON -> exportManager.writeJson("كشف حساب ${customer.name}", table.first, table.second, out)
                        }
                    } ?: error("تعذر فتح مسار الحفظ")
                }
            }
            res.onSuccess { emitFeedback("تم تصدير كشف حساب ${customer.name} بنجاح") }
                .onFailure { emitFeedback("فشل تصدير كشف الحساب: ${it.message}", isError = true) }
        }
    }

    // Backup & Restore workflow
    fun saveBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val json = backupManager.createBackupJsonString()
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("تعذر الكتابة إلى الملف المحدد")
                    backupManager.createLocalSafetyBackup(getApplication(), "manual")
                }
            }
            refreshLocalBackups()
            res.onSuccess { emitFeedback("تم إنشاء وحفظ النسخة الاحتياطية ${BackupManager.BACKUP_FILENAME} بنجاح") }
                .onFailure { emitFeedback("فشل إنشاء النسخة الاحتياطية: ${it.message}", isError = true) }
        }
    }

    fun inspectBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val validation = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("تعذر قراءة الملف")
                    backupManager.inspectBackupJson(raw)
                }.getOrElse {
                    BackupValidationResult(false, false, 0L, 0, errorMessage = it.message)
                }
            }
            _backupValidation.value = validation
            if (!validation.isValid) {
                emitFeedback(validation.errorMessage ?: "فشل التحقق من النسخة", isError = true)
            }
        }
    }

    fun inspectLocalBackupFile(file: File) {
        if (file.extension.equals("db", ignoreCase = true) || file.extension.equals("sqlite", ignoreCase = true) || file.extension.equals("sqlite3", ignoreCase = true)) {
            inspectLocalSqliteBackupFile(file)
            return
        }
        viewModelScope.launch {
            val validation = withContext(Dispatchers.IO) {
                runCatching {
                    backupManager.inspectBackupJson(file.readText(Charsets.UTF_8))
                }.getOrElse {
                    BackupValidationResult(false, false, 0L, 0, errorMessage = it.message)
                }
            }
            _backupValidation.value = validation
        }
    }

    fun confirmRestoreBackup() {
        val current = _backupValidation.value ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                backupManager.restoreFromValidatedBackup(current)
            }
            res.onSuccess { msg ->
                _backupValidation.value = null
                emitFeedback(msg)
            }.onFailure { err ->
                emitFeedback(err.message ?: "فشلت الاستعادة", isError = true)
            }
        }
    }

    fun clearBackupValidation() {
        _backupValidation.value = null
    }

    // Native SQLite (.db) Export & Restore workflow
    fun exportSqliteDatabaseToUri(uri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                backupManager.exportSqliteDatabaseToUri(getApplication(), uri)
            }
            refreshLocalBackups()
            res.onSuccess {
                emitFeedback("تم تصدير قاعدة بيانات SQLite (${BackupManager.SQLITE_BACKUP_FILENAME}) بنجاح")
            }.onFailure { err ->
                emitFeedback("فشل تصدير قاعدة بيانات SQLite: ${err.message}", isError = true)
            }
        }
    }

    fun saveLocalSqliteBackup() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { backupManager.createLocalSqliteBackup(getApplication(), "sqlite_local") }
            }
            refreshLocalBackups()
            res.onSuccess { file ->
                emitFeedback("تم حفظ نسخة SQLite محلياً باسم ${file.name}")
            }.onFailure { err ->
                emitFeedback("فشل حفظ نسخة SQLite محلياً: ${err.message}", isError = true)
            }
        }
    }

    fun inspectSqliteBackupUri(uri: Uri) {
        viewModelScope.launch {
            val validation = withContext(Dispatchers.IO) {
                backupManager.inspectSqliteBackupUri(getApplication(), uri)
            }
            _sqliteValidation.value = validation
            if (!validation.isValid) {
                emitFeedback(validation.errorMessage ?: "فشل فحص قاعدة بيانات SQLite", isError = true)
            }
        }
    }

    fun inspectLocalSqliteBackupFile(file: File) {
        viewModelScope.launch {
            val validation = withContext(Dispatchers.IO) {
                backupManager.inspectSqliteBackupFile(getApplication(), file)
            }
            _sqliteValidation.value = validation
            if (!validation.isValid) {
                emitFeedback(validation.errorMessage ?: "فشل فحص ملف SQLite المحلي", isError = true)
            }
        }
    }

    fun confirmRestoreSqliteBackup() {
        val current = _sqliteValidation.value ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                backupManager.restoreFromValidatedSqliteBackup(getApplication(), current)
            }
            refreshLocalBackups()
            res.onSuccess { msg ->
                _sqliteValidation.value = null
                emitFeedback(msg)
            }.onFailure { err ->
                emitFeedback(err.message ?: "فشلت استعادة قاعدة بيانات SQLite", isError = true)
            }
        }
    }

    fun clearSqliteValidation() {
        _sqliteValidation.value = null
    }

    private fun refreshLocalBackups() {
        _localBackups.value = backupManager.listLocalBackups(getApplication())
    }

    // Settings
    fun updateStoreInfo(storeName: String, contactPhone: String, currencySymbol: String) {
        viewModelScope.launch {
            settingsDataStore.updateStoreInfo(storeName, contactPhone, currencySymbol)
            emitFeedback("تم حفظ بيانات المتجر")
        }
    }

    fun updateTheme(themeMode: AppThemeMode, palette: AppColorPalette) {
        viewModelScope.launch {
            settingsDataStore.updateTheme(themeMode, palette)
        }
    }

    fun updateAccountingRules(allowOverpayment: Boolean, duplicateWindowMinutes: Int, overdueDays: Int) {
        viewModelScope.launch {
            settingsDataStore.updateAccountingRules(allowOverpayment, duplicateWindowMinutes, overdueDays)
            emitFeedback("تم تحديث القواعد المحاسبية")
        }
    }

    fun setWorkManagerReminders(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateWorkManagerReminders(enabled)
            runCatching {
                if (enabled) OverdueReminderWorker.schedule(getApplication())
                else OverdueReminderWorker.cancel(getApplication())
            }
            emitFeedback(
                if (enabled) "تم تفعيل فحص WorkManager الدوري كل 12 ساعة للتنبيه قبل الاستحقاق وعند التأخر"
                else "تم إيقاف فحص WorkManager الدوري"
            )
        }
    }

    fun triggerDueDateCheckNow() {
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                runCatching { OverdueReminderWorker.runImmediateCheck(getApplication()) }
                OverdueReminderWorker.runDueDateScan(
                    context = getApplication(),
                    db = SmartDebtDatabase.getInstance(getApplication()),
                )
            }
            emitFeedback(
                "تم فحص مواعيد الاستحقاق: ${summary.upcomingBeforeDueCount} قريبة الاستحقاق (خلال 48 ساعة)، ${summary.newlyOverdueCount} متأخرة"
            )
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateBiometricLock(enabled)
            if (enabled) {
                _isSessionUnlocked.value = true
                emitFeedback("تم تفعيل حماية التطبيق بالبصمة / الوجه لحماية بيانات الديون")
            } else {
                emitFeedback("تم إيقاف قفل البصمة")
            }
        }
    }

    fun unlockSession() {
        _isSessionUnlocked.value = true
    }

    fun lockSession() {
        _isSessionUnlocked.value = false
    }

    // Assistant
    fun askLocalAssistant(query: String) {
        if (query.isBlank()) return
        val userMsg = AssistantMessage(isUser = true, text = query.trim())
        val reply = assistant.answerQuery(
            query = query,
            summary = dashboardSummary.value,
            customers = customers.value,
            transactions = transactions.value,
            transfers = transfers.value,
            reminders = reminders.value,
            currency = settings.value.currencySymbol,
        )
        _assistantMessages.value = _assistantMessages.value + userMsg + reply
    }

    fun getQuickAssistantPrompts(): List<String> = assistant.quickPrompts

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = SmartDebtDatabase.getInstance(application)
            val repo = AccountingRepository(db)
            val settingsStore = SettingsDataStore(application)
            val backupMgr = BackupManager(db, settingsStore)
            val exportMgr = ExportManager()
            val inspector = ExternalDbInspector(db, backupMgr)
            return SmartDebtViewModel(
                application = application,
                repository = repo,
                settingsDataStore = settingsStore,
                backupManager = backupMgr,
                exportManager = exportMgr,
                inspector = inspector,
            ) as T
        }
    }
}
