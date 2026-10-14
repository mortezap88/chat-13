package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LinguisticCorrection

@Composable
fun CorrectionDialog(
    correction: LinguisticCorrection,
    isDarkTheme: Boolean = true,
    onApply: (LinguisticCorrection) -> Unit,
    onDismiss: () -> Unit
) {
    val labelColor = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val reasonColor = if (isDarkTheme) Color(0xFFD4D4D8) else Color(0xFF3F3F46)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, if (isDarkTheme) Color.White else Color(0xFF18181B), RoundedCornerShape(16.dp)),
        title = null,   // no "Grammar Suggestion" / "Word Choice" chip anymore
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Original:" block removed entirely — only fix + reason remain
                Column {
                    Text("Suggested:", fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                    Text(text = correction.suggested, fontSize = 15.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Reason:", fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                    Text(text = correction.reason, fontSize = 12.5.sp, color = reasonColor, lineHeight = 16.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(correction) },
                colors = ButtonDefaults.buttonColors(containerColor = if (isDarkTheme) Color.White else Color(0xFF18181B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null,
                     tint = if (isDarkTheme) Color.Black else Color.White,
                     modifier = Modifier.padding(end = 4.dp))
                Text("Apply Fix", color = if (isDarkTheme) Color.Black else Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ignore", color = labelColor) }
        },
        containerColor = if (isDarkTheme) Color.Black else Color.White,  // black body, narrow white frame
        shape = RoundedCornerShape(16.dp)
    )
}
