package com.via.himalaya.di

import com.via.himalaya.data.remote.HttpClientFactory
import com.via.himalaya.data.repository.FirebaseAuthRepository
import com.via.himalaya.data.repository.TrekRepositoryImpl
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.domain.repo.TrekRepository
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

expect val platformModule: Module

val sharedModule = module {
    single { HttpClientFactory.create(get()) }
    singleOf(::TrekRepositoryImpl).bind<TrekRepository>()
    viewModelOf(::ExploreViewModel)
    viewModelOf(::TrekDetailViewModel)

    // Auth
    single<FirebaseAuth> { Firebase.auth }
    singleOf(::FirebaseAuthRepository).bind<AuthRepository>()
    viewModelOf(::AuthViewModel)
}
