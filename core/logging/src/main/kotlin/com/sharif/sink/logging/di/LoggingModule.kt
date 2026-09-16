package com.sharif.sink.logging.di

import com.sharif.sink.logging.AndroidSinkLogger
import com.sharif.sink.logging.SinkLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidSinkLogger): SinkLogger
}
