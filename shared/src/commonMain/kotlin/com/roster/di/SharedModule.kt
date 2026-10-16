package com.roster.di

import com.roster.data.local.DatabaseDriverFactory
import com.roster.data.local.RosterDatabase
import com.roster.data.local.UserLocalDataSource
import com.roster.data.remote.UserRemoteDataSource
import com.roster.data.remote.createHttpClient
import com.roster.data.repository.UserRepositoryImpl
import com.roster.domain.repository.UserRepository
import com.roster.domain.time.RelativeTimeFormatter
import com.roster.domain.usecase.CreateUserUseCase
import com.roster.domain.usecase.FinaliseUserDeletionUseCase
import com.roster.domain.usecase.HasCachedUsersUseCase
import com.roster.domain.usecase.MarkUserPendingDeletionUseCase
import com.roster.domain.usecase.ObserveUsersUseCase
import com.roster.domain.usecase.RefreshUsersUseCase
import com.roster.domain.usecase.RestoreAbandonedDeletionsUseCase
import com.roster.domain.usecase.UndoUserDeletionUseCase
import com.roster.domain.validation.UserInputValidator
import kotlinx.datetime.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android supplies a `Context` to open the database; iOS does not. */
expect fun platformModule(): Module

/** Data + domain wiring. ViewModels are declared in the UI module, next to their consumers. */
val sharedModule: Module =
    module {
        single { createHttpClient() }
        single { UserRemoteDataSource(client = get()) }

        single { RosterDatabase(get<DatabaseDriverFactory>().create()) }
        single { UserLocalDataSource(database = get()) }

        single<Clock> { Clock.System }
        single<UserRepository> { UserRepositoryImpl(remote = get(), local = get(), clock = get()) }

        single { UserInputValidator() }
        single { RelativeTimeFormatter(clock = get()) }

        factory { ObserveUsersUseCase(get()) }
        factory { RefreshUsersUseCase(get()) }
        factory { HasCachedUsersUseCase(get()) }
        factory { CreateUserUseCase(repository = get(), validator = get()) }
        factory { MarkUserPendingDeletionUseCase(get()) }
        factory { UndoUserDeletionUseCase(get()) }
        factory { FinaliseUserDeletionUseCase(get()) }
        factory { RestoreAbandonedDeletionsUseCase(get()) }
    }
