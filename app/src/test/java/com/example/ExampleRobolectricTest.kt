package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.importexport.PaymentReceiptPdfGenerator
import com.example.data.local.AccountingRepository
import com.example.data.local.AccountingResult
import com.example.data.local.Clients
import com.example.data.local.Debt
import com.example.data.local.MessageStatus
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionType
import com.example.data.local.TransactionWithCustomer
import com.example.data.local.TransferCompany
import com.example.data.local.TransferStatus
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.sms.DirectSmsSender
import com.example.data.sms.SmsDispatchResult
import com.example.data.sms.SmsGateway
import com.example.data.sms.SmsTemplateEngine
import com.example.security.BiometricAuthHelper
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.BiometricLockScreen
import com.example.ui.components.DebtVsPaymentColumnChart
import com.example.ui.components.PaymentReceiptDialog
import com.example.ui.navigation.AppRoutes
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.TransactionsLogScreen
import com.example.ui.theme.SmartDebtTheme
import com.example.worker.OverdueReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SmartDebtDatabase
    private lateinit var repo: AccountingRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SmartDebtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AccountingRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `app metadata and package id match specification`() {
        assertEquals("مدير الديون الذكي برو SMS", context.getString(R.string.app_name))
        assertEquals("com.smartdebt.app", context.packageName)
    }

    @Test
    fun `create and update customer calculates opening balance accurately`() = runBlocking {
        val createRes = repo.createCustomer(
            name = "أحمد محمد صالح",
            phone = "777123456",
            accountNumber = "ACC-101",
            creditLimit = 50000.0,
            notes = "عميل جملة",
            openingBalance = 12000.0,
        )
        assertTrue(createRes is AccountingResult.Success)
        val cid = (createRes as AccountingResult.Success).data

        val updateRes = repo.updateCustomer(
            customerId = cid,
            name = "أحمد محمد صالح الحداد",
            phone = "777123456",
            accountNumber = "ACC-101",
            creditLimit = 60000.0,
            notes = "تم التحديث",
            isArchived = false,
        )
        assertTrue(updateRes is AccountingResult.Success)

        val customer = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        assertEquals("أحمد محمد صالح الحداد", customer.name)
        assertEquals(12000.0, customer.totalDebts, 0.01)
        assertEquals(0.0, customer.totalPayments, 0.01)
        assertEquals(12000.0, customer.balance, 0.01)
    }

    @Test
    fun `record debt and payment calculates balance and prevents zero negative overpayment and duplicates`() = runBlocking {
        val cid = (repo.createCustomer("سالم علي", "771112223", "", 0.0, "") as AccountingResult.Success).data

        // Reject zero and negative amounts
        assertTrue(repo.recordTransaction(cid, TransactionType.DEBT, 0.0, "صفر") is AccountingResult.Error)
        assertTrue(repo.recordTransaction(cid, TransactionType.DEBT, -500.0, "سالب") is AccountingResult.Error)

        // Add valid debt
        val d1 = repo.recordTransaction(cid, TransactionType.DEBT, 1500.0, "فاتورة 1")
        assertTrue(d1 is AccountingResult.Success)

        // Prevent duplicate transaction
        val dup = repo.recordTransaction(cid, TransactionType.DEBT, 1500.0, "فاتورة 1")
        assertTrue(dup is AccountingResult.Error)

        // Prevent overpayment by default
        val overpay = repo.recordTransaction(cid, TransactionType.PAYMENT, 2000.0, "دفعة زائدة")
        assertTrue(overpay is AccountingResult.Error)

        // Valid partial payment
        val p1 = repo.recordTransaction(cid, TransactionType.PAYMENT, 600.0, "سند قبض 1")
        assertTrue(p1 is AccountingResult.Success)

        val updated = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        // Balance = Total Debts - Total Payments = 1500 - 600 = 900
        assertEquals(1500.0, updated.totalDebts, 0.01)
        assertEquals(600.0, updated.totalPayments, 0.01)
        assertEquals(900.0, updated.balance, 0.01)

        // Reversing transaction preserves audit trail and updates balance
        val revRes = repo.reverseTransaction((p1 as AccountingResult.Success).data, "خطأ في المبلغ")
        assertTrue(revRes is AccountingResult.Success)
        val afterReverse = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        assertEquals(1500.0, afterReverse.balance, 0.01)
        assertTrue(db.auditLogDao().getAllAuditLogsOnce().any { it.actionType == "REVERSE_TRANSACTION" })
    }

    @Test
    fun `transfers preserve reference number format and prevent duplicate reference`() = runBlocking {
        val cid = (repo.createCustomer("خالد قاسم", "773334445", "ACC-55", 0.0, "") as AccountingResult.Success).data
        repo.recordTransaction(cid, TransactionType.DEBT, 10000.0, "بضاعة")

        val tr1 = repo.addTransfer(
            company = TransferCompany.AL_NAJM,
            referenceNumber = "ت/8000011",
            amount = 4000.0,
            senderName = "خالد قاسم",
            transferDate = System.currentTimeMillis(),
            customerId = cid,
            notes = "حوالة النجم",
        )
        assertTrue(tr1 is AccountingResult.Success)
        val transferId = (tr1 as AccountingResult.Success).data

        // Duplicate reference must be blocked
        val trDup = repo.addTransfer(
            company = TransferCompany.AL_IMTIYAZ_EXPRESS,
            referenceNumber = "ت/8000011",
            amount = 4000.0,
            senderName = "خالد",
            transferDate = System.currentTimeMillis(),
            customerId = cid,
            notes = "",
        )
        assertTrue(trDup is AccountingResult.Error)

        // Confirm transfer as payment
        val confirmRes = repo.confirmTransferAsPayment(transferId, cid, allowOverpayment = false)
        assertTrue(confirmRes is AccountingResult.Success)

        val transferEntity = db.transferDao().getById(transferId)!!
        assertEquals("ت/8000011", transferEntity.referenceNumber)
        assertEquals(TransferStatus.CONFIRMED, transferEntity.status)

        val cust = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        assertEquals(6000.0, cust.balance, 0.01)
    }

    @Test
    fun `sms template renders all 8 placeholders and builds ACTION_SENDTO intent without auto-confirming sent`() = runBlocking {
        val cid = (repo.createCustomer("فهد ناصر", "775556667", "ACC-99", 0.0, "") as AccountingResult.Success).data
        repo.recordTransaction(cid, TransactionType.DEBT, 5000.0, "مشتريات")
        repo.recordTransaction(cid, TransactionType.PAYMENT, 1500.0, "دفعة")

        val cust = db.customerDao().getCustomerWithBalanceOnce(cid)!!
        val settings = AppSettings(storeName = "متجر البركة", contactPhone = "770000111", currencySymbol = "ر.ي")
        val template = "مرحباً {اسم_العميل}، دينك {إجمالي_الديون} ومدفوعاتك {إجمالي_المدفوعات} والرصيد {الرصيد} بتاريخ {التاريخ} لدى {اسم_المتجر} ({رقم_التواصل}) حساب {رقم_الحساب}"
        val rendered = SmsTemplateEngine.renderMessage(template, cust, settings)

        assertTrue(rendered.contains("فهد ناصر"))
        assertTrue(rendered.contains("5,000 ر.ي"))
        assertTrue(rendered.contains("1,500 ر.ي"))
        assertTrue(rendered.contains("3,500 ر.ي"))
        assertTrue(rendered.contains("متجر البركة"))
        assertTrue(rendered.contains("770000111"))
        assertTrue(rendered.contains("ACC-99"))
        assertFalse(rendered.contains("{"))

        val intent = SmsTemplateEngine.buildSmsIntent(cust.phone, rendered)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:775556667", intent.dataString)
        assertEquals(rendered, intent.getStringExtra("sms_body"))

        val logId = repo.logSmsIntentOpened(cust.id, cust.name, cust.phone, rendered, "قالب")
        val log = db.messageLogDao().getById(logId)!!
        assertEquals(MessageStatus.INTENT_OPENED, log.status)
    }

    @Test
    fun `clients and debt room daos store and compute debts and payments locally`() = runBlocking {
        val clientDao = db.clientDao()
        val debtDao = db.debtDao()

        val clientId = clientDao.insertClient(
            Clients(
                name = "شركة النور للتجارة",
                phone = "771234567",
                accountNumber = "CL-200",
            )
        )
        debtDao.insertDebtOrPayment(
            Debt(
                customerId = clientId,
                type = TransactionType.DEBT,
                amount = 8000.0,
                description = "بضاعة آجلة",
            )
        )
        debtDao.insertDebtOrPayment(
            Debt(
                customerId = clientId,
                type = TransactionType.PAYMENT,
                amount = 2500.0,
                description = "دفعة أولى",
            )
        )

        val clientsList = clientDao.observeAllClients().first()
        assertEquals(1, clientsList.size)
        assertEquals("شركة النور للتجارة", clientsList.first().name)

        val totalDebts = debtDao.getTotalDebtsForClient(clientId)
        val totalPayments = debtDao.getTotalPaymentsForClient(clientId)
        assertEquals(8000.0, totalDebts, 0.01)
        assertEquals(2500.0, totalPayments, 0.01)
        assertEquals(5500.0, totalDebts - totalPayments, 0.01)
    }

    @Test
    fun `direct sms manager checks SEND_SMS permission and dispatches Arabic debt alert when granted`() = runBlocking {
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        assertTrue(pkgInfo.requestedPermissions?.contains(Manifest.permission.SEND_SMS) == true)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)

        // Denied by default
        shadowApp.denyPermissions(Manifest.permission.SEND_SMS)
        assertFalse(DirectSmsSender.hasSendSmsPermission(context))
        val deniedResult = DirectSmsSender.sendDebtAlert(
            context = context,
            phone = "777888999",
            messageBody = "تنبيه بسداد الدين",
        )
        assertTrue(deniedResult is SmsDispatchResult.PermissionDenied)

        // Grant permission and verify SmsManager resolution + Arabic alert dispatch + NoTelephonySupport fallback
        shadowApp.grantPermissions(Manifest.permission.SEND_SMS)
        assertTrue(DirectSmsSender.hasSendSmsPermission(context))
        assertNotNull(DirectSmsSender.resolveSmsManager(context))

        val longArabicAlert = "عزيزي العميل فهد ناصر، رصيدكم المستحق لدى متجر البركة هو 3,500 ر.ي. نرجو المبادرة بالسداد في أقرب وقت ممكن وشكراً لتعاونكم."
        var capturedPhone: String? = null
        var capturedBody: String? = null
        val testGateway = object : SmsGateway {
            override fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int {
                capturedPhone = destination
                capturedBody = messageBody
                return 2
            }
        }
        val sentResult = DirectSmsSender.sendDebtAlert(
            context = context,
            phone = "٧٧٧-٨٨٨-٩٩٩",
            messageBody = longArabicAlert,
            logId = 10L,
            gateway = testGateway,
        )
        assertTrue("Expected Dispatched but got: $sentResult", sentResult is SmsDispatchResult.Dispatched)
        assertEquals("777888999", capturedPhone)
        assertEquals(longArabicAlert, capturedBody)

        // When underlying Android ISms binder is absent (e.g. Wi-Fi tablet or JVM), returns NoTelephonySupport cleanly
        val unsupportedResult = DirectSmsSender.sendDebtAlert(
            context = context,
            phone = "777888999",
            messageBody = longArabicAlert,
            logId = 10L,
        )
        assertTrue(unsupportedResult is SmsDispatchResult.NoTelephonySupport || unsupportedResult is SmsDispatchResult.Dispatched)

        val cid = (repo.createCustomer("فهد ناصر", "777888999", "ACC-12", 0.0, "") as AccountingResult.Success).data
        repo.logDirectSmsDispatch(
            customerId = cid,
            customerName = "فهد ناصر",
            phone = "777888999",
            messageBody = longArabicAlert,
            templateName = "إشعار تأخر سداد",
            status = MessageStatus.SENT_VIA_SMS_MANAGER,
        )
        assertEquals(MessageStatus.SENT_VIA_SMS_MANAGER, db.messageLogDao().getAllLogsOnce().first().status)
    }

    @Test
    fun `customers screen displays Arabic list and add debt floating action button`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            val cid = (repo.createCustomer("عبدالله قاسم", "771002003", "CL-77", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(cid, TransactionType.DEBT, 4200.0, "فاتورة آجل")
        }

        val settingsStore = SettingsDataStore(app)
        val backupMgr = BackupManager(db, settingsStore)
        val vm = SmartDebtViewModel(
            application = app,
            repository = repo,
            settingsDataStore = settingsStore,
            backupManager = backupMgr,
            exportManager = ExportManager(),
            inspector = ExternalDbInspector(db, backupMgr),
        )

        var navigatedRoute: String? = null
        composeRule.setContent {
            SmartDebtTheme {
                CustomersScreen(
                    viewModel = vm,
                    onNavigate = { navigatedRoute = it },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCatching { composeRule.onNodeWithText("عبدالله قاسم").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("fab_add_debt").assertIsDisplayed()
        composeRule.onNodeWithText("إضافة دين جديد", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("fab_add_debt").performClick()
        assertEquals(AppRoutes.addDebt(), navigatedRoute)
    }

    @Test
    fun `workmanager schedules periodic check and sends local notification before debt due date`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }

        // Schedule periodic WorkManager background task
        OverdueReminderWorker.schedule(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(OverdueReminderWorker.WORK_NAME)
            .get()
        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)

        // Add customer + reminder due in 12 hours (BEFORE due date)
        val cid = (repo.createCustomer("سعيد المحمدي", "774400112", "ACC-88", 0.0, "") as AccountingResult.Success).data
        repo.recordTransaction(cid, TransactionType.DEBT, 6000.0, "بضاعة آجل")
        val now = System.currentTimeMillis()
        repo.addReminder(
            customerId = cid,
            title = "قسط مستحق قريباً",
            dueDate = now + 12 * 3_600_000L,
            amountDue = 6000.0,
            notes = "تنبيه مسبق",
        )

        val summary = OverdueReminderWorker.runDueDateScan(
            context = context,
            db = db,
            nowMillis = now,
        )
        assertEquals(1, summary.upcomingBeforeDueCount)
        assertEquals(0, summary.newlyOverdueCount)
        assertTrue(summary.upcomingNotificationPosted)
        assertTrue(summary.upcomingCustomerNames.contains("سعيد المحمدي"))

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).allNotifications
        assertTrue(posted.isNotEmpty())
        assertTrue(db.auditLogDao().getAllAuditLogsOnce().any { it.actionType == "WORKMANAGER_DUE_CHECK" })
    }

    @Test
    fun `statistics column chart displays debts vs payments comparison and updates selection on tap`() {
        composeRule.setContent {
            SmartDebtTheme {
                DebtVsPaymentColumnChart(
                    totalDebts = 10000.0,
                    totalPayments = 4000.0,
                    netBalance = 6000.0,
                    currency = "ر.ي",
                )
            }
        }

        composeRule.onNodeWithTag("debt_vs_payment_column_chart").assertIsDisplayed()
        composeRule.onNodeWithText("الديون المستحقة (إجمالي الديون المسجلة)").assertIsDisplayed()
        composeRule.onNodeWithText("10,000 ر.ي").assertIsDisplayed()

        // Tap on Payments chip and verify chart pill switches to received payments (40.0%)
        composeRule.onNodeWithTag("chart_chip_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("المدفوعات المستلمة (إجمالي التحصيل)").assertIsDisplayed()
        composeRule.onNodeWithText("4,000 ر.ي").assertIsDisplayed()
        composeRule.onNodeWithText("النسبة من إجمالي الديون: 40.0%").assertIsDisplayed()
    }

    @Test
    fun `pdf receipt generator builds valid pdf document and receipt dialog displays share save and print actions`() {
        val cid = runBlocking {
            val id = (repo.createCustomer("ناصر التميمي", "772200334", "ACC-501", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(id, TransactionType.DEBT, 10000.0, "فاتورة بضاعة")
            repo.recordTransaction(id, TransactionType.PAYMENT, 4500.0, "سند قبض نقدي")
            id
        }
        val customer = runBlocking { db.customerDao().getCustomerWithBalanceOnce(cid)!! }
        val paymentEntity = runBlocking {
            db.transactionDao().getAllTransactionsOnce().first { it.customerId == cid && it.type == TransactionType.PAYMENT }
        }
        val txWithCust = TransactionWithCustomer(
            id = paymentEntity.id,
            customerId = cid,
            customerName = customer.name,
            customerPhone = customer.phone,
            type = paymentEntity.type,
            amount = paymentEntity.amount,
            description = paymentEntity.description,
            referenceNumber = paymentEntity.referenceNumber,
            sourceApp = paymentEntity.sourceApp,
            transactionDate = paymentEntity.transactionDate,
            createdAt = paymentEntity.createdAt,
            isReversed = paymentEntity.isReversed,
            reversedAt = paymentEntity.reversedAt,
            reversalReason = paymentEntity.reversalReason,
        )
        val settings = AppSettings(storeName = "متجر الأمل", contactPhone = "770001122", currencySymbol = "ر.ي")

        val receiptResult = PaymentReceiptPdfGenerator.createTempReceiptFile(context, txWithCust, customer, settings)
        assertTrue(
            "PDF creation failed: ${receiptResult.exceptionOrNull()?.stackTraceToString()}",
            receiptResult.isSuccess,
        )
        val pdfFile = receiptResult.getOrThrow()
        assertTrue(pdfFile.exists() && pdfFile.length() > 300L)
        val pdfText = String(pdfFile.readBytes(), Charsets.US_ASCII)
        assertTrue(pdfText.startsWith("%PDF-"))
        assertTrue(pdfText.trimEnd().endsWith("%%EOF"))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        assertTrue(uri.toString().contains("receipts"))
        pdfFile.delete()

        var shared = false
        var printed = false
        composeRule.setContent {
            SmartDebtTheme {
                PaymentReceiptDialog(
                    transaction = txWithCust,
                    customer = customer,
                    currency = settings.currencySymbol,
                    onSharePdf = { shared = true },
                    onSavePdfUri = {},
                    onPrintPdf = { printed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("إيصال سداد رسمي (PDF)").assertIsDisplayed()
        composeRule.onNodeWithText("رقم السند: RCP-${txWithCust.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("share_receipt_pdf_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("save_receipt_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("print_receipt_pdf_button").assertIsDisplayed().performClick()
        assertTrue(shared)
        assertTrue(printed)
    }

    @Test
    fun `biometric helper settings toggle and lock screen protect application access`() = runBlocking {
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        assertTrue(pkgInfo.requestedPermissions?.contains(Manifest.permission.USE_BIOMETRIC) == true)

        val availability = BiometricAuthHelper.checkAvailability(context)
        assertTrue(availability.messageAr.isNotBlank())
        val promptInfo = BiometricAuthHelper.buildPromptInfo(title = "قفل الحماية", subtitle = "التحقق")
        assertEquals("قفل الحماية", promptInfo.title.toString())

        val app = ApplicationProvider.getApplicationContext<Application>()
        val settingsStore = SettingsDataStore(app)
        val backupMgr = BackupManager(db, settingsStore)
        val vm = SmartDebtViewModel(
            application = app,
            repository = repo,
            settingsDataStore = settingsStore,
            backupManager = backupMgr,
            exportManager = ExportManager(),
            inspector = ExternalDbInspector(db, backupMgr),
        )

        settingsStore.updateBiometricLock(true)
        assertTrue(settingsStore.getSettingsOnce().biometricLockEnabled)
        vm.lockSession()
        assertFalse(vm.isSessionUnlocked.value)
        vm.unlockSession()
        assertTrue(vm.isSessionUnlocked.value)
        settingsStore.updateBiometricLock(false)

        var unlockedTriggered = false
        composeRule.setContent {
            SmartDebtTheme {
                BiometricLockScreen(
                    storeName = "متجري المحمي",
                    errorMessage = "يرجى المحاولة مرة أخرى",
                    onUnlockClick = { unlockedTriggered = true },
                )
            }
        }
        composeRule.onNodeWithTag("biometric_lock_screen").assertIsDisplayed()
        composeRule.onNodeWithText("التطبيق محمي بالبصمة / الوجه").assertIsDisplayed()
        composeRule.onNodeWithText("يرجى المحاولة مرة أخرى").assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_unlock_button").assertIsDisplayed().performClick()
        assertTrue(unlockedTriggered)
    }

    @Test
    fun `customers screen advanced search bar filters by status and due date range`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val now = System.currentTimeMillis()
        runBlocking {
            val debtorId = (repo.createCustomer("أحمد المديون", "771001001", "ACC-D1", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(debtorId, TransactionType.DEBT, 5000.0, "بضاعة آجل", transactionDate = now)
            repo.addReminder(debtorId, "قسط قريب", dueDate = now + 2L * 86_400_000L, amountDue = 5000.0, notes = "")

            val settledId = (repo.createCustomer("سالم المسدد", "772002002", "ACC-S1", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(settledId, TransactionType.DEBT, 3000.0, "بضاعة", transactionDate = now)
            repo.recordTransaction(settledId, TransactionType.PAYMENT, 3000.0, "سداد كامل", transactionDate = now)
        }

        val settingsStore = SettingsDataStore(app)
        val backupMgr = BackupManager(db, settingsStore)
        val vm = SmartDebtViewModel(
            application = app,
            repository = repo,
            settingsDataStore = settingsStore,
            backupManager = backupMgr,
            exportManager = ExportManager(),
            inspector = ExternalDbInspector(db, backupMgr),
        )

        composeRule.setContent {
            SmartDebtTheme {
                CustomersScreen(viewModel = vm, onNavigate = {})
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithText("أحمد المديون").assertIsDisplayed()
                composeRule.onNodeWithText("سالم المسدد").assertIsDisplayed()
            }.isSuccess && vm.reminders.value.isNotEmpty()
        }

        // Expand advanced filters & select due date range NEXT_7_DAYS + DEBTORS status
        composeRule.onNodeWithTag("advanced_filter_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("customer_status_DEBTORS").performClick()
        composeRule.onNodeWithTag("date_filter_NEXT_7_DAYS").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("أحمد المديون").assertIsDisplayed()
        composeRule.onNodeWithText("سالم المسدد").assertDoesNotExist()

        // Reset restores settled customer as well
        composeRule.onNodeWithTag("reset_advanced_filters_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("سالم المسدد").assertIsDisplayed()
    }

    @Test
    fun `transactions log screen advanced search bar filters by customer name status and date range`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val now = System.currentTimeMillis()
        runBlocking {
            val c1 = (repo.createCustomer("خالد النجار", "773003003", "ACC-T1", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(c1, TransactionType.DEBT, 7000.0, "توريد خشب", transactionDate = now)

            val c2 = (repo.createCustomer("يوسف الحداد", "774004004", "ACC-T2", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(c2, TransactionType.DEBT, 4000.0, "حديد تسليح", transactionDate = now - 15L * 86_400_000L)
            val payId = (repo.recordTransaction(c2, TransactionType.PAYMENT, 1000.0, "دفعة ملغاة", transactionDate = now - 15L * 86_400_000L) as AccountingResult.Success).data
            repo.reverseTransaction(payId, "سند مكرر")
        }

        val settingsStore = SettingsDataStore(app)
        val backupMgr = BackupManager(db, settingsStore)
        val vm = SmartDebtViewModel(
            application = app,
            repository = repo,
            settingsDataStore = settingsStore,
            backupManager = backupMgr,
            exportManager = ExportManager(),
            inspector = ExternalDbInspector(db, backupMgr),
        )

        composeRule.setContent {
            SmartDebtTheme {
                TransactionsLogScreen(viewModel = vm, onBack = {})
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCatching { composeRule.onNodeWithText("توريد خشب").assertIsDisplayed() }.isSuccess
        }

        // Open advanced filter panel -> filter by customer name + TODAY date range
        composeRule.onNodeWithTag("advanced_filter_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("advanced_customer_name_input").performTextInput("خالد")
        composeRule.onNodeWithTag("date_filter_TODAY").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("توريد خشب").assertIsDisplayed()
        composeRule.onNodeWithText("حديد تسليح").assertDoesNotExist()

        // Reset filters and verify reversed transaction status filter
        composeRule.onNodeWithTag("reset_advanced_filters_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tx_status_REVERSED").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("دفعة ملغاة").assertIsDisplayed()
        composeRule.onNodeWithText("توريد خشب").assertDoesNotExist()
    }
}
