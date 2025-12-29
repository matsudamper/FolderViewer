package net.matsudamper.folderviewer.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import net.matsudamper.folderviewer.ui.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false,
    )

    @Test
    fun settingsScreen() {
        paparazzi.snapshot {
            SettingsScreen(
                onBack = {},
            )
        }
    }

    @Test
    fun settingsScreen_dark() {
        paparazzi.snapshot {
            SettingsScreen(
                onBack = {},
            )
        }
    }
}
