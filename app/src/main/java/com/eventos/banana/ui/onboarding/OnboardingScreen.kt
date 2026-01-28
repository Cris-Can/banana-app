package com.eventos.banana.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: (Boolean) -> Unit
) {
    // 3 Slides: Events, Security, Ratings
    val pages = listOf(
        OnboardingPage(
            title = "Eventos Exclusivos",
            description = "Descubre panoramas únicos cerca de ti o crea los tuyos en segundos. ¡Sin complicaciones!",
            icon = Icons.Filled.DateRange
        ),
        OnboardingPage(
            title = "Comunidad Real",
            description = "Solo perfiles verdaderos. Tu seguridad es nuestra prioridad para que disfrutes con tranquilidad.",
            icon = Icons.Filled.CheckCircle
        ),
        OnboardingPage(
            title = "Tu Reputación Vale",
            description = "Sé un buen asistente, recibe calificaciones y destaca como un 'Top Banana' en tu ciudad.",
            icon = Icons.Filled.Star
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var dontShowAgain by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onFinish(dontShowAgain) }) {
                    Text("Omitir")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            Spacer(modifier = Modifier.weight(1f))

            // Indicators
            Row(
                Modifier
                    .height(50.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(10.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Checkbox "No volver a mostrar"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it }
                )
                Text(
                    text = "No volver a mostrar esta guía",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button
            com.eventos.banana.ui.components.BananaButton(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinish(dontShowAgain)
                    }
                },
                text = if (pagerState.currentPage == pages.size - 1) "Comenzar" else "Siguiente"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)
