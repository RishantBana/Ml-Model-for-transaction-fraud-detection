package com.example.fraudapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fraudapp.model.ScoreResponse
import com.example.fraudapp.model.TransactionHistoryEntry
import com.example.fraudapp.viewmodel.MainViewModel
import com.example.fraudapp.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs


private object C {
    val BgDeep       = Color(0xFF0D1117)
    val Surface1     = Color(0xFF161B22)
    val Surface2     = Color(0xFF21262D)
    val Surface3     = Color(0xFF30363D)
    val Border       = Color(0xFF30363D)
    val BorderFocus  = Color(0xFF58A6FF)
    val TextPrimary  = Color(0xFFE6EDF3)
    val TextSecondary= Color(0xFF8B949E)
    val TextMuted    = Color(0xFF484F58)
    val Blue         = Color(0xFF58A6FF)
    val Green        = Color(0xFF3FB950)
    val Amber        = Color(0xFFD29922)
    val Red          = Color(0xFFF85149)

    fun decisionColors(d: String): Pair<Color, Color> = when (d.lowercase()) {
        "allow", "verified" -> Color(0xFF0D2119) to Green
        "verify"            -> Color(0xFF2B1D00) to Amber
        "block",
        "pending_review"    -> Color(0xFF2D0F0E) to Red
        else                -> Surface2 to TextSecondary
    }

    fun riskColor(score: Double) = when {
        score < 30 -> Green
        score < 60 -> Amber
        score < 80 -> Red
        else       -> Color(0xFF9C0000)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    vm: MainViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by vm.uiState.collectAsState()
    val history by vm.history.collectAsState()

    Scaffold(
        containerColor = C.BgDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Do a Transaction",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color      = C.TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = C.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = C.BgDeep)
            )
        }
    ) { padding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val s = uiState) {
                is UiState.Idle    -> TransactionForm(
                    history     = history,
                    onDoPayment = vm::doTransaction
                )
                is UiState.Loading -> LoadingView()
                is UiState.Success -> ResultView(
                    decision  = s.response.decision,
                    response  = s.response,
                    onReset   = vm::reset
                )
                is UiState.Verify  -> OTPDialog(
                    response   = s.response,
                    onVerified = vm::completeVerification,
                    onCancel   = vm::reset
                )
                is UiState.Error   -> ResultView(
                    decision      = s.response?.decision ?: "block",
                    response      = s.response,
                    overrideTitle = s.message,
                    onReset       = vm::reset
                )
            }
        }
    }
}


@Composable
private fun TransactionForm(
    history: List<TransactionHistoryEntry>,
    onDoPayment: (String, String, Double) -> Unit
) {
    var userId     by remember { mutableStateOf("") }
    var merchantId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("New Payment", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = C.TextPrimary, fontFamily = FontFamily.Monospace)
        Text("Velocity & user profile are detected automatically.",
            fontSize = 11.sp, color = C.TextSecondary, fontFamily = FontFamily.Monospace)

        Spacer(Modifier.height(2.dp))

        TxnField(userId,     { userId = it },     "User ID")
        TxnField(merchantId, { merchantId = it }, "Merchant ID")
        TxnField(amountText, { amountText = it }, "Amount (INR)", isNumeric = true)

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (userId.isNotBlank() && merchantId.isNotBlank() && amt > 0)
                    onDoPayment(userId, merchantId, amt)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = C.Blue)
        ) {
            Text("Do Payment", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = C.BgDeep, fontFamily = FontFamily.Monospace)
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SessionHistoryPanel(history)
        }

        Spacer(Modifier.height(16.dp))
    }
}


