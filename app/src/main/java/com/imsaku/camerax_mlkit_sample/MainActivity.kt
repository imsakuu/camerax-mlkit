package com.imsaku.camerax_mlkit_sample

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private var previewView: PreviewView? = null
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    // camera usecases
    private var previewUseCase: Preview? = null
    private var barcodeScanUseCase: ImageAnalysis? = null
    private var captureUseCase: ImageCapture? = null

    private lateinit var outputDirectory: File

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = view_finder

        if (allPermissionsGranted()) {
            cameraSelector =
                CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
            bindPreviewAndCaptureUseCase()
            bindBarcodeScanUseCase()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        camera_capture_button.setOnClickListener { takePhoto() }
        outputDirectory = getOutputDirectory()

    }

    @ExperimentalGetImage
    private fun bindBarcodeScanUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (barcodeScanUseCase != null) {
            cameraProvider!!.unbind(barcodeScanUseCase)
        }
        if (captureUseCase != null) {
            cameraProvider!!.unbind(captureUseCase)
        }

        val scanner = BarcodeScanning.getClient()
        val builder = ImageAnalysis.Builder()
        barcodeScanUseCase = builder.build()
        barcodeScanUseCase?.setAnalyzer(
            ContextCompat.getMainExecutor(this),
            ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                try {
                    val inputImage = InputImage.fromMediaImage(
                        imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isEmpty()) {
                                Log.d(TAG, "NO Barcode was detected")
                            } else {
                                Log.d(TAG, "Barcode was detected")
                            }
                            imageProxy.close()
                        }
                        .addOnFailureListener {
                            Log.d(TAG, "Scan failed")
                            imageProxy.close()
                        }
                } catch (e: MlKitException) {
                    Log.e("", e.localizedMessage!!)
                }
            }
        )
        cameraProvider!!.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, barcodeScanUseCase)
    }

    private fun bindPreviewAndCaptureUseCase() {
        val cameraProviderFeature = ProcessCameraProvider.getInstance(this)
        cameraProvider = cameraProviderFeature.get()

        cameraProviderFeature.addListener(Runnable {
            if (previewUseCase != null) {
                cameraProvider!!.unbind(previewUseCase)
            }
            previewUseCase = Preview.Builder().build()
            captureUseCase = ImageCapture.Builder().build()
            previewUseCase!!.setSurfaceProvider(previewView!!.createSurfaceProvider())
            cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase, captureUseCase)
        },
        ContextCompat.getMainExecutor(applicationContext))
    }

    private fun takePhoto() {
        val imageCapture = captureUseCase ?: return

        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()
                ).format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    }

                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val saveUri = Uri.fromFile(photoFile)
                        val msg = "Photo capture succeeded: $saveUri"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)
                    }
                }
        )
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdir() }
        }
        return if (mediaDir != null && mediaDir.exists()) {
            mediaDir
        } else {
            filesDir
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                bindPreviewAndCaptureUseCase()
                bindBarcodeScanUseCase()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXSample"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}