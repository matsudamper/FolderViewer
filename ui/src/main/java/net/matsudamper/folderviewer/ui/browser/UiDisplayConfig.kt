package net.matsudamper.folderviewer.ui.browser

data class UiDisplayConfig(
    val displayMode: DisplayMode,
    val displaySize: DisplaySize,
) {
    enum class DisplayMode {
        List,
        Grid,
    }

    enum class DisplaySize {
        Small,
        Medium,
        Large,
    }
}
