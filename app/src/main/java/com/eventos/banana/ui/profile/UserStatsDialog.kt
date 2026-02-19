package com.eventos.banana.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape // 🆕
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.UserProfile

@Composable
fun UserStatsDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture
                AsyncImage(
                    model = userProfile.profilePictureUrl ?: "https://via.placeholder.com/150",
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Nickname
                Text(
                    text = userProfile.nickname,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                // Reputación Badge (Existing)
                Text(
                    text = "${userProfile.getRatingBadge()} ${userProfile.localizedBadgeText()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(Modifier.height(24.dp))
                
                // 📊 STATS ATTENDANCE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = stringResource(com.eventos.banana.R.string.stats_requested),
                        value = userProfile.eventsRequestedCount.toString(),
                        icon = "📩"
                    )
                    StatItem(
                        label = stringResource(com.eventos.banana.R.string.stats_attended),
                        value = userProfile.eventsAttendedCount.toString(),
                        icon = "🎫"
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Reliability % (Calculated)
                val reliability = if (userProfile.eventsRequestedCount > 0) {
                    (userProfile.eventsAttendedCount.toFloat() / userProfile.eventsRequestedCount.toFloat() * 100).toInt()
                } else {
                    0
                }
                
                if (userProfile.eventsRequestedCount > 0) {
                    Text(
                        text = stringResource(com.eventos.banana.R.string.stats_attendance_rate, reliability),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (reliability >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(onClick = onDismiss) {
                    Text(stringResource(com.eventos.banana.R.string.common_close))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
