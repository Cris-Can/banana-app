package com.eventos.banana.di

import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.MessageRepository
import com.eventos.banana.data.repository.NotificationRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.FeedRepository
import com.eventos.banana.data.repository.MainFeedRepository
import com.eventos.banana.data.repository.UserAdminRepository
import com.eventos.banana.data.repository.UserCoreRepository
import com.eventos.banana.data.repository.UserGamificationRepository
import com.eventos.banana.data.repository.UserMediaRepository
import com.eventos.banana.data.repository.UserMessagingRepository
import com.eventos.banana.data.repository.UserSocialRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: com.google.firebase.auth.FirebaseAuth,
        rateLimitManager: com.eventos.banana.core.security.RateLimitManager
    ): AuthRepository = AuthRepository(firebaseAuth, rateLimitManager)

    @Provides
    @Singleton
    fun provideUserSocialRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        functions: com.google.firebase.functions.FirebaseFunctions,
        notificationRepository: NotificationRepository,
        rateLimitManager: com.eventos.banana.core.security.RateLimitManager
    ): UserSocialRepository = UserSocialRepository(firestore, functions, notificationRepository, rateLimitManager)

    @Provides
    @Singleton
    fun provideUserGamificationRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): UserGamificationRepository = UserGamificationRepository(firestore)

    @Provides
    @Singleton
    fun provideUserMediaRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
    ): UserMediaRepository = UserMediaRepository(firestore, storageDataSource)

    @Provides
    @Singleton
    fun provideUserMessagingRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): UserMessagingRepository = UserMessagingRepository(firestore)

    @Provides
    @Singleton
    fun provideUserAdminRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): UserAdminRepository = UserAdminRepository(firestore)

    @Provides
    @Singleton
    fun provideUserCoreRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): UserCoreRepository = UserCoreRepository(firestore)

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        notificationRepository: NotificationRepository,
        storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource,
        userSocialRepository: UserSocialRepository,
        userGamificationRepository: UserGamificationRepository,
        userMediaRepository: UserMediaRepository,
        userMessagingRepository: UserMessagingRepository,
        userAdminRepository: UserAdminRepository,
        userCoreRepository: UserCoreRepository
    ): UserRepository = UserRepository(firestore, notificationRepository, storageDataSource, userSocialRepository, userGamificationRepository, userMediaRepository, userMessagingRepository, userAdminRepository, userCoreRepository)

    @Provides
    @Singleton
    fun provideEventRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        notificationRepository: NotificationRepository,
        storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
    ): EventRepository = EventRepository(firestore, notificationRepository, storageDataSource)

    @Provides
    @Singleton
    fun provideNotificationRepository(firestore: com.google.firebase.firestore.FirebaseFirestore): NotificationRepository = NotificationRepository(firestore)

    @Provides
    @Singleton
    fun provideMessageRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        notificationRepository: NotificationRepository,
        storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
    ): MessageRepository = MessageRepository(firestore, notificationRepository, storageDataSource)

    @Provides
    @Singleton
    fun provideSubscriptionRepository(firestore: com.google.firebase.firestore.FirebaseFirestore): SubscriptionRepository = SubscriptionRepository(firestore)

    @Provides
    @Singleton
    fun provideEncounterRepository(firestore: com.google.firebase.firestore.FirebaseFirestore): EncounterRepository = EncounterRepository(firestore)

    @Provides
    @Singleton
    fun provideRatingRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        encounterRepository: EncounterRepository
    ): RatingRepository = RatingRepository(firestore, encounterRepository)

    @Provides
    @Singleton
    fun provideFeedRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
    ): FeedRepository = FeedRepository(firestore, storageDataSource)

    @Provides
    @Singleton
    fun provideMainFeedRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): MainFeedRepository = MainFeedRepository(firestore)
    @Provides
    @Singleton
    fun provideBillingRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        userRepository: UserRepository,
        authRepository: AuthRepository,
        eventRepository: EventRepository,
        @ApplicationScope appScope: kotlinx.coroutines.CoroutineScope
    ): com.eventos.banana.data.repository.BillingRepository = com.eventos.banana.data.repository.BillingRepository(context, userRepository, authRepository, eventRepository, appScope)

    @Provides
    @Singleton
    fun provideBiometricRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): com.eventos.banana.data.repository.BiometricRepository {
        return com.eventos.banana.data.repository.BiometricRepository(context)
    }
}
