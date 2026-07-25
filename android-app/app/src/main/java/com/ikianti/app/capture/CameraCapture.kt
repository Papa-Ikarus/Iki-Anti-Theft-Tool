package com.ikianti.app.capture

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import java.io.File

/**
 * TODO: Für echten Betrieb ohne sichtbare Activity braucht CameraX
 * einen LifecycleOwner. Für einen reinen Hintergrund-Service empfiehlt
 * sich stattdessen die Camera2 API direkt mit einem HandlerThread,
 * oder ein unsichtbarer Trampoline-LifecycleOwner. Dies hier ist ein
 * vereinfachtes Grundgerüst, das den Ablauf zeigt.
 */
class CameraCapture(private val context: Context) {

    fun captureAndUpload(onDone: () -> Unit) {
        val outputFile = File(context.cacheDir, "snapshot_${System.currentTimeMillis()}.jpg")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageCapture = ImageCapture.Builder().build()

            // TODO: Ohne LifecycleOwner muss hier ein eigener
            // LifecycleOwner (z.B. ProcessLifecycleOwner Workaround)
            // gebunden werden, bevor cameraProvider.bindToLifecycle(...)
            // aufgerufen werden kann.

            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        uploadFile(outputFile, onDone)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        onDone()
                    }
                }
            )
        }, ContextCompat.getMainExecutor(context))
    }

    private fun uploadFile(file: File, onDone: () -> Unit) {
        val ref = Firebase.storage.reference
            .child("devices/phone-1/photos/${file.name}")

        ref.putFile(Uri.fromFile(file))
            .addOnCompleteListener { onDone() }
    }
}
