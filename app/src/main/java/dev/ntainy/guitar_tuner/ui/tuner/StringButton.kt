package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.target

val STRING_BUTTON_SIZE = 56.dp

enum class StringButtonState {
    IDLE,
    TARGET,
    TUNED,
    TARGET_TUNED,
    ;

    val isTuned: Boolean get() = this == TUNED || this == TARGET_TUNED

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
 * 56 dp circle for one string: outline ring at rest, brass ring + brass text for the target, mint ring + text and a
 * check badge when tuned; target and tuned together keeps the brass text under a mint ring.
 */
@Composable
fun StringButton(
    label: String,
    state: StringButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
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
    val ringColor by animateColorAsState(ringTarget, label = "ring")
    val textColor by animateColorAsState(textTarget, label = "text")
    val octave = label.takeLastWhile { it.isDigit() }
    val letter = label.dropLast(octave.length)

    Box(modifier = modifier.size(STRING_BUTTON_SIZE)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(scheme.surfaceContainer)
                .border(2.dp, ringColor, CircleShape)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                .clickable(onClick = onClick, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buildAnnotatedString {
                    append(letter)
                    withStyle(SpanStyle(fontSize = 12.sp, baselineShift = BaselineShift(0.35f))) { append(octave) }
                },
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (state.isTuned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(scheme.inTune, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.onTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
