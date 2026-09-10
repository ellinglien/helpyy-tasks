package com.thelightphone.helpyytasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.viewModelScope
import com.thelightphone.helpyytasks.data.BoardError
import com.thelightphone.helpyytasks.data.BoardRepository
import com.thelightphone.helpyytasks.data.PhoneTaskDetail
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
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val loading: Boolean = true,
    val detail: PhoneTaskDetail? = null,
    val error: BoardError? = null,
)

class TaskDetailViewModel(
    private val repo: BoardRepository,
    private val taskId: String,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(TaskDetailUiState())
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    // repo.detail() is a one-shot suspend call, not a StateFlow like
    // repo.state — there is nothing for this screen to collect from the
    // repository, so the fetched detail and any failure are held here.
    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            _state.value = TaskDetailUiState(loading = true)
            _state.value = try {
                TaskDetailUiState(loading = false, detail = repo.detail(taskId))
            } catch (e: BoardError) {
                TaskDetailUiState(loading = false, error = e)
            }
        }
    }
}

class TaskDetailScreen(
    sealedActivity: SealedLightActivity,
    private val taskId: String,
) : LightScreen<Unit, TaskDetailViewModel>(sealedActivity) {

    override val viewModelClass: Class<TaskDetailViewModel>
        get() = TaskDetailViewModel::class.java

    override fun createViewModel(): TaskDetailViewModel =
        TaskDetailViewModel(ToolGraph.repository(lightContext), taskId)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                val detail = state.detail
                when {
                    state.error is BoardError.Unauthorized ->
                        CenteredMessage("Token rejected. Re-enter it in Settings.")

                    // Only a task id is passed to this screen (per the plan),
                    // so on a failed fetch there is no title already in hand
                    // to keep showing — the detail call is the only source of
                    // one. A generic one-line failure is what's left.
                    state.error != null ->
                        CenteredMessage("Couldn't load this task.")

                    state.loading || detail == null ->
                        CenteredMessage("Loading…")

                    else -> DetailBody(
                        detail = detail,
                        onMove = {
                            navigateTo(screenFactory = { activity ->
                                MoveScreen(activity, taskId, detail.column, detail.parked)
                            })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBody(detail: PhoneTaskDetail, onMove: () -> Unit) {
    LightScrollView(
        modifier = Modifier
            .fillMaxSize()
            .padding(1f.gridUnitsAsDp()),
    ) {
        VerbBoldText(detail.title, detailTitleStyle())

        val meta = buildList {
            add(detail.column)
            if (detail.labels.isNotEmpty()) add(detail.labels.joinToString(", "))
        }.joinToString(" · ")
        LightText(
            text = meta,
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
        )

        if (detail.body.isNotBlank()) {
            DetailSection(title = "notes") {
                LightText(text = detail.body, variant = LightTextVariant.Paragraph)
            }
        }

        detail.nextAction?.takeIf { it.isNotBlank() }?.let { nextAction ->
            DetailSection(title = "next") {
                LightText(text = nextAction, variant = LightTextVariant.Paragraph)
            }
        }

        if (detail.suggestedSubtasks.isNotEmpty()) {
            DetailSection(title = "subtasks") {
                detail.suggestedSubtasks.forEach { subtask ->
                    LightText(
                        text = "– $subtask",
                        variant = LightTextVariant.Paragraph,
                        modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                    )
                }
            }
        }

        LightText(
            text = "MOVE",
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onMove)
                .padding(top = 1.5f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 1f.gridUnitsAsDp())) {
        LightText(text = title, variant = LightTextVariant.Detail, lighten = true)
        content()
    }
}

/** The `subheading` rung of the compact type scale, for a screen title. */
@Composable
private fun detailTitleStyle(): TextStyle {
    val base = LightThemeTokens.typography.subheading
    return base.copy(
        fontSize = base.fontSize.value.designVerticalPxToSp(),
        lineHeight = (base.fontSize.value * 1.15f).designVerticalPxToSp(),
    )
}
