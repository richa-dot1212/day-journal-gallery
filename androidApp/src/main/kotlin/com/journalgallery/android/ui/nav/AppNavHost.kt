package com.journalgallery.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.journalgallery.android.ui.day.DayDetailScreen
import com.journalgallery.android.ui.gallery.MonthGridScreen
import com.journalgallery.android.ui.orb.OrbConnectionEffect
import com.journalgallery.android.ui.pairing.PairingScreen
import com.journalgallery.shared.domain.DayKey

object Routes {
    const val GALLERY = "gallery"
    const val PAIRING = "pairing"
    const val DAY = "day/{year}/{month}/{day}"
    fun day(d: DayKey) = "day/${d.year}/${d.month}/${d.day}"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    // Day-orb: a physical button press on the orb opens that day, reusing the existing route.
    OrbConnectionEffect(onOrbDaySelected = { day ->
        nav.navigate(Routes.day(day)) { launchSingleTop = true }
    })

    NavHost(navController = nav, startDestination = Routes.GALLERY) {
        composable(Routes.GALLERY) {
            MonthGridScreen(
                onOpenDay = { nav.navigate(Routes.day(it)) },
                onOpenPairing = { nav.navigate(Routes.PAIRING) },
            )
        }
        composable(Routes.PAIRING) {
            PairingScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Routes.DAY,
            arguments = listOf(
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
                navArgument("day") { type = NavType.IntType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            val key = DayKey(args.getInt("year"), args.getInt("month"), args.getInt("day"))
            DayDetailScreen(day = key, onBack = { nav.popBackStack() })
        }
    }
}
