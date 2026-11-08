package com.mkveditor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Orange = Color(0xFFFF8A00)
private val Bg = Color(0xFF0A0A0C)
private val CardBg = Color(0xFF1E1E20)
private val Muted = Color(0xFF9A9A9F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange,
                    onPrimary = Color.White,
                    background = Bg,
                    surface = CardBg,
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) {
                Screen()
            }
        }
    }
}

@Composable
fun Screen(vm: MainViewModel = viewModel()) {
    val view = LocalView.current
    LaunchedEffect(vm.busy) { view.keepScreenOn = vm.busy }

    val pickMain = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadMain(uri)
    }
    val pickExtra = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addExternal(uri)
    }
    val pickMerge = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.merge(uris)
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/x-matroska")
    ) { uri -> if (uri != null) vm.saveTo(uri) }

    LaunchedEffect(vm.saveRequest) {
        if (vm.saveRequest > 0) saveLauncher.launch(vm.suggestedName)
    }

    var showTrim by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }

    val shown = vm.tracks.filter {
        when (vm.filter) {
            Filter.ALL -> true
            Filter.AUDIO -> it.type == "audio"
            Filter.SUB -> it.type == "subtitle"
        }
    }

    Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(32.dp)) {
                        val p = Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close()
                        }
                        drawPath(p, Orange)
                    }
                    Spacer(Modifier.size(16.dp))
                    Text("MKV Editor", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            item {
                Button(
                    onClick = { pickMain.launch(arrayOf("*/*")) },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White)
                ) { Text("+   Agregar MKV", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile("▬", "Pistas", "Editar pistas", vm.filter == Filter.ALL, Modifier.weight(1f)) {
                            vm.filter = Filter.ALL
                        }
                        Tile("CC", "Subtítulos", "Añadir o editar", vm.filter == Filter.SUB, Modifier.weight(1f)) {
                            vm.filter = Filter.SUB
                        }
                        Tile("🔊", "Audios", "Gestionar audios", vm.filter == Filter.AUDIO, Modifier.weight(1f)) {
                            vm.filter = Filter.AUDIO
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile("✂", "Recortar", "Ajusta el área", vm.trimStart.isNotBlank() || vm.trimEnd.isNotBlank(), Modifier.weight(1f)) {
                            showTrim = true
                        }
                        Tile("▢", "Combinar", "Une varios MKV", false, Modifier.weight(1f)) {
                            if (!vm.busy) pickMerge.launch(arrayOf("*/*"))
                        }
                        Tile("⬡", "Más herramientas", "Metadatos", vm.globalTitle.isNotBlank() || vm.stripMetadata || vm.removeChapters, Modifier.weight(1f)) {
                            showTools = true
                        }
                    }
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardBg).padding(20.dp)
                ) {
                    Text(vm.fileName ?: "Ningún archivo seleccionado", fontSize = 18.sp, color = Color.White)
                }
            }
            item {
                if (vm.busy) {
                    if (vm.progress > 0f) {
                        LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth(), color = Orange)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Orange)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (vm.message.isEmpty()) "Agrega un archivo MKV para comenzar." else vm.message,
                    color = Muted, fontSize = 16.sp
                )
            }
            if (vm.tracks.isNotEmpty()) {
                item {
                    Text("PISTAS DETECTADAS", color = Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(shown, key = { it.uid }) { t -> TrackCard(t, vm) }
                item {
                    OutlinedButton(
                        onClick = { pickExtra.launch(arrayOf("*/*")) },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) { Text("+ Añadir subtítulo / audio externo", color = Orange) }
                }
                item {
                    Button(
                        onClick = { vm.export() },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White)
                    ) { Text("Aplicar cambios y guardar", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    if (vm.canSaveAgain() && !vm.busy) {
                        TextButton(onClick = { vm.requestSaveAgain() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Guardar de nuevo el último resultado", color = Orange)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showTrim) {
        AlertDialog(
            onDismissRequest = { showTrim = false },
            confirmButton = { TextButton(onClick = { showTrim = false }) { Text("Listo", color = Orange) } },
            dismissButton = {
                TextButton(onClick = { vm.trimStart = ""; vm.trimEnd = ""; showTrim = false }) { Text("Quitar recorte") }
            },
            title = { Text("Recortar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Formato HH:MM:SS (ej. 00:01:30). Déjalo vacío para no recortar. El corte se ajusta al fotograma clave más cercano.", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(vm.trimStart, { vm.trimStart = it }, label = { Text("Inicio") }, singleLine = true)
                    OutlinedTextField(vm.trimEnd, { vm.trimEnd = it }, label = { Text("Fin") }, singleLine = true)
                }
            }
        )
    }

    if (showTools) {
        AlertDialog(
            onDismissRequest = { showTools = false },
            confirmButton = { TextButton(onClick = { showTools = false }) { Text("Listo", color = Orange) } },
            title = { Text("Metadatos y herramientas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        vm.globalTitle, { vm.globalTitle = it },
                        label = { Text("Título del archivo") }, singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Borrar todos los metadatos", Modifier.weight(1f))
                        Switch(vm.stripMetadata, { vm.stripMetadata = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Eliminar capítulos", Modifier.weight(1f))
                        Switch(vm.removeChapters, { vm.removeChapters = it })
                    }
                    Text("Para borrar fuentes/adjuntos, elimínalos desde la lista de pistas.", color = Muted, fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
fun Tile(icon: String, title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .height(120.dp)
            .clip(shape)
            .background(CardBg)
            .then(if (selected) Modifier.border(2.dp, Orange, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 26.sp, color = Orange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Text(subtitle, fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

private fun typeLabel(t: String) = when (t) {
    "video" -> "Video"
    "audio" -> "Audio"
    "subtitle" -> "Subtítulo"
    "attachment" -> "Adjunto"
    else -> "Datos"
}

@Composable
fun TrackCard(t: Track, vm: MainViewModel) {
    val editable = t.type == "video" || t.type == "audio" || t.type == "subtitle"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val origin = if (t.origin.isEmpty()) "" else " · externo"
                Text("${typeLabel(t.type)} #${t.streamIndex}$origin", color = Orange, fontWeight = FontWeight.Bold)
                val sub = listOf(t.codec, t.detail, if (t.type == "attachment") t.title else "")
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Text(sub, color = Muted, fontSize = 13.sp)
                if (t.origin.isNotEmpty()) Text(t.origin, color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { vm.updateTrack(t.uid) { it.copy(keep = !it.keep) } }) {
                Text(if (t.keep) "Eliminar" else "Restaurar", color = if (t.keep) Color(0xFFFF5252) else Orange)
            }
        }
        if (t.keep && editable) {
            OutlinedTextField(
                value = t.newTitle,
                onValueChange = { v -> vm.updateTrack(t.uid) { it.copy(newTitle = v) } },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = t.newLanguage,
                    onValueChange = { v -> vm.updateTrack(t.uid) { it.copy(newLanguage = v.take(3).lowercase()) } },
                    label = { Text("Idioma (spa, eng…)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (t.type != "video") {
                    Spacer(Modifier.size(8.dp))
                    Checkbox(
                        checked = t.newDefault,
                        onCheckedChange = { v -> vm.updateTrack(t.uid) { it.copy(newDefault = v) } }
                    )
                    Text("Por defecto", fontSize = 13.sp)
                }
            }
        }
    }
}
