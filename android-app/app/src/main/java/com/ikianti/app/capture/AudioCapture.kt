package com.ikianti.app.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ikianti.app.SupabaseApi
import java.io.File

class AudioCapture(private val context: Context) {

    fun recordAndUpload(seconds: Int, onDone: () -> Unit) {
        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")

        @Suppress("DEPRECATION")
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()

            if (file.exists() && file.length() > 0) {
                SupabaseApi.uploadFile(
                    bucket   = "audio",
                    path     = "phone-1/${file.name}",
                    bytes    = file.readBytes(),
                    mimeType = "audio/mp4"
                ) {
                    file.delete()
                    onDone()
                }
            } else {
                Log.w("AudioCapture", "Audiodatei leer oder nicht vorhanden")
                onDone()
            }
        }, seconds * 1000L)
    }
}
