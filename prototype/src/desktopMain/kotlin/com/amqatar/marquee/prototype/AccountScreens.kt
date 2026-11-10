package com.amqatar.marquee.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Logo() = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(C.Accent), contentAlignment = Alignment.Center) { Icon(Icons.Play, C.Bg, 10.dp) }
    T("Marquee", 15.sp, weight = FontWeight.Bold)
}

@Composable
fun ProfilesScreen(state: AppState) {
    val first = rememberInitialFocus()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Logo()
        Spacer(Modifier.height(30.dp))
        T("Who's watching?", 32.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            state.profiles.forEachIndexed { i, p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusBox(
                        onClick = { if (p.pin != null) state.go(Screen.Pin(p.id)) else state.choose(p) },
                        modifier = Modifier.size(100.dp), shape = RoundedCornerShape(20.dp), focusedScale = 1.07f,
                        focusRequester = if (i == 0) first else null, background = p.color,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(p.name.take(1), 42.sp, Color.White, FontWeight.Bold) }
                        if (p.pin != null) Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.BottomEnd) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(C.Panel), contentAlignment = Alignment.Center) { Icon(Icons.Lock, C.Soft, 12.dp) }
                        }
                    }
                    T(p.name, 13.sp, weight = FontWeight.SemiBold)
                    T(if (p.kids) "Kids catalog only" else if (p == state.current) "Last used here" else " ", 9.sp, C.Muted)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusBox(
                    onClick = {
                        if (state.profiles.size >= 6) state.say("Up to 6 profiles per account")
                        else {
                            val n = state.profiles.size + 1
                            state.profiles.add(Profile("p$n", "Profile $n", Color(0xFF6A5A3A)))
                            state.say("Profile $n created (saved to your account via the API)")
                        }
                    },
                    modifier = Modifier.size(100.dp).border(1.dp, C.LineStrong, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), focusedScale = 1.07f,
                ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Plus, C.Muted, 28.dp) } }
                T("Add profile", 13.sp, C.Muted, FontWeight.SemiBold)
                T("Up to 6", 9.sp, C.Muted)
            }
        }
        Spacer(Modifier.height(28.dp))
        PillButton("Manage profiles", { state.go(Screen.Settings("Profile")) })
        Spacer(Modifier.height(18.dp))
        T(if (state.signedIn) "Signed in as [account email] · profiles load from your account" else "Not signed in · profiles stay on this TV", 9.sp, C.Muted)
        T("Each profile keeps its own addons, library, progress and settings, on every device", 9.sp, C.Faint)
    }
}

@Composable
fun PinScreen(state: AppState, profileId: String) {
    val profile = state.profiles.first { it.id == profileId }
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val first = rememberInitialFocus()
    LaunchedEffect(entered) {
        if (entered.length == 4) {
            delay(150)
            if (entered == profile.pin) state.choose(profile) else { wrong = true; entered = "" }
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(profile.color), contentAlignment = Alignment.Center) { T(profile.name.take(1), 28.sp, Color.White, FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        T("Enter PIN for ${profile.name}", 20.sp, weight = FontWeight.Bold)
        T(if (wrong) "Wrong PIN, try again (prototype PIN: ${profile.pin})" else "Prototype PIN: ${profile.pin}", 10.sp, if (wrong) C.Warn else C.Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { i -> Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < entered.length) C.Text else C.LineStrong)) }
        }
        Spacer(Modifier.height(18.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) Spacer(Modifier.size(48.dp)) else FocusBox(
                            onClick = { wrong = false; entered = if (k == "⌫") entered.dropLast(1) else (entered + k).take(4) },
                            modifier = Modifier.size(48.dp), shape = CircleShape, background = C.Raised,
                            focusRequester = if (k == "1") first else null,
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(k, 16.sp, weight = FontWeight.SemiBold) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        T("Esc to go back", 9.sp, C.Faint)
    }
}

@Composable
fun SimklPinScreen(state: AppState, profileId: String) {
    val profile = state.profiles.first { it.id == profileId }
    var seconds by remember { mutableIntStateOf(899) }
    LaunchedEffect(Unit) { while (seconds > 0) { delay(1000); seconds-- } }
    val first = rememberInitialFocus()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        T("Connect Simkl for ${profile.name}", 26.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        T("On your phone or computer, go to", 13.sp, C.Soft)
        T("simkl.com/pin", 22.sp, C.Accent, FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        T("and enter", 13.sp, C.Soft)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
            T("4F7K2Q", 34.sp, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        T("Code expires in ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} · this screen updates when you approve", 9.sp, C.Muted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Simulate approval", {
                profile.simklUser = "[simkl user]"; profile.simklLastSync = "just now"
                state.back(); state.say("Simkl connected for ${profile.name}; history imported")
            }, primary = true, focusRequester = first)
            PillButton("Cancel", { state.back() })
        }
        Spacer(Modifier.height(14.dp))
        T("The access token is stored by the Marquee server for this profile only.", 9.sp, C.Faint)
    }
}

@Composable
fun SignInScreen(state: AppState) {
    var seconds by remember { mutableIntStateOf(582) }
    var code by remember { mutableStateOf("K7Q-4MX") }
    LaunchedEffect(code) { seconds = 582; while (seconds > 0) { delay(1000); seconds-- } }
    val first = rememberInitialFocus()
    Row(Modifier.fillMaxSize().padding(horizontal = 80.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(70.dp)) {
        Column(Modifier.width(390.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Logo()
            T("Sign in with your phone", 32.sp, weight = FontWeight.Bold)
            listOf(
                "Scan the code with your phone's camera, or go to [your-domain]/link",
                "Sign in or create an account",
                "Check the code matches, then tap Allow this TV",
            ).forEachIndexed { i, s ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) { T("${i + 1}", 10.sp, C.Accent, FontWeight.Bold) }
                    T(s, 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Simulate phone approval", { state.signedIn = true; state.say("TV linked to your account"); state.reset(Screen.Profiles) }, primary = true, focusRequester = first)
                PillButton("Get a new code", { code = listOf("P2D-9LW", "H4N-7QX", "Z8M-3KC").random() })
                PillButton("Use without an account", { state.signedIn = false; state.reset(Screen.Profiles) })
            }
            T("Without an account, profiles and progress stay on this TV only.", 9.sp, C.Muted)
        }
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                T("QR code\n(generated per sign-in)", 10.sp, Color(0xFF4A5266), FontWeight.SemiBold)
            }
            T("Your code", 9.sp, C.Muted)
            T(code, 30.sp, weight = FontWeight.Bold, modifier = Modifier.offset(y = (-6).dp))
            T("Expires in ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", 9.sp, C.Muted)
        }
    }
}
