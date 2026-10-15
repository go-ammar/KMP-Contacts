package com.sliide.kmp.ui.users

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sliide.kmp.domain.model.Gender
import com.sliide.kmp.domain.model.UserStatus
import com.sliide.kmp.presentation.users.AddUserEvent
import com.sliide.kmp.presentation.users.AddUserViewModel
import com.sliide.kmp.ui.components.AppIcons
import com.sliide.kmp.ui.components.shortMessage
import org.koin.compose.viewmodel.koinViewModel

/**
 * The add-user form, in a modal sheet so the list stays visible behind it. Validation state
 * comes straight from [AddUserViewModel] - the composable holds no form state of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserSheet(
    onDismiss: () -> Unit,
    viewModel: AddUserViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddUserEvent.Created -> onDismiss()
            }
        }
    }

    ModalBottomSheet(
        // Cancelling is as much a reason to clear the form as submitting it; a config change
        // does not route through here, so in-progress input still survives rotation.
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
        ) {
            Text("Add a user", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "They'll appear at the top of the list straight away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Full name") },
                leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                singleLine = true,
                isError = state.visibleNameError != null,
                supportingText = state.visibleNameError?.let { { Text(it) } },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus: FocusState ->
                            if (!focus.isFocused) {
                                viewModel.onNameFocusLost()
                            }
                        },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text("Email") },
                leadingIcon = { Icon(AppIcons.Email, contentDescription = null) },
                singleLine = true,
                isError = state.visibleEmailError != null,
                supportingText = state.visibleEmailError?.let { { Text(it) } },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.submit()
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus: FocusState ->
                            if (!focus.isFocused) {
                                viewModel.onEmailFocusLost()
                            }
                        },
            )

            Spacer(Modifier.height(20.dp))
            ChoiceRow(
                label = "Gender",
                options = remember { Gender.entries.toList() },
                selected = state.gender,
                optionLabel = { it.wireValue.replaceFirstChar(Char::uppercaseChar) },
                onSelect = viewModel::onGenderChanged,
            )

            Spacer(Modifier.height(12.dp))
            ChoiceRow(
                label = "Status",
                options = remember { UserStatus.entries.toList() },
                selected = state.status,
                optionLabel = { it.wireValue.replaceFirstChar(Char::uppercaseChar) },
                onSelect = viewModel::onStatusChanged,
            )

            AnimatedVisibility(visible = state.submitError != null) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.submitError?.shortMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Creating…")
                } else {
                    Text("Create user")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}
