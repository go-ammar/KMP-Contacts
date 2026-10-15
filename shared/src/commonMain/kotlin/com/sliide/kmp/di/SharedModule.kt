package com.sliide.kmp.di

import com.sliide.kmp.data.local.DatabaseDriverFactory
import com.sliide.kmp.data.local.SliideDatabase
import com.sliide.kmp.data.local.UserLocalDataSource
import com.sliide.kmp.data.remote.UserRemoteDataSource
import com.sliide.kmp.data.remote.createHttpClient
import com.sliide.kmp.data.repository.UserRepositoryImpl
import com.sliide.kmp.domain.repository.UserRepository
import com.sliide.kmp.domain.time.RelativeTimeFormatter
import com.sliide.kmp.domain.usecase.CreateUserUseCase
import com.sliide.kmp.domain.usecase.FinaliseUserDeletionUseCase
import com.sliide.kmp.domain.usecase.HasCachedUsersUseCase
import com.sliide.kmp.domain.usecase.MarkUserPendingDeletionUseCase
import com.sliide.kmp.domain.usecase.ObserveUsersUseCase
import com.sliide.kmp.domain.usecase.RefreshUsersUseCase
import com.sliide.kmp.domain.usecase.RestoreAbandonedDeletionsUseCase
import com.sliide.kmp.domain.usecase.UndoUserDeletionUseCase
import com.sliide.kmp.domain.validation.UserInputValidator
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

        single { SliideDatabase(get<DatabaseDriverFactory>().create()) }
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
