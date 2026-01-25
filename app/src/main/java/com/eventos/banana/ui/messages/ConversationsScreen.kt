package com.eventos.banana.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Conversation
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    currentUserId: String,
    onConversationClick: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensajes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No tienes conversaciones aún", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(conversations) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        currentUserId = currentUserId,
                        onClick = { onConversationClick(conversation.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    currentUserId: String,
    onClick: () -> Unit
) {
    val otherUserId = conversation.participants.firstOrNull { it != currentUserId } ?: ""
    
    // Fallback info from conversation doc
    val storedNickname = conversation.participantNicknames[otherUserId] ?: "Usuario"
    
    // Real-time fetch (optional optimization: cache this in ViewModel)
    var displayNickname by remember { mutableStateOf(storedNickname) }
    
    // Only if it looks like a generic name or UID, try to fetch fresh
    if (storedNickname == "Usuario" || storedNickname == otherUserId) {
         val context = androidx.compose.ui.platform.LocalContext.current
         LaunchedEffect(otherUserId) {
             // Quick fetch from Firestore (simple direct call)
             try {
                 val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                 val doc = db.collection("users").document(otherUserId).get().await()
                 val nick = doc.getString("nickname")
                 if (!nick.isNullOrBlank()) {
                     displayNickname = nick
                 }
             } catch (e: Exception) {
                 // ignore
             }
         }
    } else {
        displayNickname = storedNickname
    }

    val unreadCount = conversation.unreadCount[currentUserId] ?: 0
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    displayNickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
                
                if (unreadCount > 0) {
                    Badge { Text(unreadCount.toString()) }
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
