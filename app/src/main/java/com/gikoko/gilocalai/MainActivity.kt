package com.gikoko.gilocalai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GiTheme {
                Root()
            }
        }
    }
}

@Composable
fun Root(vm: MainViewModel = viewModel()) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        MainScaffold(vm)
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val items = listOf("Chat" to "💬", "Learn" to "🧠", "Settings" to "⚙️")
    Scaffold(
        containerColor = GiColors.bg,
        bottomBar = {
            NavigationBar(containerColor = GiColors.card) {
                items.forEachIndexed { i, (label, emoji) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = GiColors.neon,
                            unselectedTextColor = GiColors.muted,
                            indicatorColor = GiColors.card2
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> ChatScreen(vm)
                1 -> LearnScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
