package com.ireddragonicy.konabessnext.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(app: Application): SharedPreferences {
        return app.getSharedPreferences("konabess_prefs", Context.MODE_PRIVATE)
    }

    // Repositories are provided by their @Inject constructor + @Singleton annotation.
    // Explicit provision is not needed unless binding to an interface or configuration is required.
    @Provides
    @Singleton
    fun provideExportHistoryManager(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): com.ireddragonicy.konabessnext.utils.ExportHistoryManager {
        return com.ireddragonicy.konabessnext.utils.ExportHistoryManager(context)
    }
}
