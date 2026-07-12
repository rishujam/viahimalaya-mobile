package com.via.himalaya.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialException
import com.via.himalaya.R
import com.via.himalaya.auth.getGoogleIdToken
import com.via.himalaya.presentation.auth.AuthViewModel
import com.via.himalaya.ui.MyApplicationTheme
import kotlinx.coroutines.launch

@Composable
fun SignInScreenRoot(
    viewModel: AuthViewModel,
    onSignedIn: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.userProfile) {
        if (state.userProfile?.email != null) onSignedIn()
    }
//    LaunchedEffect(state.errorMessage) {
//        state.errorMessage?.let { message ->
//            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
//            viewModel.consumeError()
//        }
//    }

    SignInScreen(
        isLoading = state.isLoading,
        onGoogleSignInClick = {
            scope.launch {
                try {
                    val idToken = getGoogleIdToken(context)
                    viewModel.onGoogleIdToken(idToken)
                } catch (e: GetCredentialException) {
                    viewModel.onSignInFailed(e.message ?: "Sign-in was cancelled.")
                } catch (e: Exception) {
                    viewModel.onSignInFailed(e.message ?: "Sign-in failed.")
                }
            }
        }
    )
}

@Composable
fun SignInScreen(
    onGoogleSignInClick: () -> Unit = {},
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(54.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = painterResource(id = R.drawable.bg_signin),
                contentDescription = "Sign In Background",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(bottom = 36.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            LogoBadge()
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "ViaHimalaya",
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Offline trail maps for the Himalayas",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.weight(1f))

        GoogleSignInButton(onClick = onGoogleSignInClick, isLoading = isLoading)

        Spacer(Modifier.height(16.dp))

        Text(
            text = "By continuing, you agree to our Terms & Privacy Policy.",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFFAAA89E),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun GoogleSignInButton(onClick: () -> Unit, isLoading: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = !isLoading) { onClick() },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                GoogleLogo(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Continue with Google",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LogoBadge() {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = Color(0xFF1C2416),
        shadowElevation = 6.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_foreground),
                contentDescription = "ViaHimalaya Logo",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun DrawScope.pin(cx: Float, cy: Float, color: Color, scale: Float) {
    drawCircle(Color.White, radius = 11f * scale, center = Offset(cx, cy))
    drawCircle(color, radius = 6f * scale, center = Offset(cx, cy))
}

/** Authentic multicolor Google "G" drawn from the official 18×18 paths. */
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    val blue = Color(0xFF4285F4)
    val green = Color(0xFF34A853)
    val yellow = Color(0xFFFBBC05)
    val red = Color(0xFFEA4335)

    val segments = listOf(
        blue to "M17.64 9.2045c0-.6381-.0573-1.2518-.1636-1.8409H9v3.4814h4.8436c-.2086 1.125-.8427 2.0782-1.7959 2.7164v2.2581h2.9087c1.7018-1.5668 2.6836-3.874 2.6836-6.615z",
        green to "M9 18c2.43 0 4.4673-.806 5.9564-2.1818l-2.9087-2.2581c-.8059.54-1.8368.859-3.0477.859-2.344 0-4.3282-1.5831-5.036-3.7104H.9574v2.3318C2.4382 15.9832 5.4818 18 9 18z",
        yellow to "M3.964 10.71c-.18-.54-.2823-1.1168-.2823-1.71s.1023-1.17.2823-1.71V4.9582H.9573C.3477 6.1732 0 7.5477 0 9s.3477 2.8268.9573 4.0418L3.964 10.71z",
        red to "M9 3.5795c1.3214 0 2.5077.4541 3.4405 1.346l2.5813-2.5814C13.4632.8918 11.426 0 9 0 5.4818 0 2.4382 2.0168.9573 4.9582L3.964 7.29C4.6718 5.1627 6.656 3.5795 9 3.5795z"
    ).map { (color, data) -> color to PathParser().parsePathString(data).toPath() }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 18f
        scale(s, s, pivot = Offset.Zero) {
            segments.forEach { (color, path) -> drawPath(path, color) }
        }
    }
}

@Preview
@Composable
private fun SignInScreenPreview() {
    MyApplicationTheme {
        SignInScreen()
    }
}
