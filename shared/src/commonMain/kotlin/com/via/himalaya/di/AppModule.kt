package com.via.himalaya.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.via.himalaya.data.local.DatabaseFactory
import com.via.himalaya.data.local.NavigatorDatabase
import com.via.himalaya.data.local.NavigatorDatabaseFactory
import com.via.himalaya.data.local.TrekDatabase
import com.via.himalaya.data.local.UserPreferences
import com.via.himalaya.data.local.UserPreferencesImpl
import com.via.himalaya.data.remote.HttpClientFactory
import com.via.himalaya.data.repository.FirebaseAuthRepository
import com.via.himalaya.data.repository.TrekRepositoryImpl
import com.via.himalaya.domain.Tracker
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.data.remote.FirebaseTracker
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import com.via.himalaya.presentation.auth.AuthViewModel
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.presentation.downloads.DownloadedTrekViewModel
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics

expect val platformModule: Module

val sharedModule = module {
    single { HttpClientFactory.create(get()) }
    single<FirebaseAuth> { Firebase.auth }

    single {
        get<DatabaseFactory>()
            .create()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    single { get<TrekDatabase>().trekDao }

    single {
        get<NavigatorDatabaseFactory>()
            .create()
            .setDriver(BundledSQLiteDriver())
            .build()
    }
    single { get<NavigatorDatabase>().navigatorDao }
    single<FirebaseAnalytics> { Firebase.analytics }
    singleOf(::FirebaseTracker).bind<Tracker>()

    single<UserPreferences> { UserPreferencesImpl(get()) }

    singleOf(::TrekRepositoryImpl).bind<TrekRepository>()
    viewModelOf(::ExploreViewModel)
    viewModelOf(::TrekDetailViewModel)
    singleOf(::FirebaseAuthRepository).bind<AuthRepository>()
    viewModelOf(::AuthViewModel)
    viewModelOf(::DownloadedTrekViewModel)
}
