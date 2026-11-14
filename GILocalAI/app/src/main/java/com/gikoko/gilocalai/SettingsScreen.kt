package com.gikoko.gilocalai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GiColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = GiColors.text, fontSize = 13.sp)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = GiColors.text, fontSize = 14.sp)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadFromUri(uri)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.scanModels() }

    fun scan() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.scanModels() else permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("🤖 Model (GGUF)") {
                Text(
                    vm.modelName?.let { "Loaded: $it" } ?: "No model loaded",
                    color = if (vm.modelName != null) GiColors.neon else GiColors.muted, fontSize = 14.sp
                )
                if (vm.modelInfo.isNotEmpty()) Text(vm.modelInfo, color = GiColors.muted, fontSize = 10.sp, maxLines = 4)
                if (vm.status.isNotEmpty()) Text(vm.status, color = GiColors.gold, fontSize = 12.sp)
                Button(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Pick .gguf file")
                }
                OutlinedButton(onClick = { scan() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan phone for .gguf files")
                }
                vm.ggufFiles.forEach { f ->
                    Column(Modifier.fillMaxWidth().clickable { vm.loadFromPath(f.absolutePath) }) {
                        Text("• ${f.name}", color = GiColors.text, fontSize = 13.sp)
                        Text("${f.parent}  ·  ${f.length() / 1_048_576} MB", color = GiColors.muted, fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.reloadModel() }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Reload") }
                    OutlinedButton(onClick = { vm.unloadModel() }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Unload") }
                }
                SwitchRow("Load last model at startup", s.autoLoad) { s.autoLoad = it; s.save() }
            }
        }

        item {
            SectionCard("🧠 Auto-learn") {
                SwitchRow("Use my knowledge in answers", s.useKnowledge) { s.useKnowledge = it; s.save() }
                SwitchRow("Learn from chat (remember facts, corrections)", s.learnFromChat) { s.learnFromChat = it; s.save() }
                SwitchRow("Learn online when I don't know (Wikipedia)", s.autoOnline) { s.autoOnline = it; s.save() }
                SliderRow("Notes given to the model: ${s.topHits}", s.topHits.toFloat(), 1f..5f, 3) { s.topHits = it.toInt(); s.save() }
            }
        }

        item {
            SectionCard("⚙️ Generation") {
                SliderRow("Temperature: ${"%.2f".format(s.temperature)}", s.temperature, 0f..1.5f, 14) { s.temperature = it; s.save() }
                SliderRow("Top-p: ${"%.2f".format(s.topP)}", s.topP, 0.1f..1f, 17) { s.topP = it; s.save() }
                SliderRow("Top-k: ${s.topK}", s.topK.toFloat(), 1f..100f, 0) { s.topK = it.toInt(); s.save() }
                SliderRow("Repeat penalty: ${"%.2f".format(s.repeatPenalty)}", s.repeatPenalty, 1f..1.5f, 9) { s.repeatPenalty = it; s.save() }
                SliderRow("Max answer tokens: ${s.maxTokens}", s.maxTokens.toFloat(), 32f..1024f, 30) { s.maxTokens = (it / 16).toInt() * 16; s.save() }
                SliderRow("Remembered chat turns: ${s.historyTurns}", s.historyTurns.toFloat(), 0f..10f, 9) { s.historyTurns = it.toInt(); s.save() }
            }
        }

        item {
            SectionCard("📐 Needs model reload") {
                Text("Context size", color = GiColors.text, fontSize = 13.sp)
                listOf(1024, 2048, 3072, 4096).forEach { n ->
                    RadioRow("$n tokens", s.nCtx == n) { s.nCtx = n; s.save() }
                }
                SliderRow("CPU threads: ${s.threads}", s.threads.toFloat(), 1f..8f, 6) { s.threads = it.toInt(); s.save() }
                Text("Tip: 4 threads and 2048 context suit a 4 GB phone.", color = GiColors.muted, fontSize = 11.sp)
                OutlinedButton(onClick = { vm.reloadModel() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply & reload model")
                }
            }
        }

        item {
            SectionCard("💬 Chat template") {
                PromptBuilder.Tpl.values().forEach { t ->
                    RadioRow(t.label, s.template == t.name) { s.template = t.name; s.save() }
                }
                Text("Qwen models use ChatML. Pick the one that matches your model.", color = GiColors.muted, fontSize = 11.sp)
            }
        }

        item {
            SectionCard("📜 System prompt") {
                OutlinedTextField(
                    value = s.systemPrompt, onValueChange = { s.systemPrompt = it; s.save() },
                    modifier = Modifier.fillMaxWidth(), maxLines = 8
                )
                OutlinedButton(onClick = { s.systemPrompt = DEFAULT_SYSTEM_PROMPT; s.save() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset to default")
                }
            }
        }
    }
}
