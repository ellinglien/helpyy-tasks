package com.thelightphone.helpytasks

import androidx.compose.foundation.background
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
import com.thelightphone.helpytasks.data.BoardError
import com.thelightphone.helpytasks.data.BoardRepository
import com.thelightphone.helpytasks.data.BoardState
import com.thelightphone.helpytasks.data.PhoneTask
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

// Reused by ParkingScreen, whose rows are styled identically to Home's.
internal const val CONTROL_BOX_UNITS = 2.5f
internal const val CONTROL_GLYPH_UNITS = 1.4f

@Composable
private fun HomeHeader(
    onAdd: () -> Unit,
    onParkingLot: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = "Tasks",
            variant = LightTextVariant.Subheading,
            modifier = Modifier.weight(1f),
        )
        LightIcon(
            icon = LightIcons.ADD,
            size = HEADER_ICON_UNITS,
            contentDescription = "Add task",
            modifier = Modifier
                .lightClickable(onClick = onAdd)
                .padding(end = 1f.gridUnitsAsDp()),
        )
        // No parking-lot (car) icon exists in LightIcons — LIST is the nearest
        // sensible stand-in for "browse the set-aside tasks".
        LightIcon(
            icon = LightIcons.LIST,
            size = HEADER_ICON_UNITS,
            contentDescription = "Parking lot",
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
        verticalAlignment = Alignment.CenterVertically,
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

        ControlIcon(
            icon = LightIcons.DOWN,
            armed = armed?.taskId == task.id && armed.control == Control.Park,
            enabled = controlsEnabled,
            contentDescription = "Park",
            onClick = { onTapControl(Control.Park) },
            modifier = Modifier.padding(end = 0.5f.gridUnitsAsDp()),
        )
        ControlIcon(
            icon = LightIcons.ACCEPT,
            armed = armed?.taskId == task.id && armed.control == Control.Complete,
            enabled = controlsEnabled,
            contentDescription = "Complete",
            onClick = { onTapControl(Control.Complete) },
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
