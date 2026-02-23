package com.eventos.banana.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventos.banana.ui.onboarding.GuideStep
import com.eventos.banana.ui.onboarding.GuideViewModel

@Composable
fun GuideOverlay(
    viewModel: GuideViewModel
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isVisible by viewModel.isVisible.collectAsState()

    AnimatedVisibility(
        visible = isVisible && currentStep != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomCenter) // Position at bottom
            .padding(16.dp)
            .padding(bottom = 80.dp) // Avoid overlapping bottom nav if present
    ) {
        val step = currentStep ?: return@AnimatedVisibility

        val (title, description) = when (step) {
            GuideStep.HOME_WELCOME -> stringResource(com.eventos.banana.R.string.guide_welcome_title) to stringResource(com.eventos.banana.R.string.guide_welcome_desc)
            GuideStep.HOME_FILTERS -> stringResource(com.eventos.banana.R.string.guide_filters_title) to stringResource(com.eventos.banana.R.string.guide_filters_desc)
            GuideStep.NAV_TO_CREATE -> stringResource(com.eventos.banana.R.string.guide_create_title) to stringResource(com.eventos.banana.R.string.guide_create_desc)
            GuideStep.CREATE_EXPLAIN -> stringResource(com.eventos.banana.R.string.guide_form_title) to stringResource(com.eventos.banana.R.string.guide_form_desc)
            GuideStep.NAV_TO_PROFILE -> stringResource(com.eventos.banana.R.string.guide_profile_title) to stringResource(com.eventos.banana.R.string.guide_profile_desc)
            GuideStep.PROFILE_EXPLAIN -> stringResource(com.eventos.banana.R.string.guide_achievements_title) to stringResource(com.eventos.banana.R.string.guide_achievements_desc)
            GuideStep.FINISH -> stringResource(com.eventos.banana.R.string.guide_finish_title) to stringResource(com.eventos.banana.R.string.guide_finish_desc)
        }

        val buttonText = when (step) {
            GuideStep.NAV_TO_CREATE -> stringResource(com.eventos.banana.R.string.guide_go_create)
            GuideStep.NAV_TO_PROFILE -> stringResource(com.eventos.banana.R.string.guide_go_profile)
            GuideStep.FINISH -> stringResource(com.eventos.banana.R.string.guide_start)
            else -> stringResource(com.eventos.banana.R.string.guide_next)
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${step.ordinal + 1}/${GuideStep.values().size}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Description
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { viewModel.skipGuide() }) {
                        Text(stringResource(com.eventos.banana.R.string.guide_skip))
                    }
                    Button(onClick = { viewModel.nextStep() }) {
                        Text(buttonText)
                    }
                }
            }
        }
    }
}
