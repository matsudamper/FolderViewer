package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DisplayConfigDropDownMenu(
    expanded: Boolean,
    displayConfig: FileBrowserUiState.DisplayConfig,
    onDismissRequest: () -> Unit,
    onDisplayConfigChange: (FileBrowserUiState.DisplayConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        modifier = modifier,
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            val sliderState = rememberSliderState(
                value = when (displayConfig.displaySize) {
                    FileBrowserUiState.DisplaySize.Small -> 0f
                    FileBrowserUiState.DisplaySize.Medium -> 1f
                    FileBrowserUiState.DisplaySize.Large -> 2f
                },
                steps = 1,
                valueRange = 0f..2f,
            )
            val sliderStateSize by remember {
                derivedStateOf {
                    when (sliderState.value) {
                        0f -> FileBrowserUiState.DisplaySize.Small
                        1f -> FileBrowserUiState.DisplaySize.Medium
                        2f -> FileBrowserUiState.DisplaySize.Large
                        else -> error("invalid value ${sliderState.value}")
                    }
                }
            }
            val currentOnDisplayConfigChange by rememberUpdatedState(onDisplayConfigChange)
            LaunchedEffect(sliderStateSize) {
                currentOnDisplayConfigChange(
                    FileBrowserUiState.DisplayConfig(
                        displayMode = displayConfig.displayMode,
                        displaySize = sliderStateSize,
                    ),
                )
            }
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(text = stringResource(R.string.display_mode))
                Row {
                    DisplayModeItem(
                        modifier = Modifier
                            .weight(1f),
                        text = {
                            Text(text = stringResource(R.string.display_mode_list))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_view_list),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDisplayConfigChange(
                                FileBrowserUiState.DisplayConfig(
                                    displayMode = FileBrowserUiState.DisplayMode.List,
                                    displaySize = sliderStateSize,
                                ),
                            )
                        },
                        selected = displayConfig.displayMode == FileBrowserUiState.DisplayMode.List,
                    )
                    VerticalDivider()
                    DisplayModeItem(
                        modifier = Modifier
                            .weight(1f),
                        text = {
                            Text(text = stringResource(R.string.display_mode_grid))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_grid_view),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDisplayConfigChange(
                                FileBrowserUiState.DisplayConfig(
                                    displayMode = FileBrowserUiState.DisplayMode.Grid,
                                    displaySize = sliderStateSize,
                                ),
                            )
                        },
                        selected = displayConfig.displayMode == FileBrowserUiState.DisplayMode.Grid,
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(text = stringResource(R.string.display_size))
                Slider(
                    state = sliderState,
                )
            }
        }
    }
}

@Composable
private fun DisplayModeItem(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalContentColor provides if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            LocalContentColor.current
        },
    ) {
        Column(
            modifier = modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                ) {
                    onClick()
                },
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            icon()
            text()
        }
    }
}
