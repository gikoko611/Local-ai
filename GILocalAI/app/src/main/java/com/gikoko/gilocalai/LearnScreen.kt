package com.gikoko.gilocalai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LearnScreen(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris)
    }
    var url by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var gMy by remember { mutableStateOf("") }
    var gEn by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all knowledge?") },
            text = { Text("Books, web pages, notes and saved answers will be deleted. The glossary and chat history stay.") },
            confirmButton = {
                TextButton(onClick = { vm.clearKnowledge(); confirmClear = false }) { Text("Delete", color = GiColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            containerColor = GiColors.card
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("🧠 What I know") {
                val c = vm.kindCounts
                val total = c.values.sum()
                Text(
                    "$total passages · ${vm.glossaryCount} glossary words\n" +
                        "books ${c["book"] ?: 0} · web ${c["web"] ?: 0} · saved ${c["saved"] ?: 0} · facts/notes ${c["fact"] ?: 0}",
                    color = GiColors.text, fontSize = 13.sp
                )
                if (vm.status.isNotEmpty()) Text(vm.status, color = GiColors.gold, fontSize = 12.sp)
                Text(
                    "The model itself is not retrained. Everything learned is stored on the phone and given to the model as notes when relevant.",
                    color = GiColors.muted, fontSize = 11.sp
                )
            }
        }

        item {
            SectionCard("📖 Learn from books & files") {
                Text("TXT, MD, CSV, JSON, HTML, PDF, EPUB, DOCX", color = GiColors.muted, fontSize = 12.sp)
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose files")
                }
            }
        }

        item {
            SectionCard("🌐 Learn online") {
                OutlinedTextField(
                    value = topic, onValueChange = { topic = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Wikipedia topic (Burmese or English)") }, singleLine = true
                )
                OutlinedButton(
                    onClick = { vm.learnTopic(topic); topic = "" },
                    enabled = !vm.busy && topic.isNotBlank(), modifier = Modifier.fillMaxWidth()
                ) { Text("Learn topic") }
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Web page URL") }, singleLine = true
                )
                OutlinedButton(
                    onClick = { vm.learnUrl(url); url = "" },
                    enabled = !vm.busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()
                ) { Text("Learn page") }
            }
        }

        item {
            SectionCard("📝 Teach it a fact") {
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("e.g. My name is Ko Ko / ရန်ကုန်က မြန်မာနိုင်ငံရဲ့ …") }, maxLines = 4
                )
                Button(
                    onClick = { vm.addNote(note); note = "" },
                    enabled = note.isNotBlank(), modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }

        item {
            SectionCard("🔤 Burmese ⇄ English glossary") {
                Text("Learned automatically from Wikipedia, or add your own pairs.", color = GiColors.muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gMy, onValueChange = { gMy = it }, modifier = Modifier.weight(1f),
                        label = { Text("Burmese") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = gEn, onValueChange = { gEn = it }, modifier = Modifier.weight(1f),
                        label = { Text("English") }, singleLine = true
                    )
                }
                OutlinedButton(
                    onClick = { vm.addGlossary(gMy, gEn); gMy = ""; gEn = "" },
                    enabled = gMy.isNotBlank() && gEn.isNotBlank(), modifier = Modifier.fillMaxWidth()
                ) { Text("Add pair") }
            }
        }

        if (vm.glossaryList.isNotEmpty()) {
            items(vm.glossaryList.take(30), key = { "g" + it.id }) { g ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(GiColors.card, RoundedCornerShape(10.dp))
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${g.my}  =  ${g.en}", color = GiColors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.deleteGlossary(g.id) }) { Text("✕", color = GiColors.muted) }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Latest knowledge", color = GiColors.neon, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }) { Text("Clear all", color = GiColors.danger) }
            }
        }

        items(vm.knowledgeList, key = { "k" + it.id }) { k ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(GiColors.card, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("${k.kind} · ${k.source.take(40)}", color = GiColors.gold, fontSize = 11.sp)
                    Text(k.text.take(220), color = GiColors.text, fontSize = 13.sp)
                }
                TextButton(onClick = { vm.deleteKnowledge(k.id) }) { Text("✕", color = GiColors.muted) }
            }
        }
    }
}
