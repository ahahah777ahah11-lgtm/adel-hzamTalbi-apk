package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.BiometricAuthHelper
import com.example.security.findFragmentActivity
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.ArabicTopBar
import com.example.ui.components.FinancialCard
import com.example.ui.navigation.AppRoutes
import com.example.ui.theme.AppColorPalette
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalFinancialColors
import com.example.util.FormatUtils

// 17. الإعدادات والمظهر
@Composable
fun SettingsScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val financial = LocalFinancialColors.current
    val context = LocalContext.current
    val biometricAvailability = remember(context) { BiometricAuthHelper.checkAvailability(context) }

    var storeName by remember(settings.storeName) { mutableStateOf(settings.storeName) }
    var contactPhone by remember(settings.contactPhone) { mutableStateOf(settings.contactPhone) }
    var currencySymbol by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var allowOverpayment by remember(settings.allowOverpayment) { mutableStateOf(settings.allowOverpayment) }
    var duplicateWindow by remember(settings.duplicateWindowMinutes) { mutableStateOf(settings.duplicateWindowMinutes.toString()) }
    var overdueDays by remember(settings.overdueDaysThreshold) { mutableStateOf(settings.overdueDaysThreshold.toString()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setWorkManagerReminders(true)
        } else {
            viewModel.emitFeedback("تم تفعيل الفحص في الخلفية بدون إشعارات نظام.", isError = false)
            viewModel.setWorkManagerReminders(true)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(title = "الإعدادات والمظهر", onBack = onBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FinancialCard {
                Text("بيانات المتجر (تظهر في رسائل SMS)", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("اسم المتجر / النشاط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("رقم التواصل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currencySymbol,
                    onValueChange = { currencySymbol = it },
                    label = { Text("رمز العملة (مثال: ر.ي، ر.س، $)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.updateStoreInfo(storeName, contactPhone, currencySymbol) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("حفظ بيانات المتجر")
                }
            }

            FinancialCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("المظهر والألوان (RTL العربية)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("وضع العرض:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateTheme(mode, settings.colorPalette) },
                            label = { Text(mode.titleAr) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("لوحة الألوان:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppColorPalette.entries.forEach { pal ->
                        FilterChip(
                            selected = settings.colorPalette == pal,
                            onClick = { viewModel.updateTheme(settings.themeMode, pal) },
                            label = { Text(pal.titleAr) },
                            modifier = Modifier.testTag("palette_${pal.name}"),
                        )
                    }
                }
            }

            FinancialCard {
                Text("القواعد المحاسبية وحماية الديون", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("السماح بالدفع الزائد عن الرصيد")
                        Text(
                            "مغلق افتراضياً لمنع أخطاء التحصيل الزائد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allowOverpayment,
                        onCheckedChange = { allowOverpayment = it },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duplicateWindow,
                    onValueChange = { duplicateWindow = it.filter { ch -> ch.isDigit() } },
                    label = { Text("نافذة منع العمليات المكررة (بالدقائق)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = overdueDays,
                    onValueChange = { overdueDays = it.filter { ch -> ch.isDigit() } },
                    label = { Text("أيام اعتبار العميل متأخراً") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.updateAccountingRules(
                            allowOverpayment = allowOverpayment,
                            duplicateWindowMinutes = duplicateWindow.toIntOrNull() ?: 5,
                            overdueDays = overdueDays.toIntOrNull() ?: 14,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("حفظ القواعد المحاسبية")
                }
            }

            FinancialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تفعيل فحص التذكيرات الدوري (WorkManager)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "فحص محلي بالخلفية كل 12 ساعة لإرسال إشعار محلي قبل موعد الاستحقاق بـ 48 ساعة وعند التأخر دون إنترنت.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.workManagerRemindersEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setWorkManagerReminders(enable)
                            }
                        },
                        modifier = Modifier.testTag("workmanager_reminders_switch"),
                    )
                }
            }

            FinancialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (biometricAvailability.canAuthenticate) financial.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("حماية التطبيق بالبصمة / الوجه (BiometricPrompt)", fontWeight = FontWeight.SemiBold)
                            Text(
                                biometricAvailability.messageAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = settings.biometricLockEnabled,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                viewModel.setBiometricLockEnabled(false)
                            } else {
                                val activity = context.findFragmentActivity()
                                if (!biometricAvailability.canAuthenticate) {
                                    viewModel.emitFeedback(biometricAvailability.messageAr, isError = true)
                                } else if (activity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        title = "تفعيل القفل البيومتري",
                                        subtitle = "تحقق بالبصمة أو الوجه لتأمين الوصول للتطبيق وبيانات الديون",
                                        onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                        onError = { msg -> viewModel.emitFeedback(msg, isError = true) },
                                    )
                                } else {
                                    viewModel.setBiometricLockEnabled(true)
                                }
                            }
                        },
                        modifier = Modifier.testTag("biometric_lock_switch"),
                    )
                }
                if (settings.biometricLockEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.lockSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_app_now_button"),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("قفل التطبيق الآن (اختبار التحقق بالبصمة)")
                    }
                }
            }

            FinancialCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = financial.success)
                    Spacer(Modifier.width(8.dp))
                    Text("الخصوصية والعمل دون إنترنت", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "• لا يوجد تسجيل دخول أو حسابات سحابية.\n• لا تُرفع أي بيانات عملاء إلى الإنترنت.\n• قاعدة البيانات محلية بالكامل (Room SQLite).\n• إرسال الرسائل يتم عبر تطبيق الرسائل الرسمي فقط بموافقتك.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// 18. المساعد الذكي المحلي
@Composable
fun LocalAssistantScreen(
    viewModel: SmartDebtViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val messages by viewModel.assistantMessages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            ArabicTopBar(
                title = "المساعد الذكي المحلي",
                subtitle = "تحليل فوري دون إنترنت من قاعدة بياناتك",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.getQuickAssistantPrompts()) { prompt ->
                    AssistChip(
                        onClick = { viewModel.askLocalAssistant(prompt) },
                        label = { Text(prompt) },
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (msg.isUser) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Surface(
                            color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (msg.highlightedCustomers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    msg.highlightedCustomers.forEach { c ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable { onNavigate(AppRoutes.customerDetail(c.id)) },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(c.name, fontWeight = FontWeight.Bold)
                                                Text(
                                                    FormatUtils.formatAmount(c.balance, settings.currencySymbol),
                                                    fontWeight = FontWeight.Bold,
                                                    color = LocalFinancialColors.current.danger,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("اسأل عن عميل، المتأخرين، الحوالات...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_query_input"),
                    )
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.askLocalAssistant(input)
                                input = ""
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("assistant_send_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال السؤال", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// مركز الأدوات للوصول السريع لكل الشاشات الـ 18
@Composable
fun MoreHubScreen(
    onNavigate: (String) -> Unit,
) {
    val itemsList = listOf(
        Triple("سجل العمليات والمراجعة", Icons.Default.History, AppRoutes.TRANSACTIONS_LOG),
        Triple("الحوالات الواردة", Icons.Default.CurrencyExchange, AppRoutes.TRANSFERS),
        Triple("التذكيرات والمتأخرات", Icons.Default.NotificationsActive, AppRoutes.REMINDERS),
        Triple("رسائل SMS", Icons.Default.Sms, AppRoutes.smsCenter()),
        Triple("قوالب الرسائل", Icons.Default.Description, AppRoutes.SMS_TEMPLATES),
        Triple("التقارير والرسوم البيانية", Icons.Default.Analytics, AppRoutes.REPORTS),
        Triple("استيراد قواعد البيانات والملفات", Icons.Default.FileUpload, AppRoutes.IMPORT_DATA),
        Triple("تصدير البيانات (Excel / CSV)", Icons.Default.FileDownload, AppRoutes.EXPORT_DATA),
        Triple("النسخ الاحتياطي والاستعادة", Icons.Default.Backup, AppRoutes.BACKUP_RESTORE),
        Triple("المساعد الذكي المحلي", Icons.Default.AutoAwesome, AppRoutes.LOCAL_ASSISTANT),
        Triple("الإعدادات والمظهر", Icons.Default.Settings, AppRoutes.SETTINGS),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "الأدوات والإدارة الشاملة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(itemsList) { (title, icon, route) ->
            FinancialCard(
                modifier = Modifier
                    .clickable { onNavigate(route) }
                    .testTag("hub_item_$route"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
