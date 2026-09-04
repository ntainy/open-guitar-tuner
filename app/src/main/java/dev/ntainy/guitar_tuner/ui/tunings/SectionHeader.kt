package dev.ntainy.guitar_tuner.ui.tunings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/** Group label shared by the tunings list and the settings sections: labelLarge, brass, letter-spaced caps. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}
