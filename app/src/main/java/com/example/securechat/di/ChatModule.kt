package com.example.securechat.di

import com.example.securechat.data.repository.ChatRepositoryImpl
import com.example.securechat.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindCustomGroupRepository(
        impl: com.example.securechat.data.repository.CustomGroupRepositoryImpl
    ): com.example.securechat.domain.repository.CustomGroupRepository

    @Binds
    @Singleton
    abstract fun bindFileStorageRepository(
        impl: com.example.securechat.data.repository.FileStorageRepositoryImpl
    ): com.example.securechat.domain.repository.FileStorageRepository
}
