package net.matsudamper.folderviewer.ui

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import net.matsudamper.folderviewer.ui.home.HomeScreen
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false,
    )

    @Test
    fun homeScreen() {
        paparazzi.snapshot {
            HomeScreen(
                onNavigateToSettings = {},
            )
        }
    }

    @Test
    fun homeScreen_dark() {
        paparazzi.snapshot {
            HomeScreen(
                onNavigateToSettings = {},
            )
        }
    }
}
