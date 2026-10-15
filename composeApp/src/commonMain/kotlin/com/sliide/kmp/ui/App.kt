package com.sliide.kmp.ui

import androidx.compose.runtime.Composable
import com.sliide.kmp.ui.theme.SliideTheme
import com.sliide.kmp.ui.users.UserListScreen

/** The single composable both platforms host. */
@Composable
fun App() {
    SliideTheme {
        UserListScreen()
    }
}
