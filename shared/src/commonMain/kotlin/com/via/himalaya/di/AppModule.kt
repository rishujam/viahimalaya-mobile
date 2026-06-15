package com.via.himalaya.di

import com.via.himalaya.data.remote.HttpClientFactory
import com.via.himalaya.data.repository.TrekRepositoryImpl
import com.via.himalaya.domain.repo.TrekRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel

expect val platformModule: Module

val sharedModule = module {
    single { HttpClientFactory.create(get()) }
    singleOf(::TrekRepositoryImpl).bind<TrekRepository>()
    viewModelOf(::ExploreViewModel)
    viewModelOf(::TrekDetailViewModel)
}
