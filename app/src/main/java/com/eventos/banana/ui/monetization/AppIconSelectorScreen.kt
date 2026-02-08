package com.eventos.banana.ui.monetization

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventos.banana.R
import com.eventos.banana.util.AppIconManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSelectorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentIcon by remember { mutableStateOf(AppIconManager.getCurrentIcon(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Icono de App") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            Text(
                "Personaliza tu Banana",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Elige cómo se ve la app en tu teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(24.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(AppIconManager.AppIcon.values()) { icon ->
                    AppIconItem(
                        icon = icon,
                        isSelected = icon == currentIcon,
                        onClick = {
                            if (icon != currentIcon) {
                                AppIconManager.setIcon(context, icon)
                                currentIcon = icon
                                // Note: App might close/restart here depending on OS
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppIconItem(
    icon: AppIconManager.AppIcon,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Map Res Strings to Drawables Helper (Quick hack or look up by logic)
    // Since we don't have direct R.drawable references in the Enum (can't ref R from logic easily without context sometimes or if dynamic),
    // we'll map them here manually for safety.
    
    // NOTE: In a real app we'd pass the resource ID in the enum, but `AppIconManager` is in `util` package 
    // and might not have access to R if logic is separated. But here we are in UI.
    // Let's assume we can find them.
    
    val context = LocalContext.current
    val resId = remember(icon) {
        context.resources.getIdentifier(icon.iconResName, "mipmap", context.packageName)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (resId != 0) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = icon.displayName,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                 Text("?", fontSize = 48.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                icon.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Text(
                    "Activo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
