package org.monogram.presentation.features.viewers.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun Modifier.videoGestures(
    exoPlayer: ExoPlayer,
    isLocked: Boolean,
    isInPipMode: Boolean,
    showControls: Boolean,
    isDoubleTapSeekEnabled: Boolean,
    isGesturesEnabled: Boolean,
    isZoomEnabled: Boolean,
    seekDurationMs: Long,
    zoomState: ZoomState,
    rootState: DismissRootState,
    screenHeightPx: Float,
    dismissDistancePx: Float,
    dismissVelocityThreshold: Float,
    onDismiss: () -> Unit,
    onToggleControls: () -> Unit,
    onGestureOverlayChange: (Boolean, ImageVector?, String?) -> Unit,
    onSeekFeedback: (Boolean, Boolean) -> Unit,
    context: Context
): Modifier {
    val scope = rememberCoroutineScope()
    val controlZoneRatio = 0.33f

    return this
        .pointerInput(isLocked, isInPipMode) {
            if (isInPipMode) return@pointerInput
            detectTapGestures(
                onDoubleTap = { offset ->
                    if (zoomState.scale.value > 1f) {
                        zoomState.onDoubleTap(scope, offset, 1f, size)
                    } else if (!isLocked && isDoubleTapSeekEnabled) {
                        val width = size.width
                        if (offset.x < width / 2) {
                            exoPlayer.seekTo(max(0, exoPlayer.currentPosition - seekDurationMs))
                            onSeekFeedback(true, true)
                        } else {
                            exoPlayer.seekTo(exoPlayer.currentPosition + seekDurationMs)
                            onSeekFeedback(true, false)
                        }
                    }
                },
                onTap = { onToggleControls() }
            )
        }
        .pointerInput(isLocked, isInPipMode) {
            if (isInPipMode) return@pointerInput
            var dragOnLeft = false
            var dragOnRight = false
            var startBrightness = 0.5f
            var startVolume = 0
            var maxVolume = 1
            var accumulatedDragY = 0f
            var lastAppliedVolume = -1
            detectVerticalDragGestures(
                onDragStart = { change ->
                    accumulatedDragY = 0f
                    if (!isLocked && isGesturesEnabled && zoomState.scale.value == 1f) {
                        val width = size.width
                        val x = change.x
                        dragOnLeft = x < width * controlZoneRatio
                        dragOnRight = x > width * (1f - controlZoneRatio)
                        if (dragOnLeft || dragOnRight) {
                            if (dragOnLeft) {
                                val activity = context.findActivity()
                                val currentBrightness =
                                    activity?.window?.attributes?.screenBrightness
                                startBrightness =
                                    (currentBrightness?.takeIf { it != -1f } ?: 0.5f).coerceIn(
                                        0f,
                                        1f
                                    )
                            }
                            if (dragOnRight) {
                                val audioManager =
                                    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                maxVolume =
                                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        .coerceAtLeast(1)
                                startVolume =
                                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                lastAppliedVolume = startVolume
                            }
                            onGestureOverlayChange(true, null, null)
                        } else {
                            dragOnLeft = false
                            dragOnRight = false
                        }
                    }
                },
                onDragEnd = {
                    dragOnLeft = false
                    dragOnRight = false
                    accumulatedDragY = 0f
                    onGestureOverlayChange(false, null, null)
                },
                onDragCancel = {
                    dragOnLeft = false
                    dragOnRight = false
                    accumulatedDragY = 0f
                    onGestureOverlayChange(false, null, null)
                }
            ) { change, dragAmount ->
                if (!isLocked && isGesturesEnabled && zoomState.scale.value == 1f) {
                    accumulatedDragY += dragAmount
                    val activity = context.findActivity()

                    if (dragOnLeft && activity != null) {
                        val lp = activity.window.attributes
                        var newBrightness = startBrightness - (accumulatedDragY / 1000f)
                        newBrightness = newBrightness.coerceIn(0f, 1f)
                        lp.screenBrightness = newBrightness
                        activity.window.attributes = lp
                        onGestureOverlayChange(
                            true,
                            Icons.Rounded.BrightnessMedium,
                            "${(newBrightness * 100).toInt()}%"
                        )
                    } else if (dragOnRight) {
                        val audioManager =
                            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val volumeDelta = (-accumulatedDragY / 50f).roundToInt()
                        val newVol = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                        if (newVol != lastAppliedVolume) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            lastAppliedVolume = newVol
                        }
                        onGestureOverlayChange(
                            true,
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            "${((newVol.toFloat() / maxVolume) * 100).toInt()}%"
                        )
                    }
                }
            }
        }
        .pointerInput(isLocked, isInPipMode, isZoomEnabled, showControls) {
            if (isInPipMode) return@pointerInput
            detectZoomAndDismissGestures(
                zoomState = zoomState,
                rootState = rootState,
                screenHeightPx = screenHeightPx,
                dismissThreshold = dismissDistancePx,
                dismissVelocityThreshold = dismissVelocityThreshold,
                allowZoom = isZoomEnabled,
                allowDismiss = showControls && !isLocked,
                onDismiss = onDismiss,
                scope = scope
            )
        }
}
