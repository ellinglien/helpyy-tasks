package com.thelightphone.helpyytasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewModelScope
import com.thelightphone.helpyytasks.data.BoardError
import com.thelightphone.helpyytasks.data.BoardRepository
import com.thelightphone.helpyytasks.data.BoardState
import com.thelightphone.helpyytasks.data.PhoneTask
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which row control is being armed/committed. */
sealed class Control {
    object Park : Control()
    object Complete : Control()
}

/** The one control, on the one row, that is one tap from committing. */
data class Armed(val taskId: String, val control: Control)

private data class TaskSection(val column: String, val tasks: List<PhoneTask>)

/** Groups consecutive same-column tasks without touching server order. */
private fun groupConsecutiveSections(tasks: List<PhoneTask>): List<TaskSection> {
    val sections = mutableListOf<TaskSection>()
    for (task in tasks) {
        val lastIndex = sections.lastIndex
        val last = sections.getOrNull(lastIndex)
        if (last != null && last.column == task.column) {
            sections[lastIndex] = last.copy(tasks = last.tasks + task)
        } else {
            sections.add(TaskSection(task.column, listOf(task)))
        }
    }
    return sections
}

class HomeViewModel(private val repo: BoardRepository) : LightViewModel<Unit>() {

    val boardState: StateFlow<BoardState> = repo.state

    private val _armed = MutableStateFlow<Armed?>(null)
    val armed: StateFlow<Armed?> = _armed.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch { repo.refresh() }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        // An armed control must never survive out of view.
        _armed.value = null
    }

    /** First tap on a control arms it; the second tap on the same one commits. */
    fun tapControl(taskId: String, control: Control) {
        val current = _armed.value
        if (current != null && current.taskId == taskId && current.control == control) {
            _armed.value = null
            viewModelScope.launch {
                when (control) {
                    Control.Park -> repo.setParked(taskId, true)
                    Control.Complete -> repo.complete(taskId)
                }
            }
        } else {
            _armed.value = Armed(taskId, control)
        }
    }

    /** A tap on anything else — another control, or a row — disarms without committing. */
    fun clearArmed() {
        _armed.value = null
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel =
        HomeViewModel(ToolGraph.repository(lightContext))

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val boardState by viewModel.boardState.collectAsState()
        val armed by viewModel.armed.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                HomeHeader(
                    onAdd = { navigateTo(::CaptureScreen) },
                    onParkingLot = { navigateTo(::ParkingScreen) },
                    onSettings = { navigateTo(::SettingsScreen) },
                )

                if (boardState.error is BoardError.Unauthorized) {
                    CenteredMessage("Token rejected. Re-enter it in Settings.")
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (boardState.stale) {
                            LightText(
                                text = "Connection is gone. Showing what was last saved.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(
                                    horizontal = 1f.gridUnitsAsDp(),
                                    vertical = 0.5f.gridUnitsAsDp(),
                                ),
                            )
                        }

                        if (boardState.tasks.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                CenteredMessage("Nothing active.")
                            }
                        } else {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 1f.gridUnitsAsDp()),
                            ) {
                                groupConsecutiveSections(boardState.tasks).forEach { section ->
                                    SectionHeader(section.column)
                                    section.tasks.forEach { task ->
                                        TaskRow(
                                            task = task,
                                            armed = armed,
                                            controlsEnabled = !boardState.stale,
                                            onTapControl = { control ->
                                                viewModel.tapControl(task.id, control)
                                            },
                                            onTapRow = {
                                                // A row tap while a control is armed disarms
                                                // rather than navigating: arming is a half-
                                                // finished gesture toward that control, and a
                                                // stray tap should cancel it, not launch a
                                                // screen behind its back. Only an unarmed row
                                                // opens detail.
                                                if (armed != null) {
                                                    viewModel.clearArmed()
                                                } else {
                                                    navigateTo(screenFactory = { activity ->
                                                        TaskDetailScreen(activity, task.id)
                                                    })
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val HEADER_ICON_UNITS = 2f

/** LightTopBar's own height, mirrored so Home's custom header lines up with it. */
private const val TOPBAR_HEIGHT_UNITS = 3f

// Reused by ParkingScreen, whose rows are styled identically to Home's.
internal const val CONTROL_BOX_UNITS = 2f
internal const val CONTROL_GLYPH_UNITS = 1.4f

/**
 * Text renders below the top of its layout box by about half the leading, so a
 * control aligned to the raw top sits high. This drops it to meet the glyph.
 */
internal const val CONTROL_TOP_NUDGE_UNITS = 0.35f

@Composable
private fun HomeHeader(
    onAdd: () -> Unit,
    onParkingLot: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        // Matches LightTopBar's own metrics so TASKS sits at the same size and
        // height as PARKING LOT, SETTINGS and the rest: 3 grid units tall, 1 unit
        // of horizontal padding, centre text at Fine. Home cannot use LightTopBar
        // itself -- that has only leftButton/center/rightButton, and the header
        // carries three icons plus a title.
        modifier = Modifier
            .fillMaxWidth()
            .height(TOPBAR_HEIGHT_UNITS.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = "TASKS",
            variant = LightTextVariant.Fine,
            modifier = Modifier.weight(1f),
        )
        PlusIcon(
            modifier = Modifier
                .lightClickable(onClick = onAdd)
                .padding(end = 1f.gridUnitsAsDp()),
        )
        ParkingIcon(
            modifier = Modifier
                .lightClickable(onClick = onParkingLot)
                .padding(end = 1f.gridUnitsAsDp()),
        )
        LightIcon(
            icon = LightIcons.SETTINGS,
            size = HEADER_ICON_UNITS,
            contentDescription = "Settings",
            modifier = Modifier.lightClickable(onClick = onSettings),
        )
    }
}

@Composable
private fun SectionHeader(column: String) {
    LightText(
        text = column.lowercase(),
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
    )
}

@Composable
private fun TaskRow(
    task: PhoneTask,
    armed: Armed?,
    controlsEnabled: Boolean,
    onTapControl: (Control) -> Unit,
    onTapRow: () -> Unit,
) {
    val meta = task.labels.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onTapRow)
            .padding(vertical = 0.5f.gridUnitsAsDp(), horizontal = 0f.gridUnitsAsDp()),
        // Top-aligned, not centred: on a two-line title, centred controls drift
        // to the middle of the block and stop reading as belonging to the row.
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 0.5f.gridUnitsAsDp()),
        ) {
            VerbBoldText(task.title, rowTitleStyle())
            if (meta != null) {
                LightText(
                    text = meta,
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                )
            }
        }

        DrawnControl(
            kind = ControlKind.ParkDown,
            armed = armed?.taskId == task.id && armed.control == Control.Park,
            enabled = controlsEnabled,
            contentDescription = "Park",
            onClick = { onTapControl(Control.Park) },
            modifier = Modifier.padding(
                start = 1.5f.gridUnitsAsDp(),
                end = 1.25f.gridUnitsAsDp(),
                top = CONTROL_TOP_NUDGE_UNITS.gridUnitsAsDp(),
            ),
        )
        DrawnControl(
            kind = ControlKind.Complete,
            armed = armed?.taskId == task.id && armed.control == Control.Complete,
            enabled = controlsEnabled,
            contentDescription = "Complete",
            onClick = { onTapControl(Control.Complete) },
            modifier = Modifier.padding(top = CONTROL_TOP_NUDGE_UNITS.gridUnitsAsDp()),
        )
    }
}

/** Reused by ParkingScreen for its pull-back caret. */
@Composable
internal fun ControlIcon(
    icon: LightIconConfiguration,
    armed: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    // Armed inverts: content becomes the fill, background becomes the glyph.
    val fill = if (armed) colors.content else colors.background
    val glyph = if (armed) colors.background else colors.content

    Box(
        modifier = modifier
            .size(CONTROL_BOX_UNITS.gridUnitsAsDp())
            .background(fill)
            .lightClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon.drawableResource),
            contentDescription = contentDescription,
            tint = glyph,
            modifier = Modifier.size(CONTROL_GLYPH_UNITS.gridUnitsAsDp()),
        )
    }
}

/**
 * A title with its leading action verb bolded, per `splitTitleVerb`
 * (TitleVerb.kt). Shared by every screen that shows a task title — Home rows,
 * Parking rows, and TaskDetailScreen's larger heading — so the verb-bold rule
 * only lives in one place.
 */
@Composable
internal fun VerbBoldText(
    title: String,
    style: TextStyle,
    color: Color = LightThemeTokens.colors.content,
) {
    val split = splitTitleVerb(title)

    if (split == null) {
        Text(text = title, style = style, color = color)
    } else {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(split.verb) }
                if (split.rest.isNotEmpty()) {
                    append(" ")
                    append(split.rest)
                }
            },
            style = style,
            color = color,
        )
    }
}