@Composable
private fun ResultView(
    decision: String,
    response: ScoreResponse?,
    overrideTitle: String? = null,
    onReset: () -> Unit
) {
    val (bannerBg, bannerFg) = C.decisionColors(decision)
    val icon = when (decision.lowercase()) {
        "allow", "verified" -> "✓"
        "verify"            -> "⚠"
        "block"             -> "✕"
        else                -> "⏳"
    }
    val title = overrideTitle ?: when (decision.lowercase()) {
        "allow"          -> "Transaction Allowed"
        "verified"       -> "Verification Complete"
        "verify"         -> "Verification Required"
        "block"          -> "Transaction Blocked"
        "pending_review" -> "Pending Manual Review"
        else             -> "Decision: $decision"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerBg, RoundedCornerShape(12.dp))
                .border(1.dp, bannerFg.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("$icon  $title", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = bannerFg, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }

        response?.let { r ->
            if (r.isColdStart == true) ColdStartBanner()

            FraudScoreGauge(rawScore = r.fraudScore)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (cBg, cFg) = C.decisionColors(r.decision)
                Chip(r.decision.replaceFirstChar { it.uppercase() }, cBg, cFg)
                r.modelVersion?.let { Chip("Model: $it", C.Surface2, C.TextSecondary) }
            }

            ScoreBreakdownCard(r)

            if (!r.topReasons.isNullOrEmpty()) TopReasonsCard(r.topReasons)

            if (!r.ruleFlags.isNullOrEmpty()) RuleFlagsCard(r.ruleFlags)
        }

        Button(
            onClick  = onReset,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = C.Surface2)
        ) {
            Text("Do Another Transaction", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = C.TextPrimary, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(16.dp))
    }
}


