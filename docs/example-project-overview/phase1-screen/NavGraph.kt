package com.titipin.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.titipin.app.ui.auth.LoginScreen
import com.titipin.app.ui.auth.RegisterScreen
import com.titipin.app.ui.home.HomeScreen
import com.titipin.app.ui.jastip.JastipScreen
import com.titipin.app.ui.preloved.PrelovedScreen
import com.titipin.app.ui.profile.ProfileScreen
import com.titipin.app.ui.theme.Charcoal
import com.titipin.app.ui.theme.Cream
import com.titipin.app.ui.theme.Charcoal30
import com.titipin.app.ui.theme.Terracotta

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME,     "Home",     Icons.Rounded.Home),
    BottomNavItem(Routes.JASTIP,   "Jastip",   Icons.Rounded.Inventory2),
    BottomNavItem(Routes.PRELOVED, "Preloved", Icons.Rounded.Storefront),
    BottomNavItem(Routes.PROFILE,  "Profil",   Icons.Rounded.Person),
)

// Screen yang tidak menampilkan bottom nav
val routesWithoutBottomNav = listOf(Routes.LOGIN, Routes.REGISTER)

@Composable
fun TitipinNavGraph() {
    // navController = "remote control" navigasi
    // Ini pengganti FragmentManager di XML dulu
    val navController = rememberNavController()

    // Observe route yang aktif sekarang
    // Dipakai untuk tau screen mana yang lagi ditampilkan
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Scaffold = struktur dasar halaman (topbar, bottombar, content)
    // Sama konsepnya kayak CoordinatorLayout + BottomNavigationView di XML
    Scaffold(
        bottomBar = {
            // Sembunyikan bottom nav di screen auth
            if (currentRoute !in routesWithoutBottomNav) {
                TitipinBottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            // Ini biar back stack ga numpuk
                            // Mirip kayak popBackStack di Fragment dulu
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        // NavHost = "container" yang menampilkan screen sesuai route
        // Mirip kayak FrameLayout yang jadi tempat Fragment di XML dulu
        NavHost(
            navController  = navController,
            startDestination = Routes.LOGIN, // nanti diganti logic cek token
            modifier = Modifier.padding(innerPadding)
        ) {

            // Daftarin semua screen di sini
            // composable("route") { Screen() } = setContentView per screen

            // ── AUTH ──────────────────────────────────────────
            composable(Routes.LOGIN) {
                LoginScreen(
                    onNavigateToRegister = {
                        navController.navigate(Routes.REGISTER)
                    },
                    onLoginSuccess = {
                        // Navigate ke Home dan hapus Login dari back stack
                        // Biar user ga bisa back ke login setelah masuk
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.REGISTER) {
                RegisterScreen(
                    onNavigateToLogin = {
                        navController.popBackStack()
                    },
                    onRegisterSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            // ── MAIN SCREENS ──────────────────────────────────
            composable(Routes.HOME)     { HomeScreen() }
            composable(Routes.JASTIP)   { JastipScreen() }
            composable(Routes.PRELOVED) { PrelovedScreen() }
            composable(Routes.PROFILE)  { ProfileScreen() }

            // Nanti tambah sub-screens di sini sesuai phase
            // composable(Routes.JASTIP_DETAIL) { ... }
        }
    }
}

@Composable
fun TitipinBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = Charcoal, // warna charcoal dari design system kita
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.route

            NavigationBarItem(
                selected = isSelected,
                onClick  = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector    = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text       = item.label,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = Cream,
                    selectedTextColor   = Cream,
                    unselectedIconColor = Charcoal30,
                    unselectedTextColor = Charcoal30,
                    indicatorColor      = Terracotta // dot aktif warna terracotta
                )
            )
        }
    }
}
