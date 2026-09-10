package com.thelightphone.helpyytasks

import androidx.compose.foundation.background
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
import com.thelightphone.helpyytasks.data.BoardRepository
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
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
import kotlinx.coroutines.launch

/**
 * The six places a task can go. Order matches the board's column priority,
 * with the parking lot last since it is a step out of the flow rather than
 * a step along it. Shared with CaptureScreen's destination phase.
 */
internal val DESTINATIONS = listOf("asap", "go", "wait", "think", "done", "parking lot")

private const val PARKING_LOT = "parking lot"

class MoveViewModel(
    private val repo: BoardRepository,
    private val taskId: String,
) : LightViewModel<Unit>() {

    /** Sends the task to [destination] and then leaves the screen. */
    fun choose(destination: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (destination == PARKING_LOT) {
                repo.setParked(taskId, true)
            } else {
                repo.move(taskId, destination)
            }
            onDone()
        }
    }
}

class MoveScreen(
    sealedActivity: SealedLightActivity,
    private val taskId: String,
    private val currentColumn: String,
    private val currentlyParked: Boolean = false,
) : LightScreen<Unit, MoveViewModel>(sealedActivity) {

    override val viewModelClass: Class<MoveViewModel>
        get() = MoveViewModel::class.java

    override fun createViewModel(): MoveViewModel =
        MoveViewModel(ToolGraph.repository(lightContext), taskId)

    // A parked task is off-board regardless of the column it is parked in —
    // see BoardModels/PhoneTaskDetail.parked — so its "current" destination
    // reads as "parking lot", not whatever column it happens to sit in.
    private val current: String get() = if (currentlyParked) PARKING_LOT else currentColumn

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

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
                    center = LightTopBarCenter.Text("Move"),
                )
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    DESTINATIONS.forEach { destination ->
                        DestinationRow(
                            label = destination,
                            isCurrent = destination == current,
                            // No tap-to-arm here: this screen is already a
                            // deliberate choice from a list of six named
                            // destinations. Home's checkbox and caret need a
                            // confirming second tap because they sit under a
                            // thumb mid-scroll; picking a row here already is
                            // the confirmation, and a second one would just be
                            // friction on top of friction.
                            onClick = { viewModel.choose(destination) { goBack() } },
                        )
                    }
                }
            }
        }
    }
}

/** One row of a six-destination list. Shared by MoveScreen and CaptureScreen. */
@Composable
internal fun DestinationRow(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Copy,
            modifier = Modifier.weight(1f),
        )
        if (isCurrent) {
            LightIcon(
                icon = LightIcons.ACCEPT,
                size = 1.6f,
                contentDescription = "Current",
            )
        }
    }
}
