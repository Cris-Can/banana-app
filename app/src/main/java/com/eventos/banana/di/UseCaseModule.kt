package com.eventos.banana.di

import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.usecase.auth.DeleteAccountUseCase
import com.eventos.banana.domain.usecase.auth.LoginUseCase
import com.eventos.banana.domain.usecase.auth.RegisterUseCase
import com.eventos.banana.domain.usecase.profile.ManageFriendsUseCase
import com.eventos.banana.domain.usecase.profile.UpdateProfileSettingsUseCase
import com.eventos.banana.domain.usecase.profile.UploadProfilePhotoUseCase
import com.eventos.banana.domain.usecase.profile.CreateUserProfileUseCase
import com.eventos.banana.domain.usecase.event.CreateEventUseCase
import com.eventos.banana.domain.usecase.event.ProcessEncounterUseCase
import com.eventos.banana.domain.usecase.event.ProcessCheckInUseCase
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.data.repository.EncounterRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideLoginUseCase(
        authRepository: AuthRepository,
        userRepository: UserRepository
    ): LoginUseCase = LoginUseCase(authRepository, userRepository)

    @Provides
    fun provideRegisterUseCase(
        authRepository: AuthRepository,
        createUserProfileUseCase: CreateUserProfileUseCase
    ): RegisterUseCase = RegisterUseCase(authRepository, createUserProfileUseCase)

    @Provides
    fun provideCreateUserProfileUseCase(
        userRepository: UserRepository,
        firestore: FirebaseFirestore
    ): CreateUserProfileUseCase = CreateUserProfileUseCase(userRepository, firestore)

    @Provides
    fun provideCreateEventUseCase(
        eventRepository: EventRepository,
        userRepository: UserRepository,
        subscriptionRepository: SubscriptionRepository
    ): CreateEventUseCase = CreateEventUseCase(eventRepository, userRepository, subscriptionRepository)

    @Provides
    fun provideProcessEncounterUseCase(
        encounterRepository: EncounterRepository,
        userRepository: UserRepository
    ): ProcessEncounterUseCase = ProcessEncounterUseCase(encounterRepository, userRepository)

    @Provides
    fun provideProcessCheckInUseCase(
        encounterRepository: EncounterRepository,
        userRepository: UserRepository
    ): ProcessCheckInUseCase = ProcessCheckInUseCase(encounterRepository, userRepository)

    @Provides
    fun provideDeleteAccountUseCase(
        authRepository: AuthRepository
    ): DeleteAccountUseCase = DeleteAccountUseCase(authRepository)

    @Provides
    fun provideUploadProfilePhotoUseCase(
        userRepository: UserRepository
    ): UploadProfilePhotoUseCase = UploadProfilePhotoUseCase(userRepository)

    @Provides
    fun provideManageFriendsUseCase(
        userRepository: UserRepository
    ): ManageFriendsUseCase = ManageFriendsUseCase(userRepository)

    @Provides
    fun provideUpdateProfileSettingsUseCase(
        userRepository: UserRepository
    ): UpdateProfileSettingsUseCase = UpdateProfileSettingsUseCase(userRepository)
}
