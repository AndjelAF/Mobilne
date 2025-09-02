package com.example.mapmyst.di

import com.example.mapmyst.data.repository.CacheRepositoryImpl
import com.example.mapmyst.data.repository.LeaderboardRepositoryImpl
import com.example.mapmyst.data.repository.LocalStorageRepositoryImpl
import com.example.mapmyst.data.repository.LocationEvidenceRepositoryImpl
import com.example.mapmyst.data.repository.LocationRepositoryImpl
import com.example.mapmyst.data.repository.StorageRepositoryImpl
import com.example.mapmyst.data.repository.UserRepositoryImpl
import com.example.mapmyst.domain.repository.CacheRepository
import com.example.mapmyst.domain.repository.LeaderboardRepository
import com.example.mapmyst.domain.repository.LocationEvidenceRepository
import com.example.mapmyst.domain.repository.LocationRepository
import com.example.mapmyst.domain.repository.StorageRepository
import com.example.mapmyst.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

// Qualifiers za različite implementacije Storage-a
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalStorage

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirebaseStorage

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindCacheRepository(
        cacheRepositoryImpl: CacheRepositoryImpl
    ): CacheRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository

    // metoda za LocationEvidence
    @Binds
    @Singleton
    abstract fun bindLocationEvidenceRepository(
        locationEvidenceRepositoryImpl: LocationEvidenceRepositoryImpl
    ): LocationEvidenceRepository

    //  metoda za LeaderboardRepository
    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(
        leaderboardRepositoryImpl: LeaderboardRepositoryImpl
    ): LeaderboardRepository

    // Lokalna Storage implementacija
    @Binds
    @Singleton
    @LocalStorage
    abstract fun bindLocalStorageRepository(
        localStorageRepositoryImpl: LocalStorageRepositoryImpl
    ): StorageRepository

    // Firebase Storage implementacija
    @Binds
    @Singleton
    @FirebaseStorage
    abstract fun bindFirebaseStorageRepository(
        storageRepositoryImpl: StorageRepositoryImpl
    ): StorageRepository

    // GLAVNA implementacija - TRENUTNO KORISTI LOKALNU
    // Kada se aktivira Firebase Storage, promeni u storageRepositoryImpl
    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        localStorageRepositoryImpl: LocalStorageRepositoryImpl  //  OVDE MENJATI KADA SE AKTIVIRA FIREBASE
    ): StorageRepository
}