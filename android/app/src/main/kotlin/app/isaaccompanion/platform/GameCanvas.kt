package app.isaaccompanion.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isaaccompanion.protocol.ItemKey
import app.isaaccompanion.ui.Hit
import app.isaaccompanion.ui.HitAction
import app.isaaccompanion.ui.ScreenInput
import app.isaaccompanion.ui.ScreenResult
import app.isaaccompanion.ui.drawScreen
import kotlin.math.max
import kotlin.math.min

/** Mutable bag read by the gesture handlers: what the last frame laid out. Not Compose state on purpose. */
private class FrameInfo {
    var scale = 1
    var originX = 0
    var originY = 0
    var hits: List<Hit> = emptyList()
    var maxScroll = 0
}

@Composable
fun GameCanvas(model: AppModel, onChooseResources: () -> Unit) {
    val status by model.client.status.collectAsStateWithLifecycle()
    val state by model.client.state.collectAsStateWithLifecycle()
    val items by model.client.items.collectAsStateWithLifecycle()
    val assets by model.assets.collectAsStateWithLifecycle()
    val names by model.names.collectAsStateWithLifecycle()
    val configured by model.resourcesConfigured.collectAsStateWithLifecycle()
    val iconVersion by model.iconVersion.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ItemKey?>(null) }
    var scroll by remember { mutableIntStateOf(0) }
    val frame = remember { FrameInfo() }

    fun toLogical(x: Float, y: Float) = ((x - frame.originX) / frame.scale).toInt() to ((y - frame.originY) / frame.scale).toInt()

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (lx, ly) = toLogical(offset.x, offset.y)
                    val hit = frame.hits.lastOrNull { it.rect.contains(lx, ly) && it.action !is HitAction.Grid }
                    when (val action = hit?.action) {
                        is HitAction.Tab -> { tab = action.index; selected = null; scroll = 0 }
                        is HitAction.Item -> selected = if (selected == action.key) null else action.key
                        HitAction.ChooseResources -> onChooseResources()
                        else -> selected = null
                    }
                }
            }
            .pointerInput(Unit) {
                var inGrid = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val (lx, ly) = toLogical(offset.x, offset.y)
                        inGrid = frame.hits.any { it.action is HitAction.Grid && it.rect.contains(lx, ly) }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (inGrid) {
                            change.consume()
                            scroll = (scroll - (dragAmount / frame.scale).toInt()).coerceIn(0, frame.maxScroll)
                        }
                    },
                )
            },
    ) {
        val pxW = size.width.toInt()
        val pxH = size.height.toInt()
        val scale = max(1, min(pxW / 260, pxH / 220))
        val lw = pxW / scale
        val lh = pxH / scale
        frame.scale = scale
        frame.originX = (pxW - lw * scale) / 2
        frame.originY = (pxH - lh * scale) / 2
        iconVersion // read so that newly loaded icons trigger a redraw
        val result: ScreenResult = drawScreen(
            ComposeGfx(this, scale, frame.originX, frame.originY),
            lw,
            lh,
            ScreenInput(
                status = status,
                state = state,
                items = items,
                assets = assets,
                names = names,
                tab = tab,
                selected = selected,
                scroll = scroll,
                resourcesConfigured = configured,
            ),
        )
        frame.hits = result.hits
        frame.maxScroll = result.maxScroll
    }
}
