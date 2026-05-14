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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
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
    onFinish: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var detectedCommune by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (com.eventos.banana.util.LocationHelper.hasLocationPermissions(context)) {
            val helper = com.eventos.banana.util.LocationHelper(context)
            val result = helper.detectLocationFull()
            if (result != null) {
                detectedCommune = result.commune
            }
        }
    }

    val locationDescription = if (detectedCommune != null) {
        "Banana usa tu ubicación para mostrarte eventos cerca de $detectedCommune. ¡Ve los panoramas más cercanos primero!"
    } else {
        stringResource(com.eventos.banana.R.string.onboarding_desc_4)
    }

    // 4 Slides: Events, Security, Ratings, Location
    val pages = listOf(
        OnboardingPage(
            title = stringResource(com.eventos.banana.R.string.onboarding_title_1),
            description = stringResource(com.eventos.banana.R.string.onboarding_desc_1),
            icon = Icons.Filled.DateRange
        ),
        OnboardingPage(
            title = stringResource(com.eventos.banana.R.string.onboarding_title_2),
            description = stringResource(com.eventos.banana.R.string.onboarding_desc_2),
            icon = Icons.Filled.CheckCircle
        ),
        OnboardingPage(
            title = stringResource(com.eventos.banana.R.string.onboarding_title_3),
            description = stringResource(com.eventos.banana.R.string.onboarding_desc_3),
            icon = Icons.Filled.Star
        ),
        OnboardingPage(
            title = stringResource(com.eventos.banana.R.string.onboarding_title_4),
            description = locationDescription,
            icon = Icons.Filled.LocationOn
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    // 🛡️ PERMISSIONS LAUNCHER
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Proceed regardless of result (app must handle denial)
        onFinish()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
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
                TextButton(onClick = { 
                    // Skip onboarding without asking permissions (User opted out of intro)
                    onFinish()
                }) {
                    Text(stringResource(com.eventos.banana.R.string.common_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(
                    page = pages[pageIndex],
                    detectedCommune = if (pageIndex == 3) detectedCommune else null
                )
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
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(10.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Button
            com.eventos.banana.ui.components.BananaButton(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        // 🚀 ASK PERMISSIONS ON FINAL SLIDE
                         val permissions = mutableListOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                },
                text = if (pagerState.currentPage == pages.size - 1) stringResource(com.eventos.banana.R.string.onboarding_enable_start) else stringResource(com.eventos.banana.R.string.common_next)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage, detectedCommune: String? = null) {
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
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (page.icon == Icons.Filled.LocationOn) {
            if (detectedCommune != null) {
                Text(
                    text = "📍 Eventos cerca de $detectedCommune aparecerán primero",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    text = "📍 Activa tu ubicación para ver eventos de tu comuna",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)
