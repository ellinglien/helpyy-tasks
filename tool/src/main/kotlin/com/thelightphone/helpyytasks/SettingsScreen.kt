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
import com.thelightphone.helpyytasks.data.Settings
import com.thelightphone.helpyytasks.data.TokenStore
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
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

private const val DEFAULT_BASE_URL = "https://helpyy.app"

/** Which field, if any, is open in the full-screen editor. */
enum class Editing { None, BaseUrl, Token }

data class SettingsUiState(
    val baseUrl: String = DEFAULT_BASE_URL,
    val hasToken: Boolean = false,
    val editing: Editing = Editing.None,
)

class SettingsViewModel(private val tokenStore: TokenStore) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // Only set when the user actually submits a new token in the editor. Saving
    // with this null leaves whatever token is already on disk untouched.
    private var pendingToken: String? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val settings = tokenStore.read()
            _state.value = _state.value.copy(
                baseUrl = settings.baseUrl.ifBlank { DEFAULT_BASE_URL },
                hasToken = settings.token.isNotBlank(),
            )
        }
    }

    fun beginEditBaseUrl() {
        _state.value = _state.value.copy(editing = Editing.BaseUrl)
    }

    fun beginEditToken() {
        _state.value = _state.value.copy(editing = Editing.Token)
    }

    fun cancelEdit() {
        _state.value = _state.value.copy(editing = Editing.None)
    }

    fun submitBaseUrl(value: String) {
        _state.value = _state.value.copy(baseUrl = value, editing = Editing.None)
    }

    fun submitToken(value: String) {
        pendingToken = value
        _state.value = _state.value.copy(
            hasToken = value.isNotBlank() || _state.value.hasToken,
            editing = Editing.None,
        )
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            // The token editor never sees the stored token, so an untouched field
            // must fall back to what is already on disk rather than blanking it.
            val existingToken = pendingToken ?: tokenStore.read().token
            tokenStore.write(Settings.normalised(_state.value.baseUrl, existingToken))
            onSaved()
        }
    }
}

class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel(): SettingsViewModel =
        SettingsViewModel(ToolGraph.tokenStore(lightContext))

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            when (state.editing) {
                Editing.BaseUrl -> {
                    val fieldState = remember(state.editing) { TextFieldState(state.baseUrl) }
                    LightTextInputEditor(
                        title = "Server",
                        state = fieldState,
                        onSubmit = { viewModel.submitBaseUrl(it.toString()) },
                        onBack = { viewModel.cancelEdit() },
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        submitLabel = "SAVE",
                        singleLine = true,
                    )
                }

                Editing.Token -> {
                    // Always starts empty: the stored token is never read back into
                    // the editor, so this can never leak it onto the screen.
                    val fieldState = remember(state.editing) { TextFieldState("") }
                    LightTextInputEditor(
                        title = "Token",
                        state = fieldState,
                        onSubmit = { viewModel.submitToken(it.toString()) },
                        onBack = { viewModel.cancelEdit() },
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        submitLabel = "SAVE",
                        singleLine = true,
                    )
                }

                Editing.None -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background)
                            .padding(1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = "Settings",
                            variant = LightTextVariant.Subheading,
                            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                        )

                        LightTextField(
                            label = "Server",
                            value = state.baseUrl,
                            placeholder = DEFAULT_BASE_URL,
                            onClick = { viewModel.beginEditBaseUrl() },
                            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                        )

                        LightTextField(
                            label = if (state.hasToken) "Token (set — tap to replace)" else "Token",
                            value = "",
                            placeholder = "Not set",
                            onClick = { viewModel.beginEditToken() },
                            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                        )

                        LightText(
                            text = "SAVE",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { viewModel.save { goBack() } }
                                .padding(top = 1f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}
