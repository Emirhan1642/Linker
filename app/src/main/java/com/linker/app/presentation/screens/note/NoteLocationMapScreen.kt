package com.linker.app.presentation.screens.note

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Full-screen map that shows the note author's shared location.
 *
 * Uses OpenStreetMap via osmdroid — 100% free, no API key required.
 *
 * @param latitude  GPS latitude of the shared location.
 * @param longitude GPS longitude of the shared location.
 * @param placeName Human-readable city/district string.
 * @param onNavigateBack Pop this destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteLocationMapScreen(
    latitude: Double,
    longitude: Double,
    placeName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // Initialize osmdroid configuration once
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "LinkerApp/1.0"
    }

    val geoPoint = remember(latitude, longitude) { GeoPoint(latitude, longitude) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── OpenStreetMap via osmdroid ────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(geoPoint)

                    // Add marker at location
                    val marker = Marker(this).apply {
                        position = geoPoint
                        title = placeName.ifBlank { "Konum" }
                        snippet = "Paylaşılan konum"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(marker)
                }
            },
            update = { mapView ->
                if (mapView.mapCenter.latitude != geoPoint.latitude || mapView.mapCenter.longitude != geoPoint.longitude) {
                    mapView.controller.animateTo(geoPoint)
                }
            }
        )

        // ── Top gradient + back button ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .align(Alignment.TopStart)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = Color.White
            )
        }

        // ── Bottom info card ──────────────────────────────────────────────
        LocationInfoCard(
            placeName = placeName,
            latitude = latitude,
            longitude = longitude,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom info card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationInfoCard(
    placeName: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pin icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF9C27B0), Color(0xFF6A1B9A))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = placeName.ifBlank { "Konum paylaşıldı" },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude),
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )
            }

            // Live pulse dot
            LivePulseDot()
        }
    }
}

@Composable
private fun LivePulseDot() {
    var expanded by remember { mutableStateOf(false) }
    val size by animateDpAsState(
        targetValue = if (expanded) 10.dp else 7.dp,
        animationSpec = tween(700),
        label = "pulseDot"
    )
    LaunchedEffect(Unit) {
        while (true) {
            expanded = !expanded
            kotlinx.coroutines.delay(700)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF4CAF50), CircleShape)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text("Canlı", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
