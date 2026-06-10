package com.eventos.banana.ui.monetization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventos.banana.ui.theme.PanoramasGold
import com.eventos.banana.ui.monetization.BillingViewModel
import androidx.compose.ui.res.stringResource
import com.eventos.banana.data.repository.BillingRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BananaGoldScreen(
    billingViewModel: BillingViewModel,
    onDismiss: () -> Unit,
    onNavigateToIcons: () -> Unit
) {
    val productDetails by billingViewModel.productDetails.collectAsState()
    val goldProduct = productDetails[BillingRepository.SUB_BANANA_GOLD]
    
    // Fallback price if loading or offline
    val priceText = goldProduct?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: stringResource(com.eventos.banana.R.string.banana_gold_fallback_price)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(com.eventos.banana.R.string.common_close), tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // 👑 ICON
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PanoramasGold, Color(0xFFC5A000))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🍌", fontSize = 48.sp)
            }
            
            Spacer(Modifier.height(24.dp))
            
            // TITLE
            Text(
                stringResource(com.eventos.banana.R.string.banana_gold_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = PanoramasGold
                )
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                stringResource(com.eventos.banana.R.string.banana_gold_subtitle),
                style = MaterialTheme.typography.titleMedium.copy(color = Color.Gray)
            )
            
            Spacer(Modifier.height(40.dp))
            
            // BENEFITS
            BenefitItem("🚀", stringResource(com.eventos.banana.R.string.banana_gold_fast_pass_title), stringResource(com.eventos.banana.R.string.banana_gold_fast_pass_desc))
            Spacer(Modifier.height(16.dp))
            BenefitItem("👀", stringResource(com.eventos.banana.R.string.banana_gold_views_title), stringResource(com.eventos.banana.R.string.banana_gold_views_desc))
            Spacer(Modifier.height(16.dp))
            BenefitItem(
                "🎨", 
                stringResource(com.eventos.banana.R.string.banana_gold_icons_title), 
                stringResource(com.eventos.banana.R.string.banana_gold_icons_desc),
                onClick = onNavigateToIcons
            )
            Spacer(Modifier.height(16.dp))
            BenefitItem("💬", stringResource(com.eventos.banana.R.string.banana_gold_themes_title), stringResource(com.eventos.banana.R.string.banana_gold_themes_desc))
            
            Spacer(Modifier.weight(1f))
            
            // PRICE
            Text(
                priceText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            
            Text(
                stringResource(com.eventos.banana.R.string.banana_gold_cancel_anytime),
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
            
            Spacer(Modifier.height(24.dp))
            
            // CTA
            val context = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = { 
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        billingViewModel.buyGold(activity)
                    } else {
                        android.util.Log.e("PanoramasGold", "Activity context not found")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanoramasGold,
                    contentColor = Color.Black
                ),
                enabled = goldProduct != null 
            ) {
                Text(
                    stringResource(com.eventos.banana.R.string.banana_gold_cta_button),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            
            if (goldProduct == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(com.eventos.banana.R.string.banana_gold_loading_price),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }
        }
    }
}

@Composable
fun BenefitItem(icon: String, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
        }
    }
}
