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
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ParkingViewModel(private val repo: BoardRepository) : LightViewModel<Unit>() {

    val boardState: StateFlow<BoardState> = repo.state

    // Only one control exists per row here (the pull-back caret), so armed
    // state is just the id of the row, not a (id, control) pair like Home's.
    private val _armed = MutableStateFlow<String?>(null)
    val armed: StateFlow<String?> = _armed.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch { repo.refreshParked() }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        // An armed control must never survive out of view, same as Home.
        _armed.value = null
    }

    /** First tap arms the row's caret; the second tap pulls it back. */
    fun tap(taskId: String) {
        if (_armed.value == taskId) {
            _armed.value = null
            viewModelScope.launch { repo.setParked(taskId, false) }
        } else {
            _armed.value = taskId
        }
    }

    fun clearArmed() {
        _armed.value = null
    }
}

class ParkingScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ParkingViewModel>(sealedActivity) {

    override val viewModelClass: Class<ParkingViewModel>
        get() = ParkingViewModel::class.java

    override fun createViewModel(): ParkingViewModel =
        ParkingViewModel(ToolGraph.repository(lightContext))

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
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text("Parking lot"),
                )
                ParkedCountLine(count = boardState.parked.size)

                if (boardState.error is BoardError.Unauthorized) {
                    CenteredMessage("Token rejected. Re-enter it in Settings.")
                } else if (boardState.parked.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CenteredMessage("Nothing parked.")
                    }
                } else {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 1f.gridUnitsAsDp()),
                    ) {
                        boardState.parked.forEach { task ->
                            ParkedRow(
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
private fun ParkedCountLine(count: Int) {
    LightText(
        text = "$count PARKED",
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
private fun ParkedRow(
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
        verticalAlignment = Alignment.CenterVertically,
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

        // Caret-up only, no checkbox: you do not complete things you have
        // parked, only decide to bring them back into active work.
        ControlIcon(
            icon = LightIcons.UP,
            armed = armed,
            enabled = controlsEnabled,
            contentDescription = "Pull back",
            onClick = onTapControl,
        )
    }
}
