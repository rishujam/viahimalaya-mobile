package com.via.himalaya.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.via.himalaya.data.models.Trek

/**
 * Featured Trek Card - Large card with hero image overlay design
 * Based on the Figma design with gradient overlay and information displayed on image
 */
@Composable
fun TrekCard(
    trek: Trek,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .height(280.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary
                        )
                    )
                ),
            verticalArrangement = Arrangement.Bottom
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    maxLines = 1,
                    text = trek.name,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    maxLines = 1,
                    text = trek.location,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Preview
@Composable
fun TrekCardPreview() {
    CarouselTrekCard(
        isFocused = true,
        trek = Trek(
            id = "1",
            name = "Trek Name",
            location = "Location",
            distance = "10 km",
            elevation = "2000 m",
            boundingBox = emptyList(),
            coordinateUrl = ""
        ),
        onClick = {},
        cardHeight = 600.dp
    )
}

@Composable
fun CarouselTrekCard(
    trek: Trek,
    isFocused: Boolean,
    onClick: () -> Unit,
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    // Animation specification for all transitions
    val animSpec = tween<Dp>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )
    
    // Animated horizontal padding (0dp when focused, 16dp when unfocused)
    val targetPadding = if (isFocused) 0.dp else 16.dp
    val animatedPadding by animateDpAsState(
        targetValue = targetPadding,
        animationSpec = animSpec,
        label = "HorizontalPadding"
    )
    
    // Animated blur (0dp when focused, 8dp when unfocused)
    val targetBlur = if (isFocused) 0.dp else 8.dp
    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = animSpec,
        label = "BlurAnimation"
    )
    
    // Animated alpha (1.0 when focused, 0.6 when unfocused)
    val targetAlpha = if (isFocused) 1f else 0.6f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "AlphaAnimation"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = animatedPadding, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .graphicsLayer {
                alpha = animatedAlpha
            }
            .blur(animatedBlur)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(20.dp))
        ) {
            // Background Image with blurred loading placeholder
            SubcomposeAsyncImage(
                model = trek.imageUrl,
                contentDescription = trek.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            ) {
                val state = painter.state
                if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray)
                            .blur(10.dp)
                    )
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
            
            // Gradient overlay for better text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            
            // Text content on top
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    text = trek.name,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    lineHeight = 34.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    maxLines = 1,
                    text = trek.location,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}