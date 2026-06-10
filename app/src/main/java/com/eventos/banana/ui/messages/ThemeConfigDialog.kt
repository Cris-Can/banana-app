package com.eventos.banana.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.ConversationTheme

@Composable
fun ThemeConfigDialog(
    currentTheme: ConversationTheme,
    onSave: (ConversationTheme) -> Unit,
    onDismiss: () -> Unit
) {
    var primaryColor by remember { mutableStateOf(currentTheme.primaryColor) }
    var secondaryColor by remember { mutableStateOf(currentTheme.secondaryColor) }
    var backgroundColor by remember { mutableStateOf(currentTheme.backgroundColor) }
    
    var headerStyle by remember { mutableStateOf(currentTheme.headerStyle) }
    var inputBarStyle by remember { mutableStateOf(currentTheme.inputBarStyle) }
    var separatorStyle by remember { mutableStateOf(currentTheme.separatorStyle) }
    
    var bubbleAnimation by remember { mutableStateOf(currentTheme.bubbleAnimation) }
    var bubbleShadow by remember { mutableStateOf(currentTheme.bubbleShadow) }
    var typingStyle by remember { mutableStateOf(currentTheme.typingStyle) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("🎨 Paleta", "🧱 Decoración", "✨ Efectos")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Tema") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())) {
                    when (selectedTabIndex) {
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ColorPickerRow("Color de tus burbujas", primaryColor) { primaryColor = it }
                                ColorPickerRow("Color de sus burbujas", secondaryColor) { secondaryColor = it }
                                ColorPickerRow("Color de fondo", backgroundColor) { backgroundColor = it }
                            }
                        }
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                RadioSection("Header", listOf("Gradient" to "gradient", "Sólido" to "default", "Minimal" to "minimal"), headerStyle) { headerStyle = it }
                                RadioSection("Input", listOf("Bordeada" to "default", "Rellena" to "filled", "Invisible" to "invisible"), inputBarStyle) { inputBarStyle = it }
                                RadioSection("Separador", listOf("Ninguno" to "none", "Línea" to "solid", "Punteado" to "dotted"), separatorStyle) { separatorStyle = it }
                            }
                        }
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                RadioSection("Animación burbujas", listOf("Slide" to "slide", "Fade" to "fade", "Scale" to "scale"), bubbleAnimation) { bubbleAnimation = it }
                                RadioSection("Sombra burbujas", listOf("Sin" to "none", "Suave" to "soft", "Fuerte" to "strong"), bubbleShadow) { bubbleShadow = it }
                                RadioSection("Typing indicator", listOf("Dots" to "dots", "Pulse" to "pulse", "Clásico" to "classic"), typingStyle) { typingStyle = it }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ConversationTheme(
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        backgroundColor = backgroundColor,
                        headerStyle = headerStyle,
                        inputBarStyle = inputBarStyle,
                        separatorStyle = separatorStyle,
                        bubbleAnimation = bubbleAnimation,
                        bubbleShadow = bubbleShadow,
                        typingStyle = typingStyle
                    )
                )
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ColorPickerRow(label: String, selectedColor: String?, onColorSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(selectedColor?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(e:Exception) { Color.Transparent } } ?: Color.Transparent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
        ThemeColorGrid(selectedColor = selectedColor, onColorSelected = onColorSelected)
    }
}

@Composable
private fun RadioSection(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selected == value,
                    onClick = { onSelect(value) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
