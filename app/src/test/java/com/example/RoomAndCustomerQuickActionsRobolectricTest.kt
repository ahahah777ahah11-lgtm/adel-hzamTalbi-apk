package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importexport.BackupManager
import com.example.data.importexport.ExportManager
import com.example.data.importexport.ExternalDbInspector
import com.example.data.local.AccountingRepository
import com.example.data.local.AccountingResult
import com.example.data.local.SmartDebtDatabase
import com.example.data.local.TransactionType
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.sms.DirectSmsSender
import com.example.data.sms.SmsDispatchResult
import com.example.data.sms.SmsGateway
import com.example.data.sms.SmsTemplateEngine
import com.example.ui.SmartDebtViewModel
import com.example.ui.navigation.AppRoutes
import com.example.ui.screens.CustomersScreen
import com.example.ui.theme.SmartDebtTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class RoomAndCustomerQuickActionsRobolectricTest {

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
    fun `debts and payments room tables stay synchronized with accounting operations and reversals`() = runBlocking {
        val cid = (repo.createCustomer(
            name = "عماد المنصور",
            phone = "770123123",
            accountNumber = "CUST-50",
            creditLimit = 25000.0,
            notes = "تجارة جملة",
            openingBalance = 2000.0,
        ) as AccountingResult.Success).data

        val d1 = repo.recordTransaction(cid, TransactionType.DEBT, 3500.0, "فاتورة بضاعة")
        assertTrue(d1 is AccountingResult.Success)
        val p1 = repo.recordTransaction(cid, TransactionType.PAYMENT, 1500.0, "سند قبض")
        assertTrue(p1 is AccountingResult.Success)

        val debts = db.debtsDao().observeDebtsForCustomer(cid).first()
        val payments = db.paymentsDao().observePaymentsForCustomer(cid).first()

        assertEquals(2, debts.size)
        assertTrue(debts.any { it.isOpeningBalance && it.amount == 2000.0 })
        assertTrue(debts.any { !it.isOpeningBalance && it.amount == 3500.0 })
        assertEquals(1, payments.size)
        assertEquals(1500.0, payments.first().amount, 0.01)

        assertEquals(5500.0, db.debtsDao().getTotalDebtsForCustomer(cid), 0.01)
        assertEquals(1500.0, db.paymentsDao().getTotalPaymentsForCustomer(cid), 0.01)

        // Reversing payment marks the payments table entry reversed and restores remaining balance
        val payTxId = (p1 as AccountingResult.Success).data
        assertTrue(repo.reverseTransaction(payTxId, "إرجاع سند") is AccountingResult.Success)
        assertEquals(0.0, db.paymentsDao().getTotalPaymentsForCustomer(cid), 0.01)
        assertTrue(db.paymentsDao().getAllPaymentsOnce().first { it.linkedTransactionId == payTxId }.isReversed)
    }

    @Test
    fun `room migration 1 to 2 creates debts and payments tables and backfills v1 transactions`() = runBlocking {
        val dbFile = File(context.cacheDir, "migration_test_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Build Room v1 schema manually to verify MIGRATION_1_2 on upgrade
            rawDb.execSQL(
                """
                CREATE TABLE `customers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `account_number` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `credit_limit` REAL NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `is_archived` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX `index_customers_name` ON `customers` (`name`)")
            rawDb.execSQL("CREATE INDEX `index_customers_phone` ON `customers` (`phone`)")
            rawDb.execSQL("CREATE INDEX `index_customers_account_number` ON `customers` (`account_number`)")

            rawDb.execSQL(
                """
                CREATE TABLE `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `customer_id` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `description` TEXT NOT NULL,
                    `reference_number` TEXT,
                    `source_app` TEXT,
                    `transaction_date` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `is_reversed` INTEGER NOT NULL,
                    `reversed_at` INTEGER,
                    `reversal_reason` TEXT,
                    FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX `index_transactions_customer_id` ON `transactions` (`customer_id`)")
            rawDb.execSQL("CREATE INDEX `index_transactions_transaction_date` ON `transactions` (`transaction_date`)")
            rawDb.execSQL("CREATE INDEX `index_transactions_reference_number` ON `transactions` (`reference_number`)")

            rawDb.execSQL(
                """
                CREATE TABLE `reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `customer_id` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `due_date` INTEGER NOT NULL,
                    `amount_due` REAL NOT NULL,
                    `status` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX `index_reminders_customer_id` ON `reminders` (`customer_id`)")
            rawDb.execSQL("CREATE INDEX `index_reminders_due_date` ON `reminders` (`due_date`)")

            rawDb.execSQL(
                """
                CREATE TABLE `transfers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `company` TEXT NOT NULL,
                    `reference_number` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `sender_name` TEXT NOT NULL,
                    `transfer_date` INTEGER NOT NULL,
                    `customer_id` INTEGER,
                    `status` TEXT NOT NULL,
                    `linked_transaction_id` INTEGER,
                    `notes` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE UNIQUE INDEX `index_transfers_reference_number` ON `transfers` (`reference_number`)")
            rawDb.execSQL("CREATE INDEX `index_transfers_customer_id` ON `transfers` (`customer_id`)")

            rawDb.execSQL(
                """
                CREATE TABLE `message_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `customer_id` INTEGER NOT NULL,
                    `customer_name` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `message_body` TEXT NOT NULL,
                    `template_name` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX `index_message_logs_customer_id` ON `message_logs` (`customer_id`)")
            rawDb.execSQL("CREATE INDEX `index_message_logs_created_at` ON `message_logs` (`created_at`)")

            rawDb.execSQL(
                """
                CREATE TABLE `sms_templates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `is_default` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            rawDb.execSQL(
                """
                CREATE TABLE `audit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `action_type` TEXT NOT NULL,
                    `entity_type` TEXT NOT NULL,
                    `entity_id` INTEGER,
                    `summary` TEXT NOT NULL,
                    `details` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX `index_audit_logs_created_at` ON `audit_logs` (`created_at`)")

            // Seed v1 records
            val now = 1_700_000_000_000L
            rawDb.execSQL(
                "INSERT INTO customers (id, name, phone, account_number, notes, credit_limit, created_at, updated_at, is_archived) VALUES (1, 'ناصر السالمي', '775001002', 'ACC-V1', '', 0.0, $now, $now, 0)"
            )
            rawDb.execSQL(
                "INSERT INTO transactions (id, customer_id, type, amount, description, reference_number, source_app, transaction_date, created_at, is_reversed, reversed_at, reversal_reason) VALUES (10, 1, 'OPENING_BALANCE', 3000.0, 'رصيد مرحل', NULL, NULL, $now, $now, 0, NULL, NULL)"
            )
            rawDb.execSQL(
                "INSERT INTO transactions (id, customer_id, type, amount, description, reference_number, source_app, transaction_date, created_at, is_reversed, reversed_at, reversal_reason) VALUES (11, 1, 'DEBT', 4500.0, 'بضاعة قديمة', NULL, NULL, $now, $now, 0, NULL, NULL)"
            )
            rawDb.execSQL(
                "INSERT INTO transactions (id, customer_id, type, amount, description, reference_number, source_app, transaction_date, created_at, is_reversed, reversed_at, reversal_reason) VALUES (12, 1, 'PAYMENT', 2500.0, 'دفعة نقدية', NULL, NULL, $now, $now, 0, NULL, NULL)"
            )
            rawDb.version = 1
        } finally {
            rawDb.close()
        }

        val migratedDb = Room.databaseBuilder(context, SmartDebtDatabase::class.java, dbFile.absolutePath)
            .addMigrations(SmartDebtDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(7500.0, migratedDb.debtsDao().getTotalDebtsForCustomer(1L), 0.01)
            assertEquals(2500.0, migratedDb.paymentsDao().getTotalPaymentsForCustomer(1L), 0.01)
            val customerBalance = migratedDb.customerDao().getCustomerWithBalanceOnce(1L)!!
            assertEquals(5000.0, customerBalance.balance, 0.01)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `customers lazycolumn item shows quick add debt and record payment buttons with working navigation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cid = runBlocking {
            val id = (repo.createCustomer("طارق الحميري", "771223344", "Q-10", 0.0, "") as AccountingResult.Success).data
            repo.recordTransaction(id, TransactionType.DEBT, 4000.0, "فاتورة")
            id
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

        val visitedRoutes = mutableListOf<String>()
        composeRule.setContent {
            SmartDebtTheme {
                CustomersScreen(
                    viewModel = vm,
                    onNavigate = { visitedRoutes.add(it) },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCatching { composeRule.onNodeWithText("طارق الحميري").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("customer_quick_debt_$cid").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("customer_quick_payment_$cid").assertIsDisplayed().performClick()

        assertTrue(visitedRoutes.contains(AppRoutes.addDebt(cid)))
        assertTrue(visitedRoutes.contains(AppRoutes.addPayment(cid)))
    }
}

/**
 * Multi-version Android SDK verification across Android 10 (API 29), Android 13 (API 33),
 * and Android 14 (API 34) covering Room Database, External SQLite Import, and SMS Messaging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 34])
class MultiAndroidSdkVerificationTest {

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
    fun `database sqlite import and sms messaging work across android 10 13 and 14`() = runBlocking {
        // 1. Prepare external SQLite DB file mimicking an accounting export
        val externalDbFile = File(context.cacheDir, "external_accounting_${System.nanoTime()}.db")
        if (externalDbFile.exists()) externalDbFile.delete()
        val extSql = SQLiteDatabase.openOrCreateDatabase(externalDbFile, null)
        try {
            extSql.execSQL(
                "CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, balance REAL)"
            )
            extSql.execSQL(
                "INSERT INTO customers (id, name, phone, balance) VALUES (1, 'مؤسسة الفجر', '778899001', 9200.0)"
            )
        } finally {
            extSql.close()
        }

        val settingsStore = SettingsDataStore(context)
        val backupMgr = BackupManager(db, settingsStore)
        val inspector = ExternalDbInspector(db, backupMgr)

        // 2. Inspect and import external SQLite DB into Room
        val inspected = inspector.inspectRawFile(externalDbFile).getOrThrow()
        val readyInspection = inspector.rebuildPreview(
            inspection = inspected,
            tableName = inspected.selectedTableName,
            mapping = inspected.mapping,
        )
        val report = inspector.executeImport(context = null, inspection = readyInspection, createSafetyBackup = false)
        externalDbFile.delete()

        assertEquals(1, report.importedCustomersCount)
        assertTrue(report.importedTransactionsCount >= 1)

        val importedCustomer = db.customerDao().observeCustomersWithBalance().first().first { it.name == "مؤسسة الفجر" }
        assertEquals(9200.0, importedCustomer.balance, 0.01)
        assertEquals(9200.0, db.debtsDao().getTotalDebtsForCustomer(importedCustomer.id), 0.01)

        // 3. Record payment and verify balance + payments table
        val payRes = repo.recordTransaction(importedCustomer.id, TransactionType.PAYMENT, 2200.0, "دفعة عبر التحويل")
        assertTrue(payRes is AccountingResult.Success)
        assertEquals(7000.0, db.customerDao().getCustomerWithBalanceOnce(importedCustomer.id)!!.balance, 0.01)
        assertEquals(2200.0, db.paymentsDao().getTotalPaymentsForCustomer(importedCustomer.id), 0.01)

        // 4. Verify SMS template rendering + SEND_SMS permission gating + dispatch on this Android SDK level
        val settings = AppSettings(storeName = "متجر النجاح", contactPhone = "770011223", currencySymbol = "ر.ي")
        val smsBody = SmsTemplateEngine.renderMessage(
            "مرحباً {اسم_العميل}، رصيدكم المتبقي لدى {اسم_المتجر} هو {الرصيد}",
            db.customerDao().getCustomerWithBalanceOnce(importedCustomer.id)!!,
            settings,
        )
        assertTrue(smsBody.contains("مؤسسة الفجر"))
        assertTrue(smsBody.contains("7,000 ر.ي"))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.denyPermissions(Manifest.permission.SEND_SMS)
        assertFalse(DirectSmsSender.hasSendSmsPermission(context))

        shadowApp.grantPermissions(Manifest.permission.SEND_SMS)
        assertTrue(DirectSmsSender.hasSendSmsPermission(context))

        var deliveredPhone: String? = null
        val gateway = object : SmsGateway {
            override fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int {
                deliveredPhone = destination
                return 1
            }
        }
        val dispatchResult = DirectSmsSender.sendDebtAlert(
            context = context,
            phone = importedCustomer.phone,
            messageBody = smsBody,
            logId = 1L,
            gateway = gateway,
        )
        assertTrue(dispatchResult is SmsDispatchResult.Dispatched)
        assertEquals("778899001", deliveredPhone)
    }
}
