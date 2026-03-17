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
    fun provideAuthRepository(firebaseAuth: com.google.firebase.auth.FirebaseAuth): AuthRepository = AuthRepository(firebaseAuth)

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        notificationRepository: NotificationRepository,
        storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
    ): UserRepository = UserRepository(firestore, notificationRepository, storageDataSource)

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
}
