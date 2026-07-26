package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.io.FileOutputStream

/**
 * Macht einen Kamera-Snapshot im Hintergrund (kein LifecycleOwner nötig)
 * über die Camera2 API direkt. Läuft in einem eigenen HandlerThread,
 * damit der Service-Thread nicht blockiert wird.
 *
 * Ablauf:
 *   1. HandlerThread starten
 *   2. Hintere Kamera öffnen (CameraManager)
 *   3. CaptureSession mit einem ImageReader aufbauen
 *   4. Einzelbild aufnehmen (STILL_CAPTURE)
 *   5. JPEG in den App-Cache schreiben
 *   6. Datei nach Firebase Storage hochladen
 *   7. Ressourcen freigeben, onDone() aufrufen
 */
class CameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "CameraCapture"
        private const val IMG_WIDTH = 1280
        private const val IMG_HEIGHT = 720
    }

    @SuppressLint("MissingPermission") // Permission wird in MainActivity angefragt
    fun captureAndUpload(onDone: () -> Unit) {
        val thread = HandlerThread("CameraCapture").also { it.start() }
        val handler = Handler(thread.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Hintere Kamera suchen (LENS_FACING_BACK)
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: run {
            Log.e(TAG, "Keine hintere Kamera gefunden")
            thread.quitSafely()
            onDone()
            return
        }

        val imageReader = ImageReader.newInstance(IMG_WIDTH, IMG_HEIGHT, ImageFormat.JPEG, 1)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {
                val surface = imageReader.surface

                camera.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(session: CameraCaptureSession) {
                            val captureRequest = camera.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                addTarget(surface)
                                // Autofokus und automatische Belichtung einschalten
                                set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON)
                                set(CaptureRequest.JPEG_QUALITY, 85)
                            }.build()

                            // Bild ist fertig -> speichern und hochladen
                            imageReader.setOnImageAvailableListener({ reader ->
                                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                                try {
                                    val buffer = image.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)

                                    val file = File(
                                        context.cacheDir,
                                        "snapshot_${System.currentTimeMillis()}.jpg"
                                    )
                                    FileOutputStream(file).use { it.write(bytes) }
                                    uploadFile(file) {
                                        cleanup(camera, imageReader, thread)
                                        onDone()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Fehler beim Speichern des Bildes", e)
                                    cleanup(camera, imageReader, thread)
                                    onDone()
                                } finally {
                                    image.close()
                                }
                            }, handler)

                            session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureFailed(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    failure: CaptureFailure
                                ) {
                                    Log.e(TAG, "Capture fehlgeschlagen: ${failure.reason}")
                                    cleanup(camera, imageReader, thread)
                                    onDone()
                                }
                            }, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "CaptureSession-Konfiguration fehlgeschlagen")
                            cleanup(camera, imageReader, thread)
                            onDone()
                        }
                    },
                    handler
                )
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Kamera getrennt")
                cleanup(camera, imageReader, thread)
                onDone()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Kamera-Fehler: $error")
                cleanup(camera, imageReader, thread)
                onDone()
            }
        }, handler)
    }

    private fun uploadFile(file: File, onDone: () -> Unit) {
        val ref = Firebase.storage.reference
            .child("devices/phone-1/photos/${file.name}")

        ref.putFile(Uri.fromFile(file))
            .addOnSuccessListener { Log.d(TAG, "Upload erfolgreich: ${file.name}") }
            .addOnFailureListener { Log.e(TAG, "Upload fehlgeschlagen", it) }
            .addOnCompleteListener { file.delete(); onDone() }
    }

    private fun cleanup(camera: CameraDevice, reader: ImageReader, thread: HandlerThread) {
        try { camera.close() } catch (_: Exception) {}
        try { reader.close() } catch (_: Exception) {}
        thread.quitSafely()
    }
}