@Composable
private fun ColdStartBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.Amber.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .border(1.dp, C.Amber.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("!", fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = C.Amber, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("New user — conservative mode",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = C.Amber, fontFamily = FontFamily.Monospace)
            Text(
                "No prior transaction history detected. The fraud engine is using " +
                        "population-level patterns until your local history builds up. " +
                        "Block and verify thresholds are tightened accordingly.",
                fontSize = 11.sp, color = C.Amber.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace, lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun TopReasonsCard(reasons: List<String>) {
    DarkCard {
        MonoLabel("WHY THIS RESULT")
        Spacer(Modifier.height(12.dp))
        reasons.forEachIndexed { i, reason ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(18.dp)
                        .background(C.Surface2, RoundedCornerShape(4.dp))
                        .border(1.dp, C.Border, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${i + 1}", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = C.TextSecondary, fontFamily = FontFamily.Monospace)
                }
                Text(reason, fontSize = 12.sp, color = C.TextPrimary,
                    fontFamily = FontFamily.Monospace, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ScoreBreakdownCard(r: ScoreResponse) {
    val fraudDisplay = normaliseScore(r.fraudScore)
    val mlDisplay    = r.mlScore?.let { normaliseScore(it) }
    DarkCard {
        MonoLabel("SCORE BREAKDOWN")
        Spacer(Modifier.height(14.dp))
        ScoreBar("Fraud Score", fraudDisplay, C.riskColor(fraudDisplay))
        mlDisplay?.let {
            Spacer(Modifier.height(12.dp))
            ScoreBar("ML Raw Score", it, Color(0xFF8B7EF8))
        }
        r.confidence?.let { conf ->
            val c = if (conf in 0.0..1.0) conf else conf / 100.0
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = C.Border, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Model Confidence", fontSize = 12.sp, color = C.TextSecondary,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("${(c * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF26D9C7), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Double, color: Color) {
    val frac = (value / 100.0).coerceIn(0.0, 1.0).toFloat()
    Column {
        Row {
            Text(label, fontSize = 12.sp, color = C.TextSecondary,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(String.format("%.1f", value), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = color, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp)
            .clip(RoundedCornerShape(4.dp)).background(C.Surface3)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(frac.coerceAtLeast(0.012f))
                .clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
private fun RuleFlagsCard(flags: List<String>) {
    DarkCard {
        MonoLabel("RULE FLAGS  (${flags.size})")
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            flags.forEach { flag ->
                val color = if (flag == "cold_start_user") C.Amber else C.Red
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(flag.replace('_', ' '), fontSize = 11.sp,
                        color = color, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryPanel(history: List<TransactionHistoryEntry>) {
    var expanded by remember { mutableStateOf(false) }
    DarkCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("HISTORY  (${history.size})")
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = C.TextSecondary,
                modifier = Modifier.size(20.dp).clickable { expanded = !expanded }
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                history.forEachIndexed { idx, entry ->
                    if (idx > 0) HorizontalDivider(color = C.Border.copy(alpha = 0.5f),
                        thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    HistoryRow(entry, idx == 0)
                }
            }
        }
        if (!expanded) {
            val l = history.first()
            Spacer(Modifier.height(6.dp))
            Text(
                "Latest: ${l.merchantId}  •  ₹${String.format("%.0f", l.amount)}  •  Score ${normaliseScore(l.fraudScore).toInt()}",
                fontSize = 11.sp, color = C.TextSecondary, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: TransactionHistoryEntry, isFirst: Boolean) {
    val fmt   = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val score = normaliseScore(entry.fraudScore)
    val (pillBg, pillFg) = C.decisionColors(entry.decision)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(pillFg))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${entry.merchantId}  •  ₹${String.format("%.0f", entry.amount)}",
                    fontSize = 12.sp,
                    color = if (isFirst) C.TextPrimary else C.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isFirst) FontWeight.SemiBold else FontWeight.Normal)
                if (entry.isColdStart) {
                    Box(modifier = Modifier
                        .background(C.Amber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("new user", fontSize = 9.sp,
                            color = C.Amber, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Text("${fmt.format(Date(entry.timestamp))}  •  ${entry.localTxnCount} prior txns",
                fontSize = 10.sp, color = C.TextMuted, fontFamily = FontFamily.Monospace)
        }
        Text("${score.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = C.riskColor(score), fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.background(pillBg, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text(entry.decision.uppercase(), fontSize = 9.sp,
                color = pillFg, fontFamily = FontFamily.Monospace)
        }
    }
}


@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = C.Blue)
            Text("Detecting location…", fontSize = 13.sp,
                color = C.TextSecondary, fontFamily = FontFamily.Monospace)
            Text("Computing user profile…", fontSize = 11.sp,
                color = C.TextMuted, fontFamily = FontFamily.Monospace)
        }
    }
}


@Composable
private fun OTPDialog(
    response: ScoreResponse,
    onVerified: (ScoreResponse) -> Unit,
    onCancel: () -> Unit
) {
    var otp  by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("OTP sent to registered number.") }
    AlertDialog(
        onDismissRequest = {},
        containerColor   = C.Surface1,
        titleContentColor = C.TextPrimary,
        textContentColor  = C.TextSecondary,
        title = { Text("Verify Transaction", fontFamily = FontFamily.Monospace) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = otp, onValueChange = { otp = it },
                    label = { Text("Enter OTP", fontFamily = FontFamily.Monospace) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor        = C.TextPrimary,
                        unfocusedTextColor      = C.TextPrimary,
                        focusedContainerColor   = C.Surface2,
                        unfocusedContainerColor = C.Surface1,
                        focusedBorderColor      = C.Blue,
                        unfocusedBorderColor    = C.Border,
                        focusedLabelColor       = C.Blue,
                        unfocusedLabelColor     = C.TextSecondary,
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (otp.isNotEmpty()) onVerified(response)
                else hint = "Please enter the OTP to continue."
            }) {
                Text("Verify", color = C.Blue, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = C.TextSecondary, fontFamily = FontFamily.Monospace)
            }
        }
    )
}


@Composable
private fun TxnField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, isNumeric: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions.Default,
        singleLine = true,
        textStyle  = LocalTextStyle.current.copy(color = C.TextPrimary,
            fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = C.Surface2,
            unfocusedContainerColor = C.Surface1,
            focusedBorderColor      = C.Blue,
            unfocusedBorderColor    = C.Border,
            focusedLabelColor       = C.Blue,
            unfocusedLabelColor     = C.TextSecondary,
            cursorColor             = C.Blue
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Box(modifier = Modifier.background(bg, RoundedCornerShape(20.dp))
        .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        .padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, fontSize = 11.sp, color = fg,
            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

