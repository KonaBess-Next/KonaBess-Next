package com.ireddragonicy.konabessnext.di

import com.ireddragonicy.konabessnext.repository.ChipRepository
import com.ireddragonicy.konabessnext.repository.ChipRepositoryInterface
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import com.ireddragonicy.konabessnext.repository.DeviceRepositoryInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChipRepository(
        chipRepository: ChipRepository
    ): ChipRepositoryInterface

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        deviceRepository: DeviceRepository
    ): DeviceRepositoryInterface
}
