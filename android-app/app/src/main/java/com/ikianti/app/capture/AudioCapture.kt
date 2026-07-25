package com.ikianti.app.capture

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import java.io.File

class AudioCapture(private val context: Context) {

    fun recordAndUpload(seconds: Int, onDone: () -> Unit) {
        val outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")

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
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                recorder.stop()
            } catch (_: Exception) {
                // Aufnahme evtl. zu kurz - Datei trotzdem hochladen falls vorhanden
            }
            recorder.release()
            uploadFile(outputFile, onDone)
        }, seconds * 1000L)
    }

    private fun uploadFile(file: File, onDone: () -> Unit) {
        val ref = Firebase.storage.reference
            .child("devices/phone-1/audio/${file.name}")

        ref.putFile(Uri.fromFile(file))
            .addOnCompleteListener { onDone() }
    }
}
