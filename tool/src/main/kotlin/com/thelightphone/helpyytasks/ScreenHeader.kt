package com.thelightphone.helpyytasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * LightTopBar's own height, mirrored so every screen's header -- whether it
 * uses LightTopBar or this one -- lines up at the same size. LightTopBar has
 * only leftButton/center/rightButton, one right-hand slot, so it cannot carry
 * `+` and the gear together; every screen in this tool uses ScreenHeader
 * instead so they all still line up.
 */
internal const val TOPBAR_HEIGHT_UNITS = 3f

/**
 * Nominal size of a header glyph. Shrunk from 2f (LightTopBar/LightBarButton's
 * default) so four icons plus a title fit Home without crowding. Every drawn
 * icon already insets its mark within its box, so the visible glyph reads
 * smaller still than this number suggests.
 */
internal const val HEADER_ICON_UNITS = 1.6f

/**
 * The tappable area around a header icon. Held at the pre-shrink icon size so
 * shrinking the glyphs doesn't also shrink what a thumb has to hit -- the
 * Canvas gets smaller and padded, the clickable box around it does not.
 */
private const val HEADER_TAP_TARGET_UNITS = 2f

private const val HEADER_ICON_SPACING_UNITS = 1.25f

/**
 * The header every screen in this tool shares: an optional back chevron, a
 * title, caller-supplied icons, `+` (new task), and an optional gear
 * (settings -- only Home carries it, since it is the only way in).
 */
@Composable
internal fun ScreenHeader(
    title: String,
    onAdd: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    middleIcons: @Composable RowScope.() -> Unit = {},
) {
    Row(
        // Matches LightTopBar's own metrics so every title sits at the same
        // size and height: 3 grid units tall, 1 unit of horizontal padding,
        // centre text at Fine.
        modifier = Modifier
            .fillMaxWidth()
            .height(TOPBAR_HEIGHT_UNITS.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            HeaderIconSlot(
                onClick = onBack,
                contentDescription = "Back",
                modifier = Modifier.padding(end = HEADER_ICON_SPACING_UNITS.gridUnitsAsDp()),
            ) {
                BackChevronIcon()
            }
        }

        LightText(
            text = title,
            variant = LightTextVariant.Fine,
            modifier = Modifier.weight(1f),
        )

        // `+` leads the right-hand icons -- it's the most-used action, so it
        // sits closest to the title rather than buried after the parking
        // symbol and tick. Only Home has anything after it (middleIcons, then
        // the gear), so it's the only case needing the trailing gap.
        if (onAdd != null) {
            HeaderIconSlot(
                onClick = onAdd,
                contentDescription = "Add task",
                modifier = Modifier.padding(
                    end = if (onSettings != null) HEADER_ICON_SPACING_UNITS.gridUnitsAsDp() else 0.dp,
                ),
            ) {
                PlusIcon()
            }
        }

        middleIcons()

        if (onSettings != null) {
            HeaderIconSlot(onClick = onSettings, contentDescription = "Settings") {
                // Do NOT redraw the gear: ic_settings_white.xml is the real
                // system icon and a hand-drawn version would drift from it.
                // LightIcon does not inset its glyph the way the drawn icons
                // do, so at equal nominal size it reads about a point heavier
                // -- scaled down to compensate.
                LightIcon(
                    icon = LightIcons.SETTINGS,
                    size = HEADER_ICON_UNITS * 0.9f,
                    contentDescription = null,
                )
            }
        }
    }
}

/**
 * Centres [content] -- a header glyph drawn at [HEADER_ICON_UNITS] -- inside a
 * [HEADER_TAP_TARGET_UNITS] clickable box, so shrinking the glyphs never
 * shrinks what a thumb has to hit.
 */
@Composable
internal fun HeaderIconSlot(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(HEADER_TAP_TARGET_UNITS.gridUnitsAsDp())
            .lightClickable(onClick = onClick, onClickLabel = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * A left chevron, drawn rather than taken from `LightIcons.BACK`. That vector
 * is a filled shape sitting off-centre in its own 30x30 viewport (the glyph
 * only occupies the left half), so at this header's smaller nominal size it
 * would sit noticeably off from the other drawn icons' optical centre. Drawn
 * here with the same stroke technique as the row controls (DrawnControl) and
 * PlusIcon so all header icons read at one weight.
 */
@Composable
private fun BackChevronIcon(modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Canvas(
        modifier = modifier
            .size(HEADER_ICON_UNITS.gridUnitsAsDp())
            .semantics { contentDescription = "Back" },
    ) {
        val stroke = size.width * 0.13f
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.18f)
            lineTo(size.width * 0.30f, size.height * 0.50f)
            lineTo(size.width * 0.62f, size.height * 0.82f)
        }
        drawPath(
            path = path,
            color = colors.content,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * A drawn tick, matching the checkmark stroke used by DrawnControl's armed
 * Complete state. Opens DoneScreen from Home's header.
 */
@Composable
internal fun DoneTickIcon(modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Canvas(
        modifier = modifier
            .size(HEADER_ICON_UNITS.gridUnitsAsDp())
            .semantics { contentDescription = "Done" },
    ) {
        val stroke = size.width * 0.13f
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.76f)
            lineTo(size.width * 0.84f, size.height * 0.26f)
        }
        drawPath(
            path = path,
            color = colors.content,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
