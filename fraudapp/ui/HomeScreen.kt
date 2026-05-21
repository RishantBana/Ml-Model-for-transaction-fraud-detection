package com.example.fraudapp.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fraudapp.model.TransactionHistoryEntry
import com.example.fraudapp.ui.AppColors.AccentAmber
import com.example.fraudapp.ui.AppColors.AccentBlue
import com.example.fraudapp.ui.AppColors.AccentGreen
import com.example.fraudapp.ui.AppColors.AccentRed
import com.example.fraudapp.ui.AppColors.BgDeep
import com.example.fraudapp.ui.AppColors.Border
import com.example.fraudapp.ui.AppColors.Surface1
import com.example.fraudapp.ui.AppColors.Surface2
import com.example.fraudapp.ui.AppColors.TextMuted
import com.example.fraudapp.ui.AppColors.TextPrimary
import com.example.fraudapp.ui.AppColors.TextSecondary
import com.example.fraudapp.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onNavigate: () -> Unit) {
    val history by vm.history.collectAsState()
    val stats   = remember(history) { SessionStats.from(history) }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Surface2, RoundedCornerShape(7.dp))
                                .border(1.dp, Border, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("🛡", fontSize = 14.sp) }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Fraud Detection",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                actions = {
                    LivePulse()
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BgDeep
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = 140.dp, y = (-40).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentBlue.copy(alpha = 0.06f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                Text(
                    "Transaction Risk Engine",
                    fontSize  = 12.sp,
                    color     = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                ModelStatusCard(history = history)

                SessionStatsRow(stats = stats)

                if (history.isNotEmpty()) RiskDistributionCard(stats = stats)

                if (history.isNotEmpty()) RecentTransactionsCard(history = history.take(5))

                if (history.isEmpty()) EmptyStateCard()

                Spacer(Modifier.height(4.dp))

                ScoreTransactionButton(onClick = onNavigate)

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}


@Composable
private fun LivePulse() {
    val inf = rememberInfiniteTransition(label = "pulse")
    val alpha by inf.animateFloat(
        initialValue  = 0.35f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(AccentGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(AccentGreen.copy(alpha = alpha)))
        Spacer(Modifier.width(5.dp))
        Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = AccentGreen, fontFamily = FontFamily.Monospace)
    }
}


@Composable
private fun ModelStatusCard(history: List<TransactionHistoryEntry>) {
    val fmt       = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val lastScored = history.firstOrNull()?.timestamp

    DarkCard {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MonoLabel("MODEL STATUS")
            StatusPill("ONLINE", AccentGreen)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Border, thickness = 0.5.dp)
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ModelMetric("VERSION",     "mvp_v1",                          Modifier.weight(1f))
            ModelMetric("ENDPOINT",    "/score",                           Modifier.weight(1f))
            ModelMetric("LAST SCORED", lastScored?.let { fmt.format(Date(it)) } ?: "never", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModelMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        MonoLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 13.sp, color = AccentBlue,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = color, fontFamily = FontFamily.Monospace)
    }
}


internal data class SessionStats(
    val total: Int, val allowed: Int, val verify: Int,
    val blocked: Int, val avgScore: Double
) {
    companion object {
        fun from(h: List<TransactionHistoryEntry>) = SessionStats(
            total    = h.size,
            allowed  = h.count { it.decision.lowercase() in listOf("allow", "verified") },
            verify   = h.count { it.decision.lowercase() == "verify" },
            blocked  = h.count { it.decision.lowercase() in listOf("block", "pending_review") },
            avgScore = if (h.isEmpty()) 0.0 else h.map { normaliseScore(it.fraudScore) }.average()
        )
    }
}

@Composable
private fun SessionStatsRow(stats: SessionStats) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile("SCORED",   stats.total.toString(),         AppColors.AccentBlue,  Modifier.weight(1f))
        StatTile("AVG RISK", "${stats.avgScore.toInt()}",    AppColors.riskColor(stats.avgScore), Modifier.weight(1f))
        StatTile("BLOCKED",  stats.blocked.toString(),       AccentRed,   Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Surface1, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            color = accentColor, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        MonoLabel(label)
    }
}


@Composable
private fun RiskDistributionCard(stats: SessionStats) {
    DarkCard {
        MonoLabel("SESSION DISTRIBUTION")
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
        ) {
            val total = stats.total.toFloat().coerceAtLeast(1f)
            if (stats.allowed > 0) Box(Modifier.weight(stats.allowed / total).fillMaxHeight().background(AccentGreen))
            if (stats.verify  > 0) Box(Modifier.weight(stats.verify  / total).fillMaxHeight().background(AccentAmber))
            if (stats.blocked > 0) Box(Modifier.weight(stats.blocked / total).fillMaxHeight().background(AccentRed))
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DistLegendItem("Allow",  stats.allowed, stats.total, AccentGreen)
            DistLegendItem("Verify", stats.verify,  stats.total, AccentAmber)
            DistLegendItem("Block",  stats.blocked, stats.total, AccentRed)
        }
    }
}

@Composable
private fun DistLegendItem(label: String, count: Int, total: Int, color: Color) {
    val pct = if (total > 0) (count * 100f / total).toInt() else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label  $pct%", fontSize = 11.sp, color = TextSecondary,
            fontFamily = FontFamily.Monospace)
    }
}


@Composable
private fun RecentTransactionsCard(history: List<TransactionHistoryEntry>) {
    DarkCard {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            MonoLabel("RECENT TRANSACTIONS")
            Text("LAST ${history.size}", fontSize = 9.sp,
                color = AccentBlue, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(14.dp))

        history.forEachIndexed { idx, entry ->
            if (idx > 0) HorizontalDivider(
                color = Border.copy(alpha = 0.5f), thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            RecentTxRow(entry)
        }
    }
}

@Composable
private fun RecentTxRow(entry: TransactionHistoryEntry) {
    val fmt       = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val normScore = normaliseScore(entry.fraudScore)
    val scoreColor = AppColors.riskColor(normScore)
    val (pillBg, pillFg) = AppColors.decisionColors(entry.decision)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(scoreColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .border(1.dp, scoreColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("${normScore.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = scoreColor, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(entry.merchantId, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "₹${String.format("%,.0f", entry.amount)}  •  ${fmt.format(Date(entry.timestamp))}",
                fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier.background(pillBg, RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(entry.decision.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color = pillFg, fontFamily = FontFamily.Monospace)
        }
    }
}


@Composable
private fun EmptyStateCard() {
    DarkCard {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📭", fontSize = 36.sp)
            Spacer(Modifier.height(12.dp))
            Text("No transactions scored yet", fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Submit a transaction to see risk scores,\nrule flags, and model insights.",
                fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun ScoreTransactionButton(onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue)
    ) {
        Text("Do a Transaction", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF0D1117), fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, contentDescription = null,
            tint = Color(0xFF0D1117), modifier = Modifier.size(18.dp))
    }
}


@Composable
internal fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(14.dp))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
internal fun MonoLabel(text: String) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        color = TextMuted, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp
    )
}
