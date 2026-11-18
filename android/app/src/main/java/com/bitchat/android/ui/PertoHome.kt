package com.bitchat.android.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.core.ui.icon.BitChatIcon
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.ui.theme.ThemePreference
import com.bitchat.android.ui.theme.ThemePreferenceManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Perto: tela inicial com as abas Conversas, Perto (radar) e Mural.
// A rede Bluetooth, a criptografia e as conversas continuam sendo as do bitchat;
// aqui só muda como tudo aparece pra pessoa.
// ─────────────────────────────────────────────────────────────────────────────

private val Laranja = Color(0xFFEF9F27)
private val LaranjaEscuro = Color(0xFF412402)
private const val ALERTA_PREFIXO = "🚨 ALERTA:"

private enum class PertoTab(val label: String) { Conversas("Conversas"), Perto("Perto"), Mural("Mural") }

/** Cor da barra de cima e do radar, conforme o tema escolhido. */
private fun barColor(accent: PertoAccent) = when (accent) {
    PertoAccent.Roxo -> Color(0xFF3C3489)
    PertoAccent.VerdeAgua -> Color(0xFF085041)
    PertoAccent.Coral -> Color(0xFF993C1D)
}

private fun radarColor(accent: PertoAccent) = when (accent) {
    PertoAccent.Roxo -> Color(0xFF26215C)
    PertoAccent.VerdeAgua -> Color(0xFF04342C)
    PertoAccent.Coral -> Color(0xFF4A1B0C)
}

private fun radarRing(accent: PertoAccent) = when (accent) {
    PertoAccent.Roxo -> Color(0xFF7F77DD)
    PertoAccent.VerdeAgua -> Color(0xFF1D9E75)
    PertoAccent.Coral -> Color(0xFFD85A30)
}

/** Cores de avatar estáveis por pessoa (o mesmo nome sempre ganha a mesma cor). */
private val AvatarColors = listOf(
    Color(0xFF9FE1CB) to Color(0xFF04342C),
    Color(0xFFF5C4B3) to Color(0xFF4A1B0C),
    Color(0xFFFAC775) to Color(0xFF412402),
    Color(0xFFCECBF6) to Color(0xFF26215C),
    Color(0xFFF4C0D1) to Color(0xFF4B1528),
    Color(0xFFB5D4F4) to Color(0xFF042C53),
    Color(0xFFC0DD97) to Color(0xFF173404),
)

private fun avatarColorsFor(seed: String) = AvatarColors[abs(seed.hashCode()) % AvatarColors.size]

private fun initials(name: String): String {
    val clean = name.trim().removePrefix("@")
    if (clean.isEmpty()) return "?"
    val parts = clean.split(' ', '_', '-').filter { it.isNotBlank() }
    return if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}".uppercase()
    else clean.take(2).uppercase()
}

/**
 * Distância aproximada a partir da força do sinal Bluetooth (RSSI, em dBm).
 * Calibrado com celulares reais: colados um no outro costumam dar entre -45 e -65 dBm,
 * então -55 ou mais forte já conta como 100%, e -95 ou mais fraco como 0%.
 */
private fun closeness(rssi: Int?): Float =
    if (rssi == null) 0f else ((rssi + 95).coerceIn(0, 40)) / 40f

private fun distanceLabel(rssi: Int?, direct: Boolean): String = when {
    !direct -> "longe · via outra pessoa"
    rssi == null -> "no alcance · medindo sinal"
    rssi >= -65 -> "bem perto"
    rssi >= -78 -> "perto"
    rssi >= -88 -> "médio"
    else -> "longe"
}

private fun distanceColor(rssi: Int?, direct: Boolean): Color = when {
    !direct -> Color(0xFFF0997B)
    rssi == null -> Color(0xFFAFA9EC)
    rssi >= -78 -> Color(0xFF9FE1CB)
    rssi >= -88 -> Color(0xFFFAC775)
    else -> Color(0xFFF0997B)
}

private fun formatTime(date: Date): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (yesterday) "ontem" else SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
}

