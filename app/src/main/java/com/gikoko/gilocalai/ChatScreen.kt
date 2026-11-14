package com.gikoko.gilocalai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(vm: MainViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val msgs = vm.messages

    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .background(GiColors.card)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("GI Local AI", color = GiColors.neon, fontWeight = FontWeight.Black, fontSize = 18.sp)
                val name = vm.modelName
                Text(
                    if (vm.busy && name == null) "⏳ loading model…"
                    else if (name != null) "● $name"
                    else "○ No model loaded (Settings → pick a .gguf)",
                    color = if (name != null) GiColors.neonDim else GiColors.muted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            TextButton(onClick = { vm.newChat() }) { Text("New chat", color = GiColors.gold) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (msgs.isEmpty()) {
                item { Welcome() }
            }
            items(msgs, key = { it.id }) { m -> Bubble(m, vm) }
        }

        if (vm.status.isNotEmpty()) {
            Text(
                vm.status,
                color = GiColors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                maxLines = 2
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(GiColors.card)
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…", color = GiColors.muted) },
                maxLines = 5,
                shape = RoundedCornerShape(16.dp)
            )
            if (vm.generating) {
                Button(
                    onClick = { vm.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = GiColors.danger, contentColor = GiColors.bg)
                ) { Text("■", fontSize = 18.sp) }
            } else {
                Button(
                    onClick = {
                        val t = input
                        input = ""
                        vm.send(t)
                    },
                    enabled = input.isNotBlank()
                ) { Text("➤", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun Welcome() {
    SectionCard("Welcome") {
        Text(
            "1. Settings tab → pick your .gguf model.\n" +
                "2. Chat here. Everything runs on your phone.\n" +
                "3. Learn tab → feed it books (PDF/TXT/EPUB), web pages or Wikipedia topics. " +
                "It also remembers things you tell it (say “remember …”).",
            color = GiColors.text,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun Bubble(m: UiMsg, vm: MainViewModel) {
    val isUser = m.role == "user"
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(if (isUser) GiColors.deep.copy(alpha = 0.55f) else GiColors.card2, RoundedCornerShape(16.dp))
                .border(1.dp, if (isUser) GiColors.neonDim else GiColors.line, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    if (m.text.isEmpty() && !isUser) "…" else m.text,
                    color = GiColors.text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            if (!isUser && m.info.isNotEmpty()) {
                Spacer(Modifier.padding(top = 4.dp))
                Text(m.info.trimEnd(' ', '·'), color = GiColors.gold, fontSize = 11.sp)
            }
        }
        if (!isUser && m.text.isNotEmpty()) {
            Row {
                TextButton(onClick = { vm.saveAnswer(m) }) { Text("💾 Save", color = GiColors.muted, fontSize = 12.sp) }
                TextButton(onClick = { clipboard.setText(AnnotatedString(m.text)) }) {
                    Text("📋 Copy", color = GiColors.muted, fontSize = 12.sp)
                }
            }
        }
    }
}
