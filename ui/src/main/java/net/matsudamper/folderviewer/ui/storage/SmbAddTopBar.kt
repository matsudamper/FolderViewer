package net.matsudamper.folderviewer.ui.storage

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmbAddTopBar(
    isEditMode: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = {
            Text(
                if (isEditMode) "Edit SMB Storage" else "Add SMB Storage",
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                )
            }
        },
    )
}
