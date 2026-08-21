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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.presentation.screens.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.RectF
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
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val profileImageUrl = authState?.profileImageUrl
    
    var profileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var mapZoomLevel by remember { mutableDoubleStateOf(15.0) }

    // Initialize osmdroid configuration once
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "LinkerApp/1.0"
    }

    val geoPoint = remember(latitude, longitude) { GeoPoint(latitude, longitude) }
    var searchCenter by remember { mutableStateOf(geoPoint) }
    
    // Etraftaki mekanlar state'i
    var pois by remember { mutableStateOf<List<com.linker.app.domain.model.PoiInfo>>(emptyList()) }
    
    // searchCenter değiştikçe (yani kullanıcı haritayı kaydırdıkça) Foursquare API'ye istek atılır
    // API limitlerini patlatmamak (Harita kaydırılırken saniyede 10 kere istek atmamak) için Debounce (Gecikme) eklendi!
    LaunchedEffect(searchCenter) {
        kotlinx.coroutines.delay(1200) // Harita durduktan sonra 1.2 saniye bekle
        val newPois = com.linker.app.data.remote.FoursquareApiClient.fetchNearbyPOIs(searchCenter.latitude, searchCenter.longitude, 10000)
        android.util.Log.d("NoteLocationMapScreen", "Loaded New POIs: ${newPois.size}")
        
        // Önceki çekilen mekanlarla yenileri birleştirip ID'sine göre ayıklıyoruz
        if (newPois.isNotEmpty()) {
            pois = (pois + newPois).distinctBy { it.id }
        }
    }
    
    // Profil resmini arka planda indir
    LaunchedEffect(profileImageUrl) {
        if (!profileImageUrl.isNullOrBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(profileImageUrl)
                    val connection = url.openConnection()
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val originalBitmap = BitmapFactory.decodeStream(input)
                    if (originalBitmap != null) {
                        profileBitmap = createCircularProfilePin(originalBitmap)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NoteLocationMapScreen", "Failed to load profile image", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── OpenStreetMap via osmdroid ────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    val cartoDarkMatterTileSource = org.osmdroid.tileprovider.tilesource.XYTileSource(
                        "CartoDarkMatter",
                        0, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/dark_all/")
                    )
                    setTileSource(cartoDarkMatterTileSource)
                    setMultiTouchControls(true)

                    maxZoomLevel = 20.0 // Kullanıcı haritayı daha fazla büyütebilmek istedi

                    controller.setZoom(15.0)
                    controller.setCenter(geoPoint)
                    
                    addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                            val currentMapCenter = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                            // 1 KM (1000 metre) kaydırma yapıldıysa haritada yeni mekanları arat (Arka planda)
                            if (currentMapCenter.distanceToAsDouble(searchCenter) > 1000.0) {
                                searchCenter = currentMapCenter 
                            }
                            return false
                        }
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            mapZoomLevel = event?.zoomLevel ?: 15.0
                            return false
                        }
                    })

                    // Add marker at location
                    val mainMarker = Marker(this).apply {
                        position = geoPoint
                        title = placeName.ifBlank { "Konum" }
                        snippet = "Paylaşılan konum"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        
                        // Profil resmi yüklendiyse onu pin olarak ayarla
                        profileBitmap?.let { bmp ->
                            icon = android.graphics.drawable.BitmapDrawable(context.resources, bmp)
                        }
                        infoWindow = null
                    }
                    overlays.add(mainMarker)
                }
            },
            update = { mapView ->
                // AnimateTo kaldırıldı. State update edildiğinde haritanın kendi merkezine (başlangıca) geri atmasını önlüyor.
                
                // Update POIs on the map
                // Remove old POI markers first to prevent duplicates
                mapView.overlays.removeAll { it is Marker && it.title != placeName.ifBlank { "Konum" } }
                
                // UZAKLIK-ZOOM TABANLI ÇARPIŞMA TESPİTİ (Coğrafi Mesafe - En Kararlı Yöntem)
                // Piksel projeksiyonu OSMDroid ve Compose'un asenkron update yapısından dolayı 
                // render hatalarına sebep olduğu için, GeoPoint Distance yöntemine geri dönüldü.
                val labelMinDistanceMeters = when {
                    mapZoomLevel < 13 -> 5000.0
                    mapZoomLevel < 14 -> 3000.0
                    mapZoomLevel < 15 -> 1500.0
                    mapZoomLevel < 16 -> 800.0
                    mapZoomLevel < 17 -> 400.0
                    mapZoomLevel < 18 -> 200.0
                    mapZoomLevel < 19 -> 100.0
                    else -> 40.0 
                }

                // Dot (Nokta) çarpışma alanı (Çok nokta görülmemesi için 50 metrelik devasa koruma kalkanı)
                val dotMinDistanceMeters = 50.0 

                val labelPois = mutableListOf<com.linker.app.domain.model.PoiInfo>()
                val dotPois = mutableListOf<com.linker.app.domain.model.PoiInfo>()
                
                // ÇOK DAHA KATI POPÜLERLİK BAZLI GÖRÜNÜRLÜK FİLTRESİ (Level of Detail - LoD)
                // Foursquare'den dönen liste popülerlik sırasına göredir (Index 0 = En popüler)
                // Kullanıcının harita zoom seviyesine göre listeye işlenecek MAX mekan sayısını belirliyoruz.
                val maxPoisAllowed = when {
                    mapZoomLevel < 14 -> 1    // Uzaktayken neredeyse hiçbiri gözükmez (Sadece 1 tane)
                    mapZoomLevel < 15 -> 3    // Uzak mesafede ilk 3
                    mapZoomLevel < 16 -> 6    // Mahalle girişinde ilk 6
                    mapZoomLevel < 17 -> 10   // Mahalle içinde ilk 10
                    mapZoomLevel < 18 -> 15   // Sokağa girerken ilk 15
                    mapZoomLevel < 19 -> 25   // Yakından bakarken ilk 25
                    else -> 50 // Sadece TAM (19.0+) yakınlaşınca tüm mekanlar/noktalar görülür
                }
                
                // MÜKEMMEL ÇÖZÜM: 'take(limit)' fonksiyonunu tüm liste üzerinden uygularsak,
                // Eski ve çok uzak bölgelerdeki mekanlar kotayı haksız yere dolduruyor ve 
                // yeni gittiğimiz bölgedeki popüler mekanlar 'take' sınırına takılıp ekranda çizilemiyordu!
                // Bunu engellemek için SADECE o anki EKRAN MERKEZİNE YAKIN (Örn: 5 KM) olanları filtreleyelim!
                val currentCenter = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
                val localPois = pois.filter { 
                    GeoPoint(it.latitude, it.longitude).distanceToAsDouble(currentCenter) < 5000.0 // O anki merkeze 5 KM'den yakın olanlar
                }
                
                // Şimdi Popülerlik Kotasını sadece "Bulunduğumuz Bölgedeki" mekanlara göre işletelim
                val filteredPois = localPois.take(maxPoisAllowed)
                
                for (poi in filteredPois) {
                    val pt1 = GeoPoint(poi.latitude, poi.longitude)
                    
                    // 1. Label olabilir mi?
                    var canBeLabel = true
                    for (vPoi in labelPois) {
                        val pt2 = GeoPoint(vPoi.latitude, vPoi.longitude)
                        if (pt1.distanceToAsDouble(pt2) < labelMinDistanceMeters) {
                            canBeLabel = false
                            break
                        }
                    }
                    
                    if (canBeLabel) {
                        labelPois.add(poi)
                    } else {
                        // 2. Label olamadı, Dot olabilir mi?
                        var canBeDot = true
                        val allVisible = labelPois + dotPois
                        for (vPoi in allVisible) {
                            val pt2 = GeoPoint(vPoi.latitude, vPoi.longitude)
                            if (pt1.distanceToAsDouble(pt2) < dotMinDistanceMeters) {
                                canBeDot = false
                                break
                            }
                        }
                        
                        if (canBeDot) {
                            dotPois.add(poi)
                        }
                    }
                }
                
                // Add Dot POI markers first (so they are under labels)
                dotPois.forEach { poi ->
                    val dotMarker = Marker(mapView).apply {
                        position = GeoPoint(poi.latitude, poi.longitude)
                        title = poi.name
                        snippet = poi.type.replaceFirstChar { it.uppercase() }
                        val bitmap = createGoogleMapsDotPin(poi.type, context)
                        icon = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                    }
                    mapView.overlays.add(dotMarker)
                }

                // Add Label POI markers (so they are on top)
                labelPois.forEach { poi ->
                    val poiMarker = Marker(mapView).apply {
                        position = GeoPoint(poi.latitude, poi.longitude)
                        title = poi.name
                        snippet = poi.type.replaceFirstChar { it.uppercase() }
                        // Google Maps tarzı yatay label pini
                        val bitmap = createGoogleMapsLabelPin(poi.name, poi.type, context)
                        icon = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                        
                        // İkonun tam yuvarlak merkezi(sol tarafta) olduğu için o merkezden tuttur (Anchor X = iconRadius / width, Anchor Y = 0.5f)
                        val density = context.resources.displayMetrics.density
                        val iconRadius = 8f * density
                        setAnchor(iconRadius / bitmap.width.toFloat(), 0.5f)
                        infoWindow = null
                    }
                    mapView.overlays.add(poiMarker)
                }
                mapView.invalidate()
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

// ─────────────────────────────────────────────────────────────────────────────
// Custom Canvas Drawings
// ─────────────────────────────────────────────────────────────────────────────

private fun createGoogleMapsLabelPin(name: String, category: String, context: Context): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val textSize = 12f * density
    val padding = 6f * density
    val iconRadius = 8f * density // 16dp çap
    
    // Uzun isimleri keserek etiketlerin aşırı genişlemesini önle (Ellipsize)
    val maxChars = 20
    val displayName = if (name.length > maxChars) name.substring(0, maxChars - 3).trimEnd() + "..." else name
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.textSize = textSize
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    
    // Kategoriye göre renk seçimi
    val catLower = category.lowercase()
    val bgColor = when {
        catLower.contains("restaurant") || catLower.contains("yemek") || catLower.contains("döner") || catLower.contains("food") -> "#FF9800" // Turuncu
        catLower.contains("cafe") || catLower.contains("kahve") || catLower.contains("coffee") -> "#F57C00" // Koyu Turuncu
        catLower.contains("store") || catLower.contains("market") || catLower.contains("shop") -> "#1976D2" // Mavi
        catLower.contains("health") || catLower.contains("veteriner") || catLower.contains("klinik") || catLower.contains("hospital") -> "#E91E63" // Pembe
        else -> "#9C27B0" // Varsayılan Mor
    }
    
    // Yazı genişliği hesaplama
    val textWidth = paint.measureText(displayName)
    val textBounds = android.graphics.Rect()
    paint.getTextBounds(displayName, 0, displayName.length, textBounds)
    val textHeight = textBounds.height()
    
    // Toplam resim boyutu (Yarı saydam siyah bir background hapı)
    val height = (iconRadius * 2).coerceAtLeast(textSize + padding * 2).toInt()
    val width = (iconRadius * 2 + padding + textWidth + padding).toInt()
    
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Önce ismin okunabilmesi için çok hafif saydam siyah hap şeklinde arkaplan çizelim (Dark Mode'a uygun)
    paint.color = android.graphics.Color.parseColor("#A6000000") // %65 saydam siyah
    val bgRect = RectF(iconRadius, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(bgRect, height/2f, height/2f, paint)
    
    // Sol taraftaki ikon yuvarlağı
    paint.color = android.graphics.Color.parseColor(bgColor)
    canvas.drawCircle(iconRadius, height / 2f, iconRadius, paint)
    
    // İçine küçük beyaz nokta veya kategori baş harfi
    paint.color = android.graphics.Color.parseColor("#202020") // Çok koyu gri (iç ikon niyetine)
    canvas.drawCircle(iconRadius, height / 2f, iconRadius / 3f, paint)
    
    // İsmi çiz
    paint.color = android.graphics.Color.parseColor("#E0E0E0") // Açık gri/beyaz okunaklı metin
    val textY = (height / 2f) + (textHeight / 2f) - (1f * density)
    canvas.drawText(displayName, iconRadius * 2 + padding, textY, paint)
    
    return bitmap
}

private fun createGoogleMapsDotPin(category: String, context: Context): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val radius = 5f * density // 10dp çap (Dot)
    
    val bitmap = android.graphics.Bitmap.createBitmap(
        (radius * 2).toInt(),
        (radius * 2).toInt(),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    val catLower = category.lowercase()
    val bgColor = when {
        catLower.contains("restaurant") || catLower.contains("yemek") || catLower.contains("döner") || catLower.contains("food") -> "#FF9800" // Turuncu
        catLower.contains("cafe") || catLower.contains("kahve") || catLower.contains("coffee") -> "#F57C00" // Koyu Turuncu
        catLower.contains("store") || catLower.contains("market") || catLower.contains("shop") -> "#1976D2" // Mavi
        catLower.contains("health") || catLower.contains("veteriner") || catLower.contains("klinik") || catLower.contains("hospital") -> "#E91E63" // Pembe
        else -> "#9C27B0" // Varsayılan Mor
    }
    
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius, paint)
    
    paint.color = android.graphics.Color.parseColor(bgColor)
    canvas.drawCircle(radius, radius, radius - (1f * density), paint)
    
    return bitmap
}

private fun createCircularProfilePin(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
    val size = 110 // Biraz daha büyük (Glow için)
    val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    val center = size / 2f
    
    // Dış Yarı Saydam Aura (Glow)
    paint.color = android.graphics.Color.parseColor("#404285F4") // %25 saydam Google Blue
    canvas.drawCircle(center, center, center, paint)
    
    // Beyaz Çerçeve
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, center * 0.7f, paint)
    
    // Google Mavi Çerçeve
    paint.color = android.graphics.Color.parseColor("#4285F4")
    canvas.drawCircle(center, center, center * 0.65f, paint)
    
    // Resim Dairesi (Kesilmiş)
    val innerSize = (size * 0.55f).toInt()
    val srcWidth = bitmap.width
    val srcHeight = bitmap.height
    val minSide = kotlin.math.min(srcWidth, srcHeight)
    val croppedBitmap = android.graphics.Bitmap.createBitmap(bitmap, (srcWidth - minSide) / 2, (srcHeight - minSide) / 2, minSide, minSide)
    
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, innerSize, innerSize, false)
    val bitmapShader = android.graphics.BitmapShader(scaledBitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    paint.shader = bitmapShader
    
    canvas.translate(center - innerSize/2f, center - innerSize/2f)
    canvas.drawCircle(innerSize / 2f, innerSize / 2f, innerSize / 2f, paint)
    
    return output
}
