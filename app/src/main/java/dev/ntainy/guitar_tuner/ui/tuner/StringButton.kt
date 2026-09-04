package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.target

val STRING_BUTTON_SIZE = 56.dp

/** Smallest the headstock may shrink a button to when six of them must share a short column. */
val MIN_STRING_BUTTON_SIZE = 30.dp

/** Corner radius as a percentage of the button: a circle at rest, squared off while it is the target. */
private const val CORNER_IDLE_PERCENT = 50f
private const val CORNER_TARGET_PERCENT = 32f

enum class StringButtonState {
    IDLE,
    TARGET,
    TUNED,
    TARGET_TUNED,
    ;

    val isTuned: Boolean get() = this == TUNED || this == TARGET_TUNED

    val isTarget: Boolean get() = this == TARGET || this == TARGET_TUNED

    companion object {
        fun of(isTarget: Boolean, isTuned: Boolean): StringButtonState = when {
            isTarget && isTuned -> TARGET_TUNED
            isTarget -> TARGET
            isTuned -> TUNED
            else -> IDLE
        }
    }
}

/**
 * 56 dp button for one string (smaller when the parent constrains it): outline ring at rest, brass ring + brass
 * text for the target, mint ring + text and a check badge when tuned; target and tuned together keeps the brass
 * text under a mint ring. [dimmed] fades the button, used for the strings that are not pinned in manual mode.
 *
 * The button is a circle at rest and springs to a squared-off shape while it is the target, so the live string is
 * distinguishable by silhouette and not by colour alone. [onLongClick] plays the string's reference tone.
 */
@Composable
fun StringButton(
    label: String,
    state: StringButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalTunerHaptics.current
    val ringTarget = when (state) {
        StringButtonState.IDLE -> scheme.outline
        StringButtonState.TARGET -> scheme.target
        StringButtonState.TUNED, StringButtonState.TARGET_TUNED -> scheme.inTune
    }
    val textTarget = when (state) {
        StringButtonState.IDLE -> scheme.onSurface
        StringButtonState.TARGET, StringButtonState.TARGET_TUNED -> scheme.target
        StringButtonState.TUNED -> scheme.inTune
    }
    val ringColor by animateColorAsState(ringTarget, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "ring")
    val textColor by animateColorAsState(textTarget, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "text")
    val alpha by animateFloatAsState(if (dimmed) 0.38f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dim")
    val cornerPercent by animateFloatAsState(
        targetValue = if (state.isTarget) CORNER_TARGET_PERCENT else CORNER_IDLE_PERCENT,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "corner",
    )
    val shape = RoundedCornerShape(percent = cornerPercent.toInt())
    val octave = label.takeLastWhile { it.isDigit() }
    val letter = label.dropLast(octave.length)

    BoxWithConstraints(modifier = modifier.size(STRING_BUTTON_SIZE).alpha(alpha)) {
        val compact = maxWidth < 48.dp
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(scheme.surfaceContainer)
                .border(if (compact) 1.5.dp else 2.dp, ringColor, shape)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick?.let {
                        {
                            haptics.longPress()
                            it()
                        }
                    },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buildAnnotatedString {
                    append(letter)
                    withStyle(SpanStyle(fontSize = if (compact) 9.sp else 12.sp, baselineShift = BaselineShift(0.35f))) {
                        append(octave)
                    }
                },
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (state.isTuned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(if (compact) 14.dp else 18.dp)
                    .background(scheme.inTune, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.onTertiary,
                    modifier = Modifier.size(if (compact) 9.dp else 12.dp),
                )
            }
        }
    }
}
