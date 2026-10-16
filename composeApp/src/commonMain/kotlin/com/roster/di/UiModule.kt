package com.roster.di

import com.roster.presentation.users.AddUserViewModel
import com.roster.presentation.users.UserListViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * ViewModels live beside the screens that consume them.
 *
 * Declared explicitly rather than with `viewModelOf(::…)`: the constructor-reference DSL asks
 * the graph for every parameter, including ones that have a default, so the list ViewModel's
 * defaulted ticker `Flow` would be looked up and fail. Spelling the construction out keeps the
 * default where it belongs - in the constructor - and leaves the parameter free for tests.
 */
val uiModule: Module =
    module {
        viewModel {
            UserListViewModel(
                observeUsers = get(),
                refreshUsers = get(),
                hasCachedUsers = get(),
                markPendingDeletion = get(),
                undoDeletion = get(),
                finaliseDeletion = get(),
                restoreAbandonedDeletions = get(),
                relativeTimeFormatter = get(),
            )
        }

        viewModel {
            AddUserViewModel(
                createUser = get(),
                validator = get(),
            )
        }
    }

/** Single entry point so Android and iOS cannot drift in what they register. */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(platformModule(), sharedModule, uiModule)
    }
