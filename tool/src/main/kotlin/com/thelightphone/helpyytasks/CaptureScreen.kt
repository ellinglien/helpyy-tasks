package com.thelightphone.helpyytasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.helpyytasks.data.BoardRepository
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_DESTINATION = "go"
private const val PARKING_LOT = "parking lot"

/** Where this screen is: typing a title, or picking where it goes. */
sealed class CaptureStep {
    object Title : CaptureStep()
    data class Destination(val title: String) : CaptureStep()
}

class CaptureViewModel(private val repo: BoardRepository) : LightViewModel<Unit>() {

    private val _step = MutableStateFlow<CaptureStep>(CaptureStep.Title)
    val step: StateFlow<CaptureStep> = _step.asStateFlow()

    fun submitTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _step.value = CaptureStep.Destination(trimmed)
    }

    /** "parking lot" is not a real column: it means go, held off-board. */
    fun choose(title: String, destination: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val (column, parked) = if (destination == PARKING_LOT) {
                DEFAULT_DESTINATION to true
            } else {
                destination to false
            }
            repo.capture(title, column, parked)
            onDone()
        }
    }
}

class CaptureScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, CaptureViewModel>(sealedActivity) {

    override val viewModelClass: Class<CaptureViewModel>
        get() = CaptureViewModel::class.java

    override fun createViewModel(): CaptureViewModel =
        CaptureViewModel(ToolGraph.repository(lightContext))

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val step by viewModel.step.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            when (val current = step) {
                is CaptureStep.Title -> {
                    val fieldState = remember { TextFieldState("") }
                    LightTextInputEditor(
                        title = "New task",
                        state = fieldState,
                        onSubmit = { viewModel.submitTitle(it.toString()) },
                        onBack = { goBack() },
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        submitLabel = "NEXT",
                        showBackButton = true,
                        singleLine = true,
                        initialCaps = true,
                        modifier = Modifier.background(LightThemeTokens.colors.background),
                    )
                }

                is CaptureStep.Destination -> {
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
                            center = LightTopBarCenter.Text(current.title),
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
                                    isCurrent = destination == DEFAULT_DESTINATION,
                                    // Same reasoning as MoveScreen: picking a
                                    // row from this list is already the
                                    // deliberate act, so no tap-to-arm.
                                    onClick = {
                                        viewModel.choose(current.title, destination) { goBack() }
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
