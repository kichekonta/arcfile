package com.example.plugins.video

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.FileItem
import com.example.data.MediaThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Isolated Media3 ExoPlayer session controller managing playlist navigation, seek gestures,
 * playback speed, orientation/resize/repeat preferences, and resume timestamps inside VideoPlayerPlugin.
 */
class VideoPlayerSessionController(
    context: Context,
    initialQueue: List<FileItem>,
    initialItem: FileItem,
    private val onActiveItemChanged: (FileItem) -> Unit = {},
    private val createExoPlayer: Boolean = true
) {
    private val appContext = context.applicationContext
    private val repository = VideoPlayerStorageRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val initialSafeQueue = initialQueue.ifEmpty { listOf(initialItem) }
    private val initialIndex = initialSafeQueue.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0)
    private val initialPrefs = repository.loadPreferences()

    private val _uiState = MutableStateFlow(
        VideoPlayerUiState(
            queue = initialSafeQueue,
            activeIndex = initialIndex,
            isMuted = initialPrefs.isMuted,
            playbackSpeed = initialPrefs.playbackSpeed,
            orientationMode = initialPrefs.orientationMode,
            resizeMode = initialPrefs.resizeMode,
            repeatMode = initialPrefs.repeatMode,
            autoPlayNext = initialPrefs.autoPlayNext
        )
    )
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var attachedTextureView: TextureView? = null
    private var progressJob: Job? = null
    private var seekBadgeJob: Job? = null
    private var hudClearJob: Job? = null
    private var bannerClearJob: Job? = null
    private var controlsHideJob: Job? = null
    private var lastResumePersistAtMs = 0L
    private var pendingResumeAppliedForId: String? = null
    private var volumeAccumulatorFraction = 0f

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val active = _uiState.value.activeItem
                _uiState.update { state ->
                    state.copy(
                        videoWidth = videoSize.width,
                        videoHeight = videoSize.height,
                        trackSummary = state.trackSummary.copy(
                            width = videoSize.width,
                            height = videoSize.height
                        )
                    )
                }
                repository.recordCacheSessionMetadata(active, _uiState.value.trackSummary)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val videoFormat = tracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                ?.getTrackFormat(0)
            if (videoFormat != null) {
                val summary = VideoTrackSummary(
                    codec = videoFormat.sampleMimeType.orEmpty().removePrefix("video/").uppercase(),
                    bitrateKbps = (videoFormat.bitrate.takeIf { it > 0 } ?: 0) / 1000,
                    frameRate = videoFormat.frameRate.takeIf { it > 0f } ?: 0f,
                    width = videoFormat.width.takeIf { it > 0 } ?: _uiState.value.videoWidth,
                    height = videoFormat.height.takeIf { it > 0 } ?: _uiState.value.videoHeight
                )
                _uiState.update { state ->
                    state.copy(
                        trackSummary = summary,
                        videoWidth = if (summary.width > 0) summary.width else state.videoWidth,
                        videoHeight = if (summary.height > 0) summary.height else state.videoHeight
                    )
                }
                repository.recordCacheSessionMetadata(_uiState.value.activeItem, summary)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isBuffering = true, isEnded = false) }
                }

                Player.STATE_READY -> {
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: _uiState.value.durationMs
                    _uiState.update {
                        it.copy(
                            isPrepared = true,
                            isBuffering = false,
                            isEnded = false,
                            durationMs = dur,
                            errorMessage = null
                        )
                    }
                    applySavedResumeIfNeeded(dur)
                }

                Player.STATE_ENDED -> {
                    val active = _uiState.value.activeItem
                    repository.saveResumePosition(active, 0L, _uiState.value.durationMs)
                    val state = _uiState.value
                    when {
                        state.repeatMode == VideoRepeatMode.ONE -> {
                            player.seekTo(0L)
                            player.play()
                        }

                        state.autoPlayNext && state.hasNext -> {
                            nextVideo(autoTriggered = true)
                        }

                        else -> {
                            _uiState.update {
                                it.copy(
                                    isPlaying = false,
                                    isBuffering = false,
                                    isEnded = true,
                                    positionMs = it.durationMs,
                                    showControls = true
                                )
                            }
                        }
                    }
                }

                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                scheduleControlsAutoHide()
            } else {
                controlsHideJob?.cancel()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    showControls = true,
                    errorMessage = error.localizedMessage ?: "Unable to decode video stream"
                )
            }
        }
    }

    init {
        if (createExoPlayer) {
            initExoPlayer()
        }
        prepareCurrentMedia(autoPlay = true)
        startProgressLoop()
    }

    private fun initExoPlayer() {
        runCatching {
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
            ExoPlayer.Builder(appContext)
                .setAudioAttributes(audioAttrs, true)
                .setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(SEEK_STEP_MS)
                .setSeekForwardIncrementMs(SEEK_STEP_MS)
                .build()
                .apply {
                    addListener(playerListener)
                    volume = if (_uiState.value.isMuted) 0f else 1f
                    playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
                    attachedTextureView?.let { setVideoTextureView(it) }
                }
        }.onSuccess { player ->
            exoPlayer = player
        }
    }

    fun attachTextureView(textureView: TextureView?) {
        if (attachedTextureView === textureView) return
        attachedTextureView = textureView
        runCatching {
            exoPlayer?.setVideoTextureView(textureView)
        }
    }

    fun prepareCurrentMedia(autoPlay: Boolean = true) {
        val state = _uiState.value
        val active = state.activeItem
        pendingResumeAppliedForId = null
        onActiveItemChanged(active)

        val savedResume = repository.loadResumePosition(active)
        _uiState.update {
            it.copy(
                positionMs = savedResume,
                durationMs = 0L,
                bufferedMs = 0L,
                isPrepared = false,
                isBuffering = exoPlayer != null,
                isEnded = false,
                errorMessage = null,
                videoWidth = 0,
                videoHeight = 0,
                trackSummary = VideoTrackSummary()
            )
        }

        // Warm metadata probe for width/height/duration fallback
        scope.launch(Dispatchers.IO) {
            val cachedMeta = MediaThumbnailLoader.getCached(active, needVideoFrame = false)
                ?: MediaThumbnailLoader.loadMeta(appContext, active, needVideoFrame = false)
            if (cachedMeta != null && _uiState.value.activeItem.id == active.id) {
                _uiState.update { current ->
                    current.copy(
                        videoWidth = if (current.videoWidth > 0) current.videoWidth else cachedMeta.width,
                        videoHeight = if (current.videoHeight > 0) current.videoHeight else cachedMeta.height,
                        durationMs = if (current.durationMs > 0L) current.durationMs else (cachedMeta.durationMillis ?: 0L),
                        isPrepared = current.isPrepared || exoPlayer == null
                    )
                }
            }
        }

        val player = exoPlayer ?: return
        runCatching {
            val uri = if (active.isSafUri) {
                Uri.parse(active.pathOrUri)
            } else {
                Uri.fromFile(File(active.pathOrUri))
            }
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(active.id)
                .build()
            player.setMediaItem(mediaItem)
            player.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
            player.volume = if (_uiState.value.isMuted) 0f else 1f
            player.repeatMode = when (_uiState.value.repeatMode) {
                VideoRepeatMode.ONE -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            player.prepare()
            player.playWhenReady = autoPlay
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = err.localizedMessage ?: "Failed to open video"
                )
            }
        }
    }

    private fun applySavedResumeIfNeeded(durationMs: Long) {
        val active = _uiState.value.activeItem
        if (pendingResumeAppliedForId == active.id) return
        pendingResumeAppliedForId = active.id

        val savedMs = repository.loadResumePosition(active)
        if (savedMs >= 2_500L && (durationMs <= 0L || savedMs < durationMs - 3_000L)) {
            val clamped = if (durationMs > 0L) savedMs.coerceIn(0L, durationMs - 1_000L) else savedMs
            runCatching { exoPlayer?.seekTo(clamped) }
            _uiState.update { it.copy(positionMs = clamped) }
            showStatusBanner("Resumed from ${formatVideoDuration(clamped)}")
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(250L)
                val player = exoPlayer
                if (player != null &&
                    (player.playbackState == Player.STATE_READY ||
                        (player.playbackState == Player.STATE_BUFFERING && player.duration != C.TIME_UNSET)) &&
                    _uiState.value.scrubPreviewPositionMs == null
                ) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: _uiState.value.durationMs
                    val buf = player.bufferedPosition.coerceAtLeast(0L)
                    _uiState.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur,
                            bufferedMs = buf
                        )
                    }
                    val now = System.currentTimeMillis()
                    if (player.isPlaying && now - lastResumePersistAtMs >= 3_000L) {
                        lastResumePersistAtMs = now
                        repository.saveResumePosition(_uiState.value.activeItem, pos, dur)
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        val player = exoPlayer
        if (player == null) {
            _uiState.update { it.copy(isPlaying = !it.isPlaying, isEnded = false) }
            return
        }
        if (state.isEnded) {
            player.seekTo(0L)
            player.play()
            showControlsTemporary()
            return
        }
        if (player.isPlaying) {
            player.pause()
            persistCurrentPositionNow()
            _uiState.update { it.copy(isPlaying = false, showControls = true) }
        } else {
            player.play()
            _uiState.update { it.copy(isPlaying = true) }
            showControlsTemporary()
        }
    }

    fun play() {
        exoPlayer?.play()
        _uiState.update { it.copy(isPlaying = true, isEnded = false) }
        showControlsTemporary()
    }

    fun pause() {
        exoPlayer?.pause()
        persistCurrentPositionNow()
        _uiState.update { it.copy(isPlaying = false) }
        controlsHideJob?.cancel()
    }

    fun onScrubChange(targetMs: Long) {
        controlsHideJob?.cancel()
        val maxDur = _uiState.value.durationMs
        val clamped = if (maxDur > 0L) targetMs.coerceIn(0L, maxDur) else targetMs.coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                scrubPreviewPositionMs = clamped,
                positionMs = clamped
            )
        }
    }

    fun onScrubFinished() {
        val preview = _uiState.value.scrubPreviewPositionMs ?: return
        _uiState.update { it.copy(scrubPreviewPositionMs = null) }
        seekTo(preview)
    }

    fun seekTo(targetMs: Long) {
        val maxDur = _uiState.value.durationMs
        val clamped = if (maxDur > 0L) targetMs.coerceIn(0L, maxDur) else targetMs.coerceAtLeast(0L)
        runCatching { exoPlayer?.seekTo(clamped) }
        _uiState.update { it.copy(positionMs = clamped, isEnded = false) }
        persistCurrentPositionNow()
        showControlsTemporary()
    }

    fun seekBy(deltaMs: Long) {
        val base = _uiState.value.scrubPreviewPositionMs ?: _uiState.value.positionMs
        val maxDur = _uiState.value.durationMs
        val target = if (maxDur > 0L) {
            (base + deltaMs).coerceIn(0L, maxDur)
        } else {
            (base + deltaMs).coerceAtLeast(0L)
        }
        runCatching { exoPlayer?.seekTo(target) }
        val deltaSec = (deltaMs / 1000L).toInt()
        _uiState.update {
            it.copy(
                positionMs = target,
                isEnded = false,
                seekOverlayDeltaSeconds = deltaSec
            )
        }
        seekBadgeJob?.cancel()
        seekBadgeJob = scope.launch {
            delay(850L)
            _uiState.update { it.copy(seekOverlayDeltaSeconds = null) }
        }
        persistCurrentPositionNow()
        if (_uiState.value.showControls) {
            showControlsTemporary()
        }
    }

    fun previousVideo() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        // If > 4 seconds into current video & has no previous or standard player behavior only when >10s?
        // User explicitly asked for prev/next buttons: switching immediately when clicking Prev is cleaner & predictable.
        persistCurrentPositionNow()
        val prevIndex = when {
            state.activeIndex > 0 -> state.activeIndex - 1
            state.repeatMode == VideoRepeatMode.ALL && state.queue.size > 1 -> state.queue.lastIndex
            else -> return
        }
        _uiState.update { it.copy(activeIndex = prevIndex) }
        prepareCurrentMedia(autoPlay = true)
        showStatusBanner("Playing ${prevIndex + 1} / ${state.queue.size}")
        showControlsTemporary()
    }

    fun nextVideo(autoTriggered: Boolean = false) {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        if (!autoTriggered) {
            persistCurrentPositionNow()
        }
        val nextIndex = when {
            state.activeIndex < state.queue.lastIndex -> state.activeIndex + 1
            state.repeatMode == VideoRepeatMode.ALL && state.queue.size > 1 -> 0
            else -> return
        }
        _uiState.update { it.copy(activeIndex = nextIndex) }
        prepareCurrentMedia(autoPlay = true)
        showStatusBanner("Playing ${nextIndex + 1} / ${state.queue.size}")
        showControlsTemporary()
    }

    fun selectVideoAt(index: Int) {
        val state = _uiState.value
        if (index !in state.queue.indices) return
        persistCurrentPositionNow()
        _uiState.update { it.copy(activeIndex = index, isPlaylistOpen = false) }
        prepareCurrentMedia(autoPlay = true)
        showControlsTemporary()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        repository.saveSpeed(clamped)
        runCatching { exoPlayer?.playbackParameters = PlaybackParameters(clamped) }
        _uiState.update { it.copy(playbackSpeed = clamped) }
        showStatusBanner("Speed ${VideoPlaybackSpeeds.formatSpeed(clamped)}")
        showControlsTemporary()
    }

    fun cycleOrientationMode() {
        val next = _uiState.value.orientationMode.next()
        setOrientationMode(next)
    }

    fun setOrientationMode(mode: VideoOrientationMode) {
        repository.saveOrientationMode(mode)
        _uiState.update { it.copy(orientationMode = mode) }
        showStatusBanner("Orientation: ${mode.label}")
        showControlsTemporary()
    }

    fun cycleResizeMode() {
        val next = _uiState.value.resizeMode.next()
        repository.saveResizeMode(next)
        _uiState.update { it.copy(resizeMode = next) }
        showStatusBanner("Aspect: ${next.label}")
        showControlsTemporary()
    }

    fun cycleRepeatMode() {
        val next = _uiState.value.repeatMode.next()
        repository.saveRepeatMode(next)
        runCatching {
            exoPlayer?.repeatMode = when (next) {
                VideoRepeatMode.ONE -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        _uiState.update { it.copy(repeatMode = next) }
        showStatusBanner(next.label)
        showControlsTemporary()
    }

    fun toggleAutoPlayNext() {
        val next = !_uiState.value.autoPlayNext
        repository.saveAutoPlayNext(next)
        _uiState.update { it.copy(autoPlayNext = next) }
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        repository.saveMuted(next)
        runCatching { exoPlayer?.volume = if (next) 0f else 1f }
        _uiState.update { it.copy(isMuted = next) }
        showStatusBanner(if (next) "Audio Muted" else "Audio Unmuted")
        showControlsTemporary()
    }

    fun toggleScreenLock() {
        val nextLocked = !_uiState.value.isLocked
        _uiState.update {
            it.copy(
                isLocked = nextLocked,
                showControls = !nextLocked,
                isPlaylistOpen = false,
                isSpeedSheetOpen = false,
                isInfoDialogOpen = false
            )
        }
        if (nextLocked) {
            controlsHideJob?.cancel()
            showStatusBanner("Controls Locked")
        } else {
            showStatusBanner("Controls Unlocked")
            showControlsTemporary()
        }
    }

    fun toggleControls() {
        if (_uiState.value.isLocked) {
            // Brief flash of lock state handled in screen
            _uiState.update { it.copy(showControls = !it.showControls) }
            return
        }
        val next = !_uiState.value.showControls
        _uiState.update { it.copy(showControls = next) }
        if (next) {
            scheduleControlsAutoHide()
        } else {
            controlsHideJob?.cancel()
        }
    }

    fun showControlsTemporary() {
        _uiState.update { it.copy(showControls = true) }
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        controlsHideJob?.cancel()
        controlsHideJob = scope.launch {
            delay(4_200L)
            val s = _uiState.value
            if (s.isPlaying && !s.isPlaylistOpen && !s.isSpeedSheetOpen && !s.isInfoDialogOpen && s.scrubPreviewPositionMs == null) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    fun setPlaylistOpen(open: Boolean) {
        _uiState.update { it.copy(isPlaylistOpen = open, isSpeedSheetOpen = false) }
        if (open) controlsHideJob?.cancel() else showControlsTemporary()
    }

    fun setSpeedSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isSpeedSheetOpen = open, isPlaylistOpen = false) }
        if (open) controlsHideJob?.cancel() else showControlsTemporary()
    }

    fun setInfoDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isInfoDialogOpen = open) }
        if (open) controlsHideJob?.cancel() else showControlsTemporary()
    }

    fun adjustVolumeBy(deltaFraction: Float) {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeAccumulatorFraction += deltaFraction * maxVol
        val step = volumeAccumulatorFraction.toInt()
        if (step != 0) {
            volumeAccumulatorFraction -= step
            val nextVol = (curVol + step).coerceIn(0, maxVol)
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
            }
            val pct = ((nextVol.toFloat() / maxVol.toFloat()) * 100f).toInt().coerceIn(0, 100)
            updateVolumeOverlay(pct)
        } else {
            val pct = ((curVol.toFloat() / maxVol.toFloat()) * 100f).toInt().coerceIn(0, 100)
            updateVolumeOverlay(pct)
        }
    }

    private fun updateVolumeOverlay(percent: Int) {
        _uiState.update { it.copy(volumeOverlayPercent = percent, brightnessOverlayPercent = null) }
        hudClearJob?.cancel()
        hudClearJob = scope.launch {
            delay(900L)
            _uiState.update { it.copy(volumeOverlayPercent = null, brightnessOverlayPercent = null) }
        }
    }

    fun updateBrightnessOverlay(percent: Int) {
        _uiState.update { it.copy(brightnessOverlayPercent = percent.coerceIn(0, 100), volumeOverlayPercent = null) }
        hudClearJob?.cancel()
        hudClearJob = scope.launch {
            delay(900L)
            _uiState.update { it.copy(brightnessOverlayPercent = null, volumeOverlayPercent = null) }
        }
    }

    fun showStatusBanner(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
        bannerClearJob?.cancel()
        bannerClearJob = scope.launch {
            delay(1_800L)
            _uiState.update { it.copy(statusBannerMessage = null) }
        }
    }

    fun clearAllTransientOverlays() {
        volumeAccumulatorFraction = 0f
    }

    fun persistCurrentPositionNow() {
        val s = _uiState.value
        repository.saveResumePosition(s.activeItem, s.positionMs, s.durationMs)
    }

    fun release() {
        persistCurrentPositionNow()
        progressJob?.cancel()
        seekBadgeJob?.cancel()
        hudClearJob?.cancel()
        bannerClearJob?.cancel()
        controlsHideJob?.cancel()
        runCatching {
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.setVideoTextureView(null)
            exoPlayer?.release()
        }
        exoPlayer = null
        attachedTextureView = null
        scope.cancel()
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
    }
}
