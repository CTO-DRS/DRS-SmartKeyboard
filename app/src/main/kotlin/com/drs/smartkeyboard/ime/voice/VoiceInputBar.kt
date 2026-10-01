/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import org.drs.lib.compose.rememberReducedMotion
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggText

/**
 * DRS v1.23.0: the live dictation bar — «شريط الاستماع». While the
 * built-in recognizer owns the session it replaces the Smartbar: a
 * microphone glyph, the live partial transcript (or the honest
 * «تحدث الآن…» hint before any words arrive, or the localized error),
 * and a cancel button that stops listening immediately. The keyboard
 * stays visible below, so the user never loses their typing context.
 * Themed through the exact smartbar snygg elements, so every user theme
 * paints it consistently.
 *
 * DRS v1.26.0: when the live session runs on the ROM's on-device
 * recognizer, a small «بدون شبكة» chip appears next to the transcript —
 * the session's privacy truth, not a promise. AUTO sessions that got
 * the local engine show it exactly like strict ON_DEVICE_ONLY ones.
 *
 * DRS v1.27.0: the bar declares its listening state to the theme
 * engine — the root element stays "smartbar" (so every existing rule
 * keeps painting it, plain rules match any attribute query) but now
 * carries the voice attribute, letting any theme add
 * "smartbar[voice=`true`]" rules to tell the listening state apart
 * without duplicating its base styling.
 */
@Composable
fun VoiceInputBar() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val uiState by DrsVoiceInputBus.uiState.collectAsState()

    val hintText = stringResource(R.string.voice__listening)
    val message = when (val state = uiState) {
        is VoiceUiState.Listening -> state.partial.ifBlank { hintText }
        is VoiceUiState.Error -> stringResource(state.resId)
        VoiceUiState.Idle -> hintText
    }
    val stopLabel = stringResource(R.string.voice__cancel)
    // DRS v1.26.0: the badge flag rides on the state itself, so the chip
    // appears and disappears with the session that owns the truth.
    val onDeviceActive = (uiState as? VoiceUiState.Listening)?.onDevice == true
    val onDeviceLabel = stringResource(R.string.voice__on_device_badge)

    // DRS v1.24.0: «الشريط يتنفس» — the mic glyph breathes while the
    // recognizer is actually listening (a calm 620ms scale pulse, the
    // standard Material emphasis curve) and rests at its natural size
    // for idle/error states. Pure presentation: the state flow stays
    // the single source of truth, and the animation touches nothing
    // outside its own graphicsLayer.
    val listening = uiState is VoiceUiState.Listening
    // DRS v1.25.0: the live amplitude channel. Many recognition
    // services never deliver RMS at all, so the wave only replaces the
    // pulse once actual samples flowed this session — the pulse stays
    // the honest fallback everywhere else. remember(listening) gives
    // every new session a clean slate.
    val rmsAmplitude by DrsVoiceInputBus.rmsAmplitude.collectAsState()
    var sawRms by remember(listening) { mutableStateOf(false) }
    LaunchedEffect(listening, rmsAmplitude) {
        if (listening && rmsAmplitude > 0f) sawRms = true
    }
    val waveVisible = listening && sawRms
    // DRS a11y/i18n/ux (r0-I): reduced-motion gate — when the system's
    // animator duration scale is 0 ("remove animations" / battery saver),
    // the infinite pulse transition must NEVER be created; the mic rests
    // at its natural size. Uses the shared rememberReducedMotion() helper
    // from lib/compose (MotionScale, r1-E) — live-updating via a
    // ContentObserver, so toggling the system setting takes effect
    // without recreating this bar.
    val reducedMotion = rememberReducedMotion()
    val pulseScale: Float = if (reducedMotion) {
        1f
    } else {
        val pulseTransition = rememberInfiniteTransition(label = "drsVoicePulse")
        val animatedScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (listening && !waveVisible) 1.22f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "drsVoicePulseScale",
        )
        animatedScale
    }

    SnyggBox(
        elementName = DrsImeUi.Smartbar.elementName,
        // DRS v1.27.0: the listening-state declaration — plain "smartbar"
        // rules still match (rule attributes are matched against the
        // query, an attribute-less rule matches anything), while explicit
        // "smartbar[voice=`true`]" rules paint ONLY this bar.
        attributes = mapOf(DrsImeUi.Attr.Voice to true),
        modifier = Modifier
            .fillMaxWidth()
            .height(DrsImeSizing.smartbarHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DrsImeSizing.smartbarHeight / 5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            ) {
                SnyggIcon(
                    elementName = DrsImeUi.SmartbarActionTileIcon.elementName,
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = null,
                )
            }
            // DRS v1.25.0: the wave takes the mic's place as the live
            // breathing element once real RMS samples flow; the spacer
            // rhythm stays identical either way.
            if (waveVisible) {
                Spacer(modifier = Modifier.height(DrsImeSizing.smartbarHeight / 8f))
                LiveVoiceWave(
                    modifier = Modifier
                        .width(VOICE_WAVE_WIDTH.dp)
                        .height(DrsImeSizing.smartbarHeight / 2f),
                    amplitude = rmsAmplitude,
                )
                Spacer(modifier = Modifier.height(DrsImeSizing.smartbarHeight / 8f))
            } else {
                Spacer(modifier = Modifier.height(DrsImeSizing.smartbarHeight / 8f))
            }
            SnyggText(
                elementName = DrsImeUi.SmartbarActionTileText.elementName,
                modifier = Modifier.weight(1f),
                text = message,
            )
            // DRS v1.26.0: the «بدون شبكة» chip — a hairline outline in
            // the bar's own foreground so every theme paints it honestly.
            // Plain Text here: the chip's reduced size is a deliberate
            // visual exception, exactly like the tech keys' 14.sp labels.
            if (onDeviceActive) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 4.dp)
                        .border(
                            width = 1.dp,
                            color = LocalContentColor.current.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = onDeviceLabel,
                        color = LocalContentColor.current,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
            SnyggIconButton(
                elementName = DrsImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = { keyboardManager.voiceController.stop() },
            ) {
                SnyggIcon(
                    elementName = DrsImeUi.SmartbarActionTileIcon.elementName,
                    imageVector = Icons.Default.Close,
                    contentDescription = stopLabel,
                )
            }
        }
    }
}

