package com.thelightphone.helpyytasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.helpyytasks.data.BoardError
import com.thelightphone.helpyytasks.data.BoardRepository
import com.thelightphone.helpyytasks.data.BoardState
import com.thelightphone.helpyytasks.data.PhoneTask
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DoneViewModel(private val repo: BoardRepository) : LightViewModel<Unit>() {

    val boardState: StateFlow<BoardState> = repo.state

    // Only one control per row here (the restore caret), so armed state is
    // just the id of the row -- same shape as ParkingViewModel's.
    private val _armed = MutableStateFlow<String?>(null)
    val armed: StateFlow<String?> = _armed.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch { repo.refreshDone() }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        // An armed control must never survive out of view, same as Home/Parking.
        _armed.value = null
    }

    /** First tap arms the row's caret; the second tap restores it to active. */
    fun tap(taskId: String) {
        if (_armed.value == taskId) {
            _armed.value = null
            viewModelScope.launch { repo.restore(taskId) }
        } else {
            _armed.value = taskId
        }
    }

    fun clearArmed() {
        _armed.value = null
    }
}

class DoneScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, DoneViewModel>(sealedActivity) {

    override val viewModelClass: Class<DoneViewModel>
        get() = DoneViewModel::class.java

    override fun createViewModel(): DoneViewModel =
        DoneViewModel(ToolGraph.repository(lightContext))

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
                ScreenHeader(
                    title = "DONE",
                    onBack = { goBack() },
                    onAdd = { navigateTo(::CaptureScreen) },
                )
                DoneCountLine(count = boardState.done.size)

                if (boardState.error is BoardError.Unauthorized) {
                    CenteredMessage("Token rejected. Re-enter it in Settings.")
                } else if (boardState.done.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CenteredMessage("Nothing completed yet.")
                    }
                } else {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 1f.gridUnitsAsDp()),
                    ) {
                        boardState.done.forEach { task ->
                            DoneRow(
                                task = task,
                                armed = armed == task.id,
                                controlsEnabled = !boardState.stale,
                                onTapControl = { viewModel.tap(task.id) },
                                onTapRow = { viewModel.clearArmed() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneCountLine(count: Int) {
    LightText(
        text = "$count RECENTLY DONE",
        variant = LightTextVariant.Superfine,
        lighten = true,
        modifier = Modifier.padding(
            start = 1f.gridUnitsAsDp(),
            end = 1f.gridUnitsAsDp(),
            top = 0.5f.gridUnitsAsDp(),
            bottom = 0.25f.gridUnitsAsDp(),
        ),
    )
}

@Composable
private fun DoneRow(
    task: PhoneTask,
    armed: Boolean,
    controlsEnabled: Boolean,
    onTapControl: () -> Unit,
    onTapRow: () -> Unit,
) {
    val meta = task.labels.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onTapRow)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 0.5f.gridUnitsAsDp()),
        ) {
            VerbBoldText(task.title, rowTitleStyle())
            if (meta != null) {
                LightText(text = meta, variant = LightTextVariant.Superfine, lighten = true)
            }
        }

        // Caret-up only, restoring a done task back to `go` -- same control as
        // ParkingScreen's pull-back, same reasoning: nothing else to do with a
        // finished row here except decide to bring it back.
        DrawnControl(
            kind = ControlKind.PullUp,
            armed = armed,
            enabled = controlsEnabled,
            contentDescription = "Restore",
            onClick = onTapControl,
        )
    }
}
