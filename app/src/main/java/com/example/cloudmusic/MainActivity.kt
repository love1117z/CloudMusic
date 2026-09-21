package com.example.cloudmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Song(val id: Long, val title: String, val artist: String, val album: String, val previewUrl: String?)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent { CloudMusicApp(player) }
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

private suspend fun searchMusic(text: String): List<Song> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(text, "UTF-8")
    val conn = (URL("https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=30&country=CN").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000
    }
    try {
        val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val arr = root.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("trackName").ifBlank { continue }
                add(Song(o.optLong("trackId", i.toLong()), title, o.optString("artistName", "未知歌手"), o.optString("collectionName", "单曲"), o.optString("previewUrl").ifBlank { null }))
            }
        }
    } finally { conn.disconnect() }
}

@Composable
fun CloudMusicApp(player: ExoPlayer) {
    var query by remember { mutableStateOf("周杰伦 花海") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var searchTick by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(searchTick) {
        loading = true; error = ""
        try { songs = searchMusic(query); if (songs.isEmpty()) error = "没有找到结果" }
        catch (_: Exception) { error = "搜索失败，请检查网络连接"; songs = emptyList() }
        finally { loading = false }
    }

    fun play(song: Song) {
        val url = song.previewUrl
        if (url.isNullOrBlank()) { error = "这个结果没有可播放试听音源"; return }
        if (current?.id != song.id) { player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); current = song }
        player.play(); playing = true
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE8435A), background = Color(0xFF0F1014), surface = Color(0xFF17181D))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F1014))) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("云音", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("音乐测试版", color = Color.Gray, fontSize = 13.sp) }
                    Icon(Icons.Default.AccountCircle, null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("搜索歌曲、歌手…") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { IconButton(onClick = { if (query.isNotBlank()) searchTick++ }) { Icon(Icons.Default.ArrowForward, "搜索") } },
                    shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1B20), unfocusedContainerColor = Color(0xFF1A1B20), focusedBorderColor = Color(0xFFE8435A), unfocusedBorderColor = Color.Transparent)
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFE8435A))
                Text(if (query.isBlank()) "推荐音乐" else "搜索结果", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 14.dp))
                if (!loading && songs.isEmpty()) Text(error, color = Color.Gray, modifier = Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = if (current != null) 100.dp else 20.dp)) {
                    items(songs) { song -> SongRow(song, song.id == current?.id && playing) {
                        error = ""; play(song)
                    } }
                }
            }
            current?.let { song ->
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp, color = Color(0xFF202127)) {
                    Row(Modifier.padding(12.dp), Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(Color(0xFF34353D), RoundedCornerShape(9.dp)), Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Color(0xFFE8435A)) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Color.Gray, fontSize = 12.sp) }
                        IconButton(onClick = { if (playing) { player.pause(); playing = false } else play(song) }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, playing: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF3A2630), Color(0xFF24252C))), RoundedCornerShape(10.dp)), Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = if (playing) Color(0xFFE8435A) else Color.Gray) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${song.album}", color = Color(0xFF8D8F99), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (playing) Icons.Default.VolumeUp else Icons.Default.PlayArrow, null, tint = Color(0xFFB9BAC1))
    }
}
