package com.journalgallery.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.journalgallery.android.di.appModule
import com.journalgallery.shared.di.allSharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JournalApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@JournalApp)
            modules(allSharedModules() + appModule)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
