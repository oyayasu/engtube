package com.oyayasu.engtube

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Uses only the official YouTube IFrame player. Does not capture, download,
 * extract, or hide advertising or player controls. Availability depends on
 * whether the video allows embedding.
 */
@Composable
internal fun YouTubeClipPlayer(videoId: String, startMs: Long, endMs: Long) {
    val context = LocalContext.current
    var repeat by remember(videoId) { mutableStateOf(true) }
    val start = startMs.coerceAtLeast(0).toDouble() / 1000.0
    val end = endMs.coerceAtLeast(startMs + 500).toDouble() / 1000.0
    val first = floor(start).toInt()
    val last = ceil(end).toInt()
    val loopLiteral = if (repeat) "true" else "false"
    val browser = remember(videoId, startMs, endMs, repeat) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body,#p{width:100%;height:100%;margin:0;overflow:hidden;background:#111}</style>
                </head><body><div id="p"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                 var clipPlayer=null;
                 var clipStart=$start;
                 var clipEnd=$end;
                 function onYouTubeIframeAPIReady(){
                   clipPlayer=new YT.Player('p',{
                     width:'100%',height:'100%',videoId:'$videoId',
                     playerVars:{playsinline:1,start:$first,end:$last,origin:'https://www.youtube.com'},
                     events:{onReady:function(e){e.target.seekTo(clipStart,false);},
                     onStateChange:function(e){
                       if(e.data===YT.PlayerState.ENDED && $loopLiteral){
                         e.target.seekTo(clipStart,true);e.target.playVideo();
                       }
                     }}
                   });
                 }
                 setInterval(function(){
                   if(!clipPlayer || typeof clipPlayer.getPlayerState!=='function')return;
                   if(clipPlayer.getPlayerState()===YT.PlayerState.PLAYING &&
                      clipPlayer.getCurrentTime()>=clipEnd){
                     if($loopLiteral){clipPlayer.seekTo(clipStart,true);}
                     else{clipPlayer.pauseVideo();}
                   }
                 },100);
                </script></body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(browser) {
        onDispose { browser.stopLoading(); browser.destroy() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Source video · selected section", style = MaterialTheme.typography.labelLarge)
        Surface(shape = RoundedCornerShape(16.dp)) {
            AndroidView(factory = { browser }, modifier = Modifier.fillMaxWidth().height(210.dp))
        }
        FilterChip(selected = repeat, onClick = { repeat = !repeat }, label = {
            Text(if (repeat) "A–B loop enabled" else "A–B loop disabled")
        })
        Text("Plays through YouTube. Segment looping depends on the embed being available; no media is saved.",
            style = MaterialTheme.typography.bodySmall)
    }
}
