package com.lizongying.mytv0

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.lizongying.mytv0.data.SourceType
import com.lizongying.mytv0.databinding.PlayerBinding
import com.lizongying.mytv0.models.TVModel


class PlayerFragment : Fragment() {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null

    private var tvModel: TVModel? = null
    private val aspectRatio = 16f / 9f

    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideVolume = 2 * 1000L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updatePlayer()
        (activity as MainActivity).ready(TAG)
    }

    private val bufferingTimeoutRunnable = Runnable {
        Log.w(TAG, "Buffering timeout (8s), auto retrying or switching source...")
        retryOrNextVideo("BufferingTimeout")
    }

    private fun retryOrNextVideo(reason: String) {
        if (tvModel == null) {
            Log.e(TAG, "tvModel == null, cannot retry ($reason)")
            return
        }
        val tv = tvModel!!
        handler.removeCallbacks(bufferingTimeoutRunnable)
        if (tv.retryTimes < tv.retryMaxTimes) {
            var last = true
            if (tv.getSourceTypeDefault() == SourceType.UNKNOWN) {
                last = tv.nextSourceType()
            }
            tv.setReady(true)
            if (last) {
                tv.retryTimes++
            }
            Log.i(
                TAG,
                "[$reason] retry ${tv.videoIndex.value} ${tv.getSourceTypeCurrent()} ${tv.retryTimes}/${tv.retryMaxTimes}"
            )
        } else {
            if (!tv.isLastVideo()) {
                Log.i(TAG, "[$reason] switch to next video url")
                tv.nextVideo()
                tv.setReady(true)
                tv.retryTimes = 0
            } else {
                Log.w(TAG, "[$reason] all sources failed")
                tv.setErrInfo(R.string.play_error.getString())
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun updatePlayer() {
        if (context == null) {
            Log.e(TAG, "context == null")
            return
        }

        val ctx = requireContext()

        val playerView = binding.playerView

        val renderersFactory = DefaultRenderersFactory(ctx)
        val playerMediaCodecSelector = PlayerMediaCodecSelector()
        renderersFactory.setMediaCodecSelector(playerMediaCodecSelector)
        renderersFactory.setExtensionRendererMode(
            if (SP.softDecode) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                30_000,
                1_000,
                2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        if (player != null) {
            handler.removeCallbacks(bufferingTimeoutRunnable)
            player?.release()
        }

        player = ExoPlayer.Builder(ctx)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
        player?.repeatMode = REPEAT_MODE_ALL
        player?.playWhenReady = true
        player?.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val ratio = playerView.measuredWidth.div(playerView.measuredHeight)
                val layoutParams = playerView.layoutParams
                if (ratio < aspectRatio) {
                    layoutParams?.height =
                        (playerView.measuredWidth.div(aspectRatio)).toInt()
                    playerView.layoutParams = layoutParams
                } else if (ratio > aspectRatio) {
                    layoutParams?.width =
                        (playerView.measuredHeight.times(aspectRatio)).toInt()
                    playerView.layoutParams = layoutParams
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_BUFFERING) {
                    handler.removeCallbacks(bufferingTimeoutRunnable)
                    handler.postDelayed(bufferingTimeoutRunnable, 8_000L)
                } else {
                    handler.removeCallbacks(bufferingTimeoutRunnable)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                if (tvModel == null) {
                    Log.e(TAG, "tvModel == null")
                    return
                }

                val tv = tvModel!!

                if (isPlaying) {
                    handler.removeCallbacks(bufferingTimeoutRunnable)
                    tv.confirmSourceType()
                    tv.confirmVideoIndex()
                    tv.setErrInfo("")
                    tv.retryTimes = 0
                } else {
                    Log.i(TAG, "${tv.tv.title} 播放停止")
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    (activity as MainActivity).onPlayEnd()
                }
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e(TAG, "onPlayerError: ${error.errorCodeName}(${error.errorCode}): ${error.message}")
                retryOrNextVideo("PlayerError")
            }
        })

        playerView.player = player
        tvModel?.let {
            play(it)
        }
    }

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        handler.removeCallbacks(bufferingTimeoutRunnable)
        player?.run {
            tvModel.getVideoUrl() ?: return

            while (true) {
                val last = tvModel.isLastVideo()
                val mediaItem = tvModel.getMediaItem()
                if (mediaItem == null) {
                    if (last) {
                        tvModel.setErrInfo(R.string.play_error.getString())
                        break
                    }
                    tvModel.nextVideo()
                    continue
                }
                val mediaSource = tvModel.getMediaSource()
                if (mediaSource != null) {
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(mediaItem)
                }
                prepare()
                break
            }
        }
    }

    @OptIn(UnstableApi::class)
    class PlayerMediaCodecSelector : MediaCodecSelector {
        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunnelingDecoder: Boolean
        ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
            val infos = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            // 优先选择硬件加速解码器（排除软件解码器 softwareOnly 以及系统自带的低性能 Google 纯软解）
            return infos.sortedWith(
                compareBy(
                    { it.softwareOnly },
                    { it.name.startsWith("c2.android") || it.name.startsWith("OMX.google") }
                )
            ).toMutableList()
        }
    }

    fun showVolume(visibility: Int) {
        binding.icon.visibility = visibility
        binding.volume.visibility = visibility
        hideVolume()
    }

    fun setVolumeMax(volume: Int) {
        binding.volume.max = volume
    }

    fun setVolume(progress: Int, volume: Boolean = false) {
        val context = requireContext()
        binding.volume.progress = progress
        binding.icon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                if (volume) {
                    if (progress > 0) R.drawable.volume_up_24px else R.drawable.volume_off_24px
                } else {
                    R.drawable.light_mode_24px
                }
            )
        )
    }

    fun hideVolume() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, delayHideVolume)
    }

    fun hideVolumeNow() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, 0)
    }

    private val hideVolumeRunnable = Runnable {
        binding.icon.visibility = View.GONE
        binding.volume.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (player?.isPlaying == false) {
            player?.prepare()
            player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(bufferingTimeoutRunnable)
        if (player?.isPlaying == true) {
            player?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(bufferingTimeoutRunnable)
        player?.release()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(bufferingTimeoutRunnable)
        _binding = null
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}