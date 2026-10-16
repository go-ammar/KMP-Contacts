package com.roster.ui

import androidx.compose.runtime.Composable
import com.roster.ui.theme.RosterTheme
import com.roster.ui.users.UserListScreen

/** The single composable both platforms host. */
@Composable
fun App() {
    RosterTheme {
        UserListScreen()
    }
}
