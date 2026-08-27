package com.journalgallery.android.di

import com.journalgallery.android.ui.day.DayViewModel
import com.journalgallery.android.ui.gallery.GalleryViewModel
import com.journalgallery.android.ui.pairing.PairingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

/** Directory for locally recorded journal audio. */
class AudioPaths(val dir: String)

val appModule = module {
    single { AudioPaths(File(androidContext().filesDir, "journal-audio").absolutePath) }

    viewModel { GalleryViewModel(get(), get(), get(), get()) }
    viewModel { params -> DayViewModel(params.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PairingViewModel(get(), get(), get()) }
}
