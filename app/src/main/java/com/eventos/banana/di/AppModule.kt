package com.eventos.banana.di

import android.content.Context
import android.content.SharedPreferences
import com.eventos.banana.core.security.AppCheckHelper
import com.eventos.banana.core.security.RateLimitManager
import com.eventos.banana.util.AudioPlayerHelper
import com.eventos.banana.util.AudioRecorderHelper
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = context.getSharedPreferences("banana_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideAudioRecorderHelper(
        @ApplicationContext context: Context
    ): AudioRecorderHelper = AudioRecorderHelper(context)

    @Provides
    @Singleton
    fun provideAudioPlayerHelper(
        @ApplicationContext context: Context
    ): AudioPlayerHelper = AudioPlayerHelper(context)

    @Provides
    @Singleton
    fun provideFirestore(): com.google.firebase.firestore.FirebaseFirestore {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val settings = com.google.firebase.firestore.firestoreSettings {
            isPersistenceEnabled = true
        }
        firestore.firestoreSettings = settings
        return firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): com.google.firebase.auth.FirebaseAuth = 
        com.google.firebase.auth.FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): com.google.firebase.storage.FirebaseStorage = 
        com.google.firebase.storage.FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions

    @Provides
    @Singleton
    fun provideAppCheckHelper(
        @ApplicationContext context: Context
    ): AppCheckHelper = AppCheckHelper(context)

    @Provides
    @Singleton
    fun provideRateLimitManager(
        firebaseFunctions: FirebaseFunctions
    ): RateLimitManager = RateLimitManager(firebaseFunctions)

    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationScope(): kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): coil.ImageLoader = coil.ImageLoader.Builder(context)
        .memoryCache {
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil_image_cache"))
                .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
        }
        .crossfade(true)
        .respectCacheHeaders(false) // Firebase Storage no envía headers de cache válidos
        .build()
}
