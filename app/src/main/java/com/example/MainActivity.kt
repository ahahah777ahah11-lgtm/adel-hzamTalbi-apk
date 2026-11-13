package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.security.BiometricAuthHelper
import com.example.security.findFragmentActivity
import com.example.ui.SmartDebtViewModel
import com.example.ui.components.BiometricLockScreen
import com.example.ui.navigation.AppRoutes
import com.example.ui.screens.AccountStatementScreen
import com.example.ui.screens.AddDebtScreen
import com.example.ui.screens.AddEditCustomerScreen
import com.example.ui.screens.AddPaymentScreen
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExportDataScreen
import com.example.ui.screens.ImportDataScreen
import com.example.ui.screens.LocalAssistantScreen
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmsCenterScreen
import com.example.ui.screens.SmsTemplatesScreen
import com.example.ui.screens.TransactionsLogScreen
import com.example.ui.screens.TransfersScreen
import com.example.ui.theme.LocalFinancialColors
import com.example.ui.theme.SmartDebtTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SmartDebtViewModel = viewModel(
                factory = SmartDebtViewModel.Factory(application)
            )
            val settings by vm.settings.collectAsStateWithLifecycle()
            val isUnlocked by vm.isSessionUnlocked.collectAsStateWithLifecycle()
            var biometricError by remember { mutableStateOf<String?>(null) }

            val triggerBiometricPrompt = {
                BiometricAuthHelper.authenticate(
                    activity = this,
                    onSuccess = {
                        biometricError = null
                        vm.unlockSession()
                    },
                    onError = { err ->
                        biometricError = err
                    },
                )
            }

            SmartDebtTheme(
                themeMode = settings.themeMode,
                colorPalette = settings.colorPalette,
            ) {
                if (settings.biometricLockEnabled && !isUnlocked) {
                    LaunchedEffect(Unit) {
                        triggerBiometricPrompt()
                    }
                    BiometricLockScreen(
                        storeName = settings.storeName,
                        errorMessage = biometricError,
                        onUnlockClick = { triggerBiometricPrompt() },
                    )
                } else {
                    SmartDebtAppRoot(viewModel = vm)
                }
            }
        }
    }
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

@Composable
fun SmartDebtAppRoot(viewModel: SmartDebtViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topDestinations = remember {
        listOf(
            TopNavItem(AppRoutes.DASHBOARD, "الرئيسية", Icons.Default.Home, "nav_dashboard"),
            TopNavItem(AppRoutes.CUSTOMERS, "العملاء", Icons.Default.People, "nav_customers"),
            TopNavItem(AppRoutes.TRANSFERS, "الحوالات", Icons.Default.CurrencyExchange, "nav_transfers"),
            TopNavItem(AppRoutes.SMS_CENTER, "SMS", Icons.Default.Sms, "nav_sms"),
            TopNavItem(AppRoutes.MORE_HUB, "المزيد", Icons.Default.Apps, "nav_more"),
        )
    }

    val topRoutesSet = remember {
        setOf(AppRoutes.DASHBOARD, AppRoutes.CUSTOMERS, AppRoutes.TRANSFERS, AppRoutes.SMS_CENTER, AppRoutes.MORE_HUB)
    }
    val showTopLevelNav = currentRoute in topRoutesSet

    LaunchedEffect(Unit) {
        viewModel.feedback.collect { fb ->
            snackbarHostState.showSnackbar(fb.message)
        }
    }

    fun navigateTop(route: String) {
        val target = if (route == AppRoutes.SMS_CENTER) AppRoutes.smsCenter() else route
        navController.navigate(target) {
            popUpTo(AppRoutes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail {
                        topDestinations.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTop(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.DASHBOARD,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(AppRoutes.DASHBOARD) {
                        DashboardScreen(viewModel = viewModel, onNavigate = { navController.navigate(it) })
                    }
                    composable(AppRoutes.CUSTOMERS) {
                        CustomersScreen(viewModel = viewModel, onNavigate = { navController.navigate(it) })
                    }
                    composable(
                        route = AppRoutes.ADD_EDIT_CUSTOMER,
                        arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
                        AddEditCustomerScreen(
                            customerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                navController.popBackStack()
                                if (cid == null) {
                                    navController.navigate(AppRoutes.customerDetail(savedId))
                                }
                            },
                        )
                    }
                    composable(
                        route = AppRoutes.CUSTOMER_DETAIL,
                        arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
                    ) { entry ->
                        val cid = entry.arguments?.getLong("customerId") ?: 0L
                        CustomerDetailScreen(
                            customerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { navController.navigate(it) },
                        )
                    }
                    composable(
                        route = AppRoutes.ACCOUNT_STATEMENT,
                        arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
                    ) { entry ->
                        val cid = entry.arguments?.getLong("customerId") ?: 0L
                        AccountStatementScreen(
                            customerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = AppRoutes.ADD_DEBT,
                        arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
                        AddDebtScreen(
                            preselectedCustomerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = AppRoutes.ADD_PAYMENT,
                        arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
                        AddPaymentScreen(
                            preselectedCustomerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                    composable(AppRoutes.TRANSACTIONS_LOG) {
                        TransactionsLogScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.REMINDERS) {
                        RemindersScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { navController.navigate(it) },
                        )
                    }
                    composable(AppRoutes.TRANSFERS) {
                        TransfersScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = AppRoutes.SMS_CENTER,
                        arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val cid = entry.arguments?.getLong("customerId")?.takeIf { it > 0L }
                        SmsCenterScreen(
                            preselectedCustomerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { navController.navigate(it) },
                        )
                    }
                    composable(AppRoutes.SMS_TEMPLATES) {
                        SmsTemplatesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.REPORTS) {
                        ReportsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { navController.navigate(it) },
                        )
                    }
                    composable(AppRoutes.IMPORT_DATA) {
                        ImportDataScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.EXPORT_DATA) {
                        ExportDataScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.BACKUP_RESTORE) {
                        BackupRestoreScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.LOCAL_ASSISTANT) {
                        LocalAssistantScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { navController.navigate(it) },
                        )
                    }
                    composable(AppRoutes.MORE_HUB) {
                        MoreHubScreen(onNavigate = { navController.navigate(it) })
                    }
                }
            }
        }
    }
}
