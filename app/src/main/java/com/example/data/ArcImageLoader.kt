package com.example.data

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.EnumMap

/**
 * Manages isolated Coil ImageLoaders per CacheBucket so Images Gallery, Movies Video thumbnails,
 * and Browser file previews keep separate disk caches that Settings can inspect & clear individually.
 */
object ArcImageLoader {
    private val loaders = EnumMap<CacheBucket, ImageLoader>(CacheBucket::class.java)

    fun get(context: Context): ImageLoader = getForBucket(context, CacheBucket.BROWSER_MEDIA)

    fun getForBucket(context: Context, bucket: CacheBucket): ImageLoader {
        synchronized(this) {
            loaders[bucket]?.let { return it }
            return buildLoader(context.applicationContext, bucket).also { loaders[bucket] = it }
        }
    }

    fun clearBucketCaches(context: Context, bucket: CacheBucket) {
        val loader = synchronized(this) { loaders[bucket] }
        loader?.memoryCache?.clear()
        @Suppress("OPT_IN_USAGE")
        runCatching { loader?.diskCache?.clear() }
        // Recreate disk-backed bucket directory cleanly if cleared outside loader
        ArcCacheManager.bucketDirectory(context, bucket)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildLoader(appContext: Context, bucket: CacheBucket): ImageLoader {
        val ioParallelism = Dispatchers.IO.limitedParallelism(4)
        val decodeParallelism = Dispatchers.IO.limitedParallelism(if (bucket == CacheBucket.MOVIE_VIDEOS) 2 else 3)
        val cacheDir = ArcCacheManager.bucketDirectory(appContext, bucket)

        return ImageLoader.Builder(appContext)
            .components {
                if (bucket == CacheBucket.MOVIE_VIDEOS || bucket == CacheBucket.BROWSER_MEDIA) {
                    add(VideoThumbnailFetcher.Factory(appContext))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(IcoDecoder.Factory())
            }
            .fetcherDispatcher(ioParallelism)
            .decoderDispatcher(decodeParallelism)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(if (bucket == CacheBucket.BROWSER_MEDIA) 0.18 else 0.14)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(bucket.maxBytes)
                    .build()
            }
            .crossfade(false)
            .allowRgb565(true)
            .build()
    }
}
