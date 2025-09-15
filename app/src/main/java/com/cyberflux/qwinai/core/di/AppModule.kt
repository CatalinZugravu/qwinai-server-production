package com.cyberflux.qwinai.core.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.cyberflux.qwinai.database.AppDatabase
import com.cyberflux.qwinai.network.AimlApiService
import com.cyberflux.qwinai.network.RetrofitInstance
import com.cyberflux.qwinai.network.OCRApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Main Hilt module providing core dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }






    @Provides
    @Singleton
    fun provideOCRApiService(): OCRApiService {
        return RetrofitInstance.createOCRApiService()
    }



}