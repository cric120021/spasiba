package app.spasiba.nativeapp2.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SilverBrandHeader(
    modifier: Modifier = Modifier,
    subtitle: String? = "Music tailored for your flow"
) {
    Column(modifier = modifier) {
        Text(
            text = "MUSIC",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 2.4.sp
            )
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Spasiba",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontSize = 26.sp,
                letterSpacing = (-0.5).sp
            )
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp
                )
            )
        }
    }
}