/** The `paragraph` rung of the compact type scale, used for every task row. */
@Composable
internal fun rowTitleStyle(): TextStyle {
    val base = LightThemeTokens.typography.paragraph
    return base.copy(
        fontSize = base.fontSize.value.designVerticalPxToSp(),
        lineHeight = (base.fontSize.value * 1.25f).designVerticalPxToSp(),
    )
}

@Composable
internal fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            align = TextAlign.Center,
        )
    }
}

/**
 * The international parking symbol: a P in a box. LightIcons ships no car or
 * parking glyph, and a list icon read as "a bunch of lines" on the device.
 * Drawn rather than shipped as a drawable so it takes the theme's content
 * colour and the LP3's own typeface without new resource plumbing.
 */
@Composable
internal fun ParkingIcon(modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Box(
        modifier = modifier.size(HEADER_ICON_UNITS.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = 2.dp, color = colors.content),
        )
        LightText(
            text = "P",
            variant = LightTextVariant.Detail,
        )
    }
}

/**
 * The row controls, drawn rather than taken from LightIcons.
 *
 * LightIcons has no empty checkbox and no thin caret: DOWN renders as a filled
 * triangle and ACCEPT as a bare checkmark, which read as neither a checkbox nor
 * a caret on the device. These are stroked to match the design: an empty square
 * for complete, a chevron for park. Armed inverts — the square fills with
 * `content` and the glyph is drawn in `background`.
 */
@Composable
internal fun DrawnControl(
    kind: ControlKind,
    armed: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    val line = if (enabled) colors.content else colors.contentSecondary
    val glyph = if (armed) colors.background else line
    val box = CONTROL_BOX_UNITS.gridUnitsAsDp()

    Canvas(
        modifier = modifier
            .size(box)
            .lightClickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        // The glyph is drawn inside an inset so the shape reads smaller than the
        // tap target it sits in — 52dp of finger, a lighter mark on screen.
        val inset = size.width * 0.10f
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        val stroke = w * 0.085f

        when (kind) {
            ControlKind.Complete -> {
                if (armed) {
                    drawRect(color = line, topLeft = Offset(inset, inset), size = Size(w, h))
                    // Checkmark, only once armed — an empty box is the resting state.
                    val path = Path().apply {
                        moveTo(inset + w * 0.24f, inset + h * 0.52f)
                        lineTo(inset + w * 0.43f, inset + h * 0.72f)
                        lineTo(inset + w * 0.78f, inset + h * 0.28f)
                    }
                    drawPath(
                        path = path,
                        color = glyph,
                        style = Stroke(width = stroke * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else {
                    drawRect(color = line, topLeft = Offset(inset, inset), size = Size(w, h), style = Stroke(width = stroke))
                }
            }

            ControlKind.ParkDown, ControlKind.PullUp -> {
                if (armed) drawRect(color = line, topLeft = Offset(inset, inset), size = Size(w, h))
                val down = kind == ControlKind.ParkDown
                val path = Path().apply {
                    if (down) {
                        moveTo(inset + w * 0.20f, inset + h * 0.38f)
                        lineTo(inset + w * 0.50f, inset + h * 0.66f)
                        lineTo(inset + w * 0.80f, inset + h * 0.38f)
                    } else {
                        moveTo(inset + w * 0.20f, inset + h * 0.62f)
                        lineTo(inset + w * 0.50f, inset + h * 0.34f)
                        lineTo(inset + w * 0.80f, inset + h * 0.62f)
                    }
                }
                drawPath(
                    path = path,
                    color = glyph,
                    style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

internal enum class ControlKind { Complete, ParkDown, PullUp }

/**
 * A bare plus. The only add glyph LightIcons ships (ic_add_white) is a plus
 * inside a circle, which sits heavier in the header than the gear and the
 * parking box beside it.
 */
@Composable
internal fun PlusIcon(modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Canvas(
        modifier = modifier
            .size(HEADER_ICON_UNITS.gridUnitsAsDp())
            .semantics { contentDescription = "Add task" },
    ) {
        val stroke = size.width * 0.085f
        val arm = size.width * 0.34f
        val cx = size.width / 2
        val cy = size.height / 2
        drawLine(colors.content, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke, StrokeCap.Round)
        drawLine(colors.content, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke, StrokeCap.Round)
    }
}