/** Five vertical bars, three dp each with equal gaps — 27 dp total. */
private const val VOICE_WAVE_BARS = 5
private const val VOICE_WAVE_WIDTH = 27
private const val VOICE_WAVE_BAR_DP = 3f
private const val VOICE_WAVE_GAP_DP = 3f

/**
 * Deterministic per-bar shape factors — a fixed silhouette the live
 * amplitude breathes into (no time-based randomness, so bar heights
 * stay stable: height = amplitude x factor).
 */
private val VOICE_WAVE_SHAPE = floatArrayOf(0.55f, 0.9f, 1f, 0.75f, 0.45f)

/**
 * DRS v1.25.0: the live voice wave — five rounded bars whose heights
 * follow the quantized RMS amplitude flowing through
 * [DrsVoiceInputBus.rmsAmplitude]. Painted with the smartbar element's
 * own foreground (LocalContentColor as provided by the enclosing snygg
 * style), so every user theme paints it consistently. Shown only while
 * the recognizer actually reports amplitude; the v1.24 mic pulse
 * remains the fallback for services that never deliver RMS.
 */
@Composable
private fun LiveVoiceWave(modifier: Modifier = Modifier, amplitude: Float) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val barWidth = VOICE_WAVE_BAR_DP.dp.toPx()
        val gap = VOICE_WAVE_GAP_DP.dp.toPx()
        val canvasHeight = size.height
        for (i in 0 until VOICE_WAVE_BARS) {
            val factor = VOICE_WAVE_SHAPE[i]
            // 4..24 dp equivalent band, expressed against the canvas
            // height so the wave scales with any smartbar height.
            val barHeight = ((4f + 20f * amplitude * factor) / 26f) * canvasHeight
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), (canvasHeight - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
