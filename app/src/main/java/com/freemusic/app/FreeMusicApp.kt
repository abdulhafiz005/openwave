package com.freemusic.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class FreeMusicApp : Application(), ImageLoaderFactory {
    // VideoFrameDecoder lets Coil show a frame of a local video file as its artwork.
    override fun newImageLoader() = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .build()
}
