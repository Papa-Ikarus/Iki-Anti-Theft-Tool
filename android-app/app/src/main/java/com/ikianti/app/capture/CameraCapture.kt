package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.ikianti.app.SupabaseApi
import java.io.File
import java.io.FileOutputStream

class CameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "CameraCapture"
        private const val IMG_WIDTH  = 1280
        private const val IMG_HEIGHT = 720
    }

    @SuppressLint("MissingPermission")
    fun captureAndUpload(onDone: () -> Unit) {
        val thread = HandlerThread("CameraCapture").also { it.start() }
        val handler = Handler(thread.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: run {
            Log.e(TAG, "Keine hintere Kamera gefunden")
            thread.quitSafely(); onDone(); return
        }

        val imageReader = ImageReader.newInstance(IMG_WIDTH, IMG_HEIGHT, ImageFormat.JPEG, 1)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {
                camera.createCaptureSession(listOf(imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(session: CameraCaptureSession) {
                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(imageReader.surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                set(CaptureRequest.JPEG_QUALITY, 85)
                            }.build()

                            imageReader.setOnImageAvailableListener({ reader ->
                                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                                try {
                                    val buffer = image.planes[0].buffer
                                    val bytes  = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                    val file   = File(context.cacheDir, "snapshot_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(file).use { it.write(bytes) }

                                    SupabaseApi.uploadFile(
                                        bucket   = "photos",
                                        path     = "phone-1/${file.name}",
                                        bytes    = bytes,
                                        mimeType = "image/jpeg"
                                    ) {
                                        file.delete()
                                        cleanup(camera, imageReader, thread)
                                        onDone()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Bild-Fehler", e)
                                    cleanup(camera, imageReader, thread); onDone()
                                } finally { image.close() }
                            }, handler)

                            session.capture(req, object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, f: CaptureFailure) {
                                    Log.e(TAG, "Capture fehlgeschlagen: ${f.reason}")
                                    cleanup(camera, imageReader, thread); onDone()
                                }
                            }, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Session-Konfiguration fehlgeschlagen")
                            cleanup(camera, imageReader, thread); onDone()
                        }
                    }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) { cleanup(camera, imageReader, thread); onDone() }
            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Kamera-Fehler: $error")
                cleanup(camera, imageReader, thread); onDone()
            }
        }, handler)
    }

    private fun cleanup(camera: CameraDevice, reader: ImageReader, thread: HandlerThread) {
        try { camera.close() } catch (_: Exception) {}
        try { reader.close() } catch (_: Exception) {}
        thread.quitSafely()
    }
}
