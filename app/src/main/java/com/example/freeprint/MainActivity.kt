package com.example.freeprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.freeprint.ui.screens.*
import com.example.freeprint.ui.theme.FreePrintTheme
import kotlinx.coroutines.launch

private data class DrawerItem(val route: String, val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreePrintTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // --- NEW: Add "Print Queue" to the drawer items ---
                val drawerItems = listOf(
                    DrawerItem("file_picker", "My Files", Icons.Default.Home),
                    DrawerItem("print_queue", "Print Queue", Icons.Default.List), // New Item
                    DrawerItem("printers", "Printers", Icons.Default.Print),
                    DrawerItem("drivers", "Drivers", Icons.Default.Description),
                    DrawerItem("settings", "Settings", Icons.Default.Settings)
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val canPop = navController.previousBackStackEntry != null
                val showBackButton = canPop && drawerItems.none { it.route == currentRoute }

                val printerViewModel: PrinterViewModel = viewModel()
                val filePickerViewModel: FilePickerViewModel = viewModel()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text("Navigation", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("Select a destination", style = MaterialTheme.typography.bodySmall)
                            }
                            Divider()
                            Spacer(Modifier.height(8.dp))

                            drawerItems.forEach { item ->
                                NavigationDrawerItem(
                                    icon = { Icon(item.icon, contentDescription = item.title) },
                                    label = { Text(item.title) },
                                    selected = item.route == currentRoute,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        if (item.route != currentRoute) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("FreePrint") },
                                navigationIcon = {
                                    if (showBackButton) {
                                        IconButton(onClick = { navController.navigateUp() }) {
                                            Icon(Icons.Default.ArrowBack, "Go Back")
                                        }
                                    } else {
                                        IconButton(onClick = { scope.launch { drawerState.apply { if (isClosed) open() else close() } } }) {
                                            Icon(Icons.Default.Menu, "Open Navigation Menu")
                                        }
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "file_picker",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("file_picker") {
                                FilePickerScreen(
                                    navController = navController,
                                    fileViewModel = filePickerViewModel
                                )
                            }

                            // --- NEW: Add the route for the Print Queue screen ---
                            composable("print_queue") {
                                PrintQueueScreen(printerViewModel = printerViewModel)
                            }

                            composable("printers") {
                                PrinterListScreen(
                                    navController = navController,
                                    printerViewModel = printerViewModel
                                )
                            }

                            composable("settings") {
                                Text(text = "App Settings Screen")
                            }

                            composable("drivers") {
                                DriversScreen(printerViewModel = printerViewModel)
                            }

                            composable(
                                route = "printer_settings/{printerIp}",
                                arguments = listOf(navArgument("printerIp") { type = NavType.StringType })
                            ) { backStackEntry ->
                                PrinterSettings(
                                    navController = navController,
                                    printerIp = backStackEntry.arguments?.getString("printerIp"),
                                    printerViewModel = printerViewModel
                                )
                            }

                            composable(
                                route = "print_settings/{filePath}",
                                arguments = listOf(navArgument("filePath") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedPath = backStackEntry.arguments?.getString("filePath")
                                PrintSettingsScreen(
                                    navController = navController,
                                    filePath = encodedPath,
                                    fileViewModel = filePickerViewModel,
                                    printerViewModel = printerViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
