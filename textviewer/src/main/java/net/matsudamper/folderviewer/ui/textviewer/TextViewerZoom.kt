package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

internal const val TextViewerMinZoom = 0.5f
internal const val TextViewerMaxZoom = 4f

internal fun Modifier.pinchToZoom(
    scale: MutableFloatState,
    scrollTarget: MutableState<Offset?>,
    verticalScroll: ScrollState,
    horizontalScroll: ScrollState,
): Modifier {
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 2) {
                    val zoomChange = event.calculateZoom()
                    if (zoomChange.isFinite() && zoomChange != 1f) {
                        val current = scale.floatValue
                        val next = (current * zoomChange).coerceIn(TextViewerMinZoom, TextViewerMaxZoom)
                        val factor = next / current
                        if (factor.isFinite() && factor != 1f) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val target = textViewerZoomScrollTarget(
                                scrollX = horizontalScroll.value.toFloat(),
                                scrollY = verticalScroll.value.toFloat(),
                                centroidX = centroid.x,
                                centroidY = centroid.y,
                                factor = factor,
                            )
                            scrollTarget.value = Offset(target.x, target.y)
                            scale.floatValue = next
                            event.changes.forEach { change ->
                                if (change.pressed) change.consume()
                            }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

internal fun Modifier.gestureScale(scale: Float): Modifier {
    if (scale == 1f) return this
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val scaledWidth = (placeable.width * scale).roundToInt().coerceAtLeast(0)
        val scaledHeight = (placeable.height * scale).roundToInt().coerceAtLeast(0)
        layout(scaledWidth, scaledHeight) {
            placeable.place(0, 0)
        }
    }.drawWithContent {
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
            this@drawWithContent.drawContent()
        }
    }
}
