package net.matsudamper.folderviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.Settings
import net.matsudamper.folderviewer.navigation.SmbAdd
import net.matsudamper.folderviewer.navigation.StorageTypeSelection
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.home.HomeScreen
import net.matsudamper.folderviewer.ui.settings.SettingsScreen
import net.matsudamper.folderviewer.ui.storage.SmbAddScreen
import net.matsudamper.folderviewer.ui.storage.StorageTypeSelectionScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storageRepository = StorageRepository(applicationContext)

        setContent {
            FolderViewerTheme {
                AppContent(storageRepository)
            }
        }
    }
}

@Composable
fun AppContent(
    storageRepository: StorageRepository,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Home> {
                HomeScreen(
                    storageRepository = storageRepository,
                    onNavigateToSettings = {
                        navController.navigate(Settings)
                    },
                    onAddStorageClick = {
                        navController.navigate(StorageTypeSelection)
                    },
                )
            }
            composable<Settings> {
                SettingsScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable<StorageTypeSelection> {
                StorageTypeSelectionScreen(
                    onSmbClick = {
                        navController.navigate(SmbAdd)
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable<SmbAdd> {
                SmbAddScreen(
                    storageRepository = storageRepository,
                    navController = navController,
                    onBack = {
                        navController.popBackStack()
                    },
                    onSaveSuccess = {
                        navController.popBackStack(Home, inclusive = false)
                    },
                )
            }
        }
    }
}