private fun vibrate(context: Context, millis: Long) {
    runCatching {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tela principal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PertoHome(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(PertoTab.Conversas) }
    val accent by PertoPrefs.accent.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAlert by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var findPeer by remember { mutableStateOf<String?>(null) }

    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val favorites by viewModel.favoritePeers.collectAsStateWithLifecycle()
    val fingerprints by viewModel.peerFingerprints.collectAsStateWithLifecycle()
    val nicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val unreadPrivate by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val meshMessages by viewModel.messages.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val myNickname by viewModel.nickname.collectAsStateWithLifecycle()

    // A barra de cima é escura: ícones da barra de status em branco.
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // Voltar: das abas Perto/Mural volta pra Conversas antes de sair do app.
    BackHandler(enabled = tab != PertoTab.Conversas && currentChannel == null) {
        tab = PertoTab.Conversas
    }

    // Aviso quando um amigo fixo entra no alcance. A identificação (fingerprint) de quem
    // acabou de conectar pode chegar um instante depois, então guardamos quem já foi avisado
    // enquanto continua no alcance, e reavaliamos sempre que a lista ou os fingerprints mudam.
    var avisados by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(connectedPeers, fingerprints, favorites) {
        val presentes = connectedPeers.toSet()
        val amigosPresentes = presentes.filter { pid -> fingerprints[pid]?.let { it in favorites } == true }
        val novos = amigosPresentes.filter { it !in avisados }
        avisados = (avisados intersect presentes) + novos
        for (pid in novos) {
            vibrate(context, 120)
            snackbar.showSnackbar("${nicknames[pid] ?: "Um amigo"} chegou perto")
        }
    }

    // Alerta urgente recebido de alguém por perto. Os alertas que já estavam na tela quando o
    // app abriu não disparam de novo; qualquer alerta novo (inclusive o primeiro) dispara.
    val alertasIniciais = remember {
        viewModel.messages.value.filter { it.content.startsWith(ALERTA_PREFIXO) }.map { it.id }.toSet()
    }
    var lastSeenAlertId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(meshMessages.lastOrNull()?.id) {
        val alert = meshMessages.lastOrNull { it.content.startsWith(ALERTA_PREFIXO) } ?: return@LaunchedEffect
        if (alert.id in alertasIniciais) return@LaunchedEffect
        if (alert.id != lastSeenAlertId) {
            lastSeenAlertId = alert.id
            if (alert.sender != myNickname) {
                vibrate(context, 600)
                val texto = alert.content.removePrefix(ALERTA_PREFIXO).trim()
                val res = snackbar.showSnackbar(
                    message = "Alerta de ${alert.sender}: $texto",
                    actionLabel = "Ver",
                    duration = SnackbarDuration.Long,
                )
                if (res == SnackbarResult.ActionPerformed) tab = PertoTab.Mural
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            PertoTopBar(
                barColor = barColor(accent),
                selected = tab,
                unreadCount = unreadPrivate.size,
                peerCount = connectedPeers.size,
                onSelect = { tab = it },
                onAlert = { showAlert = true },
                onSettings = { showSettings = true },
                onLogoTap = { viewModel.showAppInfo() },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (tab) {
                    PertoTab.Conversas -> ConversasTab(
                        viewModel = viewModel,
                        onGoToRadar = { tab = PertoTab.Perto },
                    )
                    PertoTab.Perto -> RadarTab(
                        viewModel = viewModel,
                        accent = accent,
                        onFind = { findPeer = it },
                    )
                    PertoTab.Mural -> ChatScreen(viewModel = viewModel, embeddedInTabs = true)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }

    if (tab != PertoTab.Mural) PertoSharedDialogs(viewModel)

    if (showAlert) {
        AlertaDialog(
            onDismiss = { showAlert = false },
            onSend = { texto ->
                showAlert = false
                if (viewModel.currentChannel.value != null) viewModel.switchToChannel(null)
                viewModel.sendMessage("$ALERTA_PREFIXO $texto") { accepted ->
                    if (accepted) tab = PertoTab.Mural
                }
            }
        )
    }

    if (showSettings) {
        PertoSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
        )
    }

    findPeer?.let { pid ->
        AcharAmigoDialog(viewModel = viewModel, peerID = pid, onDismiss = { findPeer = null })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Barra de cima com as abas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PertoTopBar(
    barColor: Color,
    selected: PertoTab,
    unreadCount: Int,
    peerCount: Int,
    onSelect: (PertoTab) -> Unit,
    onAlert: () -> Unit,
    onSettings: () -> Unit,
    onLogoTap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(barColor)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLogoTap)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(BitChatIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Perto", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = Color(0xFF9FE1CB), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("$peerCount", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onAlert) {
                Icon(Icons.Outlined.Campaign, contentDescription = "Mandar alerta urgente", tint = Laranja)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações", tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            PertoTab.entries.forEach { t ->
                val isSel = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(t) }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.label,
                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.62f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (t == PertoTab.Conversas && unreadCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = Laranja, contentColor = LaranjaEscuro) {
                                Text("$unreadCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (isSel) Laranja else Color.Transparent)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Aba Conversas
// ─────────────────────────────────────────────────────────────────────────────

private data class ConversaItem(
    val key: String,
    val name: String,
    val preview: String,
    val time: Date,
    val unread: Int,
    val online: Boolean,
    val favorite: Boolean,
)

private fun previewOf(msg: BitchatMessage, me: String): String {
    val body = when (msg.type) {
        BitchatMessageType.Audio -> "🎤 Áudio"
        BitchatMessageType.Image -> "📷 Foto"
        BitchatMessageType.File -> "📎 Arquivo"
        else -> msg.content.replace('\n', ' ')
    }
    return if (msg.sender == me) "Você: $body" else body
}

@Composable
private fun ConversasTab(viewModel: ChatViewModel, onGoToRadar: () -> Unit) {
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val unread by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val nicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val connected by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val favorites by viewModel.favoritePeers.collectAsStateWithLifecycle()
    val fingerprints by viewModel.peerFingerprints.collectAsStateWithLifecycle()
    val me by viewModel.nickname.collectAsStateWithLifecycle()

    val items = remember(privateChats, unread, nicknames, connected, favorites, fingerprints, me) {
        // As conversas de amigos fixos usam um ID de contato (da chave de criptografia), não o
        // ID de quem está no alcance agora. Este mapa liga um ao outro.
        val conversaParaPeer = connected.associateBy { pid ->
            runCatching { com.bitchat.android.services.ContactDirectory.canonicalConversationId(pid) }
                .getOrDefault(pid)
        }
        privateChats.entries
            .filter { it.value.isNotEmpty() }
            .map { (key, msgs) ->
                val last = msgs.maxBy { it.timestamp.time }
                val contato = runCatching { com.bitchat.android.services.ContactDirectory.resolve(key) }.getOrNull()
                val peer = conversaParaPeer[key]
                    ?: key.takeIf { it in connected }
                    ?: contato?.meshPeerID?.takeIf { it in connected }
                val name = peer?.let { nicknames[it] }
                    ?: nicknames[key]
                    ?: contato?.displayName?.takeIf { it.isNotBlank() }
                    ?: msgs.lastOrNull { it.sender != me && it.sender.isNotBlank() }?.sender
                    ?: msgs.firstNotNullOfOrNull { it.recipientNickname }
                    ?: key.take(8)
                val unreadCount = if (key in unread) {
                    msgs.sortedBy { it.timestamp.time }
                        .takeLastWhile { it.sender != me }
                        .size.coerceAtLeast(1)
                } else 0
                ConversaItem(
                    key = key,
                    name = name,
                    preview = previewOf(last, me),
                    time = last.timestamp,
                    unread = unreadCount,
                    online = peer != null,
                    favorite = (peer ?: key).let { fingerprints[it] }?.let { it in favorites } == true ||
                        contato?.isMutualFavorite == true ||
                        com.bitchat.android.services.ContactIdentityResolver.isContactConversationId(key),
                )
            }
            .sortedWith(compareByDescending<ConversaItem> { it.favorite }.thenByDescending { it.time.time })
    }

    if (items.isEmpty()) {
        EmptyState(
            title = "Nenhuma conversa ainda",
            body = "Abra a aba Perto pra ver quem está no alcance do Bluetooth e começar uma conversa.",
            action = "Ver quem está perto",
            onAction = onGoToRadar,
        )
        return
    }

    val favoritos = items.filter { it.favorite }
    val outros = items.filterNot { it.favorite }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        if (favoritos.isNotEmpty()) {
            item { SectionLabel("Amigos fixos") }
            items(favoritos, key = { "f-" + it.key }) { c ->
                ConversaRow(c) { viewModel.showPrivateChatSheet(c.key) }
            }
            if (outros.isNotEmpty()) item { SectionLabel("Outras conversas") }
        }
        items(outros, key = { it.key }) { c ->
            ConversaRow(c) { viewModel.showPrivateChatSheet(c.key) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Avatar(name: String, size: Dp, online: Boolean = false, seed: String = name) {
    val (bg, fg) = avatarColorsFor(seed)
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(name), color = fg, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.34f).sp)
        }
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1D9E75))
            )
        }
    }
}

@Composable
private fun ConversaRow(c: ConversaItem, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.name, 50.dp, online = c.online, seed = c.key)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.favorite) {
                    Icon(Icons.Outlined.Star, null, tint = Laranja, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    c.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onBackground
                )
                Text(
                    formatTime(c.time),
                    fontSize = 12.sp,
                    color = if (c.unread > 0) colorScheme.secondary else colorScheme.onSurfaceVariant,
                    fontWeight = if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.preview,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariant
                )
                if (c.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = Laranja, contentColor = LaranjaEscuro) {
                        Text("${c.unread}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp),
        thickness = 0.5.dp,
        color = colorScheme.outlineVariant
    )
}

@Composable
private fun EmptyState(title: String, body: String, action: String?, onAction: () -> Unit, dark: Boolean = false) {
    val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onBackground
    val subColor = if (dark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = subColor, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = subColor, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Laranja, contentColor = LaranjaEscuro)
            ) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Aba Perto (radar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RadarTab(viewModel: ChatViewModel, accent: PertoAccent, onFind: (String) -> Unit) {
    val connected by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val nicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val rssi by viewModel.peerRSSI.collectAsStateWithLifecycle()
    val direct by viewModel.peerDirect.collectAsStateWithLifecycle()
    val favorites by viewModel.favoritePeers.collectAsStateWithLifecycle()
    val fingerprints by viewModel.peerFingerprints.collectAsStateWithLifecycle()

    val bg = radarColor(accent)
    val ring = radarRing(accent)
    val card = barColor(accent)

    val peers = connected.sortedByDescending { closeness(rssi[it]) }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(18.dp))
        Radar(
            peers = peers,
            nicknames = nicknames,
            rssi = rssi,
            direct = direct,
            ring = ring,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(280.dp),
            onPeerTap = { viewModel.showPrivateChatSheet(it) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (peers.size) {
                0 -> "Procurando pessoas com o Perto…"
                1 -> "1 pessoa no alcance"
                else -> "${peers.size} pessoas no alcance"
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))

        if (peers.isEmpty()) {
            EmptyState(
                title = "Ninguém no alcance ainda",
                body = "Quando alguém com o Perto chegar perto (uns 30 a 100 metros), aparece aqui no radar. " +
                    "Deixe o Bluetooth ligado.",
                action = null,
                onAction = {},
                dark = true,
            )
        }

        peers.forEach { pid ->
            val name = nicknames[pid] ?: pid.take(8)
            val isDirect = direct[pid] ?: true
            val isFav = fingerprints[pid]?.let { it in favorites } == true
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(card)
                    .clickable { viewModel.showPrivateChatSheet(pid) }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name, 38.dp, seed = pid)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(distanceLabel(rssi[pid], isDirect), color = distanceColor(rssi[pid], isDirect), fontSize = 12.5.sp)
                }
                IconButton(onClick = { viewModel.toggleFavorite(pid) }) {
                    Icon(
                        if (isFav) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isFav) "Tirar dos amigos fixos" else "Fixar como amigo",
                        tint = if (isFav) Laranja else Color.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { onFind(pid) }) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "Achar $name", tint = Color.White)
                }
                IconButton(onClick = { viewModel.showPrivateChatSheet(pid) }) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Conversar com $name", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Radar(
    peers: List<String>,
    nicknames: Map<String, String>,
    rssi: Map<String, Int>,
    direct: Map<String, Boolean>,
    ring: Color,
    modifier: Modifier,
    onPeerTap: (String) -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    BoxWithConstraints(modifier) {
        val sizeDp = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            listOf(1f, 0.68f, 0.36f).forEachIndexed { i, f ->
                drawCircle(ring.copy(alpha = 0.35f + i * 0.2f), radius = r * f, center = c, style = Stroke(width = 1.5.dp.toPx()))
            }
            rotate(sweep, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Laranja.copy(alpha = 0.30f),
                        0.18f to Color.Transparent,
                        1f to Color.Transparent,
                        center = c
                    ),
                    radius = r,
                    center = c
                )
            }
            drawCircle(Laranja.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = c)
            drawCircle(Laranja, radius = 9.dp.toPx(), center = c)
        }
        val avatar = 40.dp
        peers.forEach { pid ->
            val isDirect = direct[pid] ?: true
            val near = if (isDirect) closeness(rssi[pid]) else 0f
            val frac = 0.30f + 0.62f * (1f - near)
            val angle = Math.toRadians((abs(pid.hashCode()) % 360).toDouble())
            val radius = sizeDp / 2 * frac
            val x = sizeDp / 2 + radius * cos(angle).toFloat() - avatar / 2
            val y = sizeDp / 2 + radius * sin(angle).toFloat() - avatar / 2
            val name = nicknames[pid] ?: pid.take(8)
            Box(
                Modifier
                    .offset(x = x, y = y)
                    .size(avatar)
                    .clip(CircleShape)
                    .clickable { onPeerTap(pid) }
            ) {
                Avatar(name, avatar, seed = pid)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "Achar amigo": quente ou frio pela força do sinal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AcharAmigoDialog(viewModel: ChatViewModel, peerID: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rssiMap by viewModel.peerRSSI.collectAsStateWithLifecycle()
    val connected by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val nicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val name = nicknames[peerID] ?: "seu amigo"
    val inRange = peerID in connected
    val rssi = if (inRange) rssiMap[peerID] else null
    // O sinal Bluetooth oscila bastante; uma média móvel deixa o "quente ou frio" estável.
    var smoothed by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(rssi) {
        if (rssi != null) {
            val now = closeness(rssi)
            smoothed = smoothed?.let { it * 0.6f + now * 0.4f } ?: now
        }
    }
    val target = smoothed ?: 0f
    val c by animateFloatAsState(target, tween(600), label = "closeness")
    val currentC by rememberUpdatedState(target)

    // Vibra mais rápido e mais forte quanto mais perto.
    LaunchedEffect(Unit) {
        while (true) {
            val v = currentC
            if (v > 0f) vibrate(context, (30 + v * 70).toLong())
            delay((1600 - v * 1350).toLong().coerceAtLeast(220))
        }
    }

    val label = when {
        !inRange -> "Fora do alcance… ande um pouco"
        smoothed == null -> "Conectado, medindo o sinal…"
        c > 0.82f -> "Pelando! Tá do seu lado"
        c > 0.62f -> "Quente!"
        c > 0.42f -> "Morno"
        c > 0.22f -> "Frio"
        else -> "Gelado"
    }
    val color by animateColorAsState(lerp(Color(0xFF378ADD), Color(0xFFE24B4A), c), label = "heat")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Achar $name") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .padding(vertical = 12.dp)
                        .size((90 + 70 * c).dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.25f))
                        .border(BorderStroke(3.dp, color), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${(c * 100).toInt()}%", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ande pelo lugar: o celular vibra mais rápido quanto mais perto você chega. " +
                        "Paredes e pessoas no meio enfraquecem o sinal.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rssi != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Sinal: $rssi dBm", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Alerta urgente para todo mundo no alcance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlertaDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val opcoes = listOf(
        "Me perdi, alguém me ajuda a achar o grupo?",
        "Preciso de ajuda agora!",
        "Estou bem, só avisando onde estou.",
        "Vamos embora, encontro na saída.",
    )
    var escolhido by remember { mutableStateOf(opcoes.first()) }
    var outro by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Campaign, null, tint = Laranja) },
        title = { Text("Mandar alerta") },
        text = {
            Column {
                Text(
                    "Vai pra todo mundo com o Perto no alcance, com vibração forte.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                opcoes.forEach { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { escolhido = o; erro = false }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = escolhido == o, onClick = { escolhido = o; erro = false })
                        Text(o, fontSize = 14.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = escolhido == "", onClick = { escolhido = "" })
                    OutlinedTextField(
                        value = outro,
                        onValueChange = { outro = it; escolhido = ""; erro = false },
                        placeholder = { Text("Escrever outro…") },
                        singleLine = true,
                        isError = erro,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (erro) {
                    Text("Escreva o alerta primeiro", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val texto = if (escolhido.isEmpty()) outro.trim() else escolhido
                    if (texto.isEmpty()) erro = true else onSend(texto)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Laranja, contentColor = LaranjaEscuro)
            ) { Text("Mandar alerta", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Configurações: tema, Mural, verificação, emergência
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PertoSettingsDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent by PertoPrefs.accent.collectAsStateWithLifecycle()
    val muralHours by PertoPrefs.muralHours.collectAsStateWithLifecycle()
    val themePref by ThemePreferenceManager.themeFlow.collectAsStateWithLifecycle()
    var confirmPanic by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurações") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingTitle("Cor do app")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PertoAccent.entries.forEach { a ->
                        ChoiceChip(
                            label = a.label,
                            selected = a == accent,
                            swatch = barColor(a),
                            onClick = { PertoPrefs.setAccent(context, a) }
                        )
                    }
                }
                SettingTitle("Claro ou escuro")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemePreference.System to "Automático",
                        ThemePreference.Light to "Claro",
                        ThemePreference.Dark to "Escuro",
                    ).forEach { (p, l) ->
                        ChoiceChip(label = l, selected = p == themePref, onClick = { ThemePreferenceManager.set(context, p) })
                    }
                }
                SettingTitle("Mural mostra mensagens de")
                Text(
                    "As mais antigas somem sozinhas da tela.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PertoPrefs.muralHourOptions.forEach { h ->
                        ChoiceChip(
                            label = if (h == 0) "Sempre" else "Últimas ${h}h",
                            selected = h == muralHours,
                            onClick = { PertoPrefs.setMuralHours(context, h) }
                        )
                    }
                }
                SettingTitle("Segurança")
                SettingAction("Adicionar amigo pelo QR code") { onDismiss(); viewModel.showVerificationSheet() }
                SettingAction("Rede e pessoas conectadas") { onDismiss(); viewModel.showMeshPeerList() }
                SettingAction("Sobre o Perto") { onDismiss(); viewModel.showAppInfo() }
                SettingAction("Apagar tudo (emergência)", danger = true) { confirmPanic = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Pronto") } }
    )

    if (confirmPanic) {
        AlertDialog(
            onDismissRequest = { confirmPanic = false },
            title = { Text("Apagar tudo?") },
            text = {
                Text(
                    "Apaga todas as conversas, contatos fixos e sua identidade deste celular. " +
                        "Não dá pra desfazer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPanic = false
                        onDismiss()
                        viewModel.panicClearAllData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Apagar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmPanic = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SettingTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
}

@Composable
private fun SettingAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, swatch: Color? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = swatch?.let { sw ->
            { Box(Modifier.size(12.dp).clip(CircleShape).background(sw)) }
        }
    )
}
