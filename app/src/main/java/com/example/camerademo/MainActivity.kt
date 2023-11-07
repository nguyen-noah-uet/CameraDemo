package com.example.camerademo

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.VideoCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity(), ImageAnalysis.Analyzer, View.OnClickListener {
    private var frameSkipCounter: Int = 0
    private lateinit var bRecord: Button
    private lateinit var bCapture: Button
    private lateinit var graySwitch: SwitchCompat
    private lateinit var grayView: ImageView
    private lateinit var previewView: PreviewView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 0
            )
        }
        setContentView(R.layout.activity_main)
        previewView = findViewById<PreviewView>(R.id.previewView)
        grayView = findViewById<ImageView>(R.id.grayView)

        grayView.visibility = View.GONE
        graySwitch = findViewById<SwitchCompat>(R.id.grayscaleSwitch)
        graySwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                grayView.visibility = View.VISIBLE
            } else {
                grayView.visibility = View.INVISIBLE
            }
        }
        bCapture = findViewById<Button>(R.id.bCapture)
        bRecord = findViewById<Button>(R.id.bRecord)
        bRecord.setText("start recording") // Set the initial text of the button


        bCapture.setOnClickListener(this)
        bRecord.setOnClickListener(this)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                startCameraX(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, getExecutor())
    }

    private fun getExecutor(): Executor {
        return ContextCompat.getMainExecutor(this)
    }

    @SuppressLint("RestrictedApi")
    private fun startCameraX(cameraProvider: ProcessCameraProvider) {
        cameraProvider.unbindAll()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val preview = Preview.Builder()
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // Image capture use case
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Video capture use case
        videoCapture = VideoCapture.Builder()
            .setVideoFrameRate(30)
            .build()

        // Image analysis use case
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(getExecutor(), this)

        //bind to lifecycle:
        cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        )

    }

    override fun analyze(image: ImageProxy) {
        Log.d("TAG", frameSkipCounter.toString())
        if(frameSkipCounter % 1 == 0) {
            // image processing here for the current frame
            Log.d("TAG", "analyze: got the frame at: " + image.imageInfo.timestamp)

            val bitmap = previewView.bitmap



            if (bitmap == null) return

            val bitmap1: Bitmap = toGrayscale(bitmap)

            runOnUiThread { grayView.setImageBitmap(bitmap1) }
        }
        frameSkipCounter++
        image.close()
    }
    private fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
        val height: Int = bmpOriginal.height
        val width: Int = bmpOriginal.width
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        c.drawBitmap(bmpOriginal, 0f, 0f, paint)
        return bmpGrayscale
    }

    @SuppressLint("RestrictedApi")
    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.bCapture -> capturePhoto()
            R.id.bRecord -> if (bRecord.text === "start recording") {
                bRecord.text = "stop recording"
                recordVideo()
            } else {
                bRecord.text = "start recording"
                videoCapture.stopRecording()
            }
        }
    }

    private fun capturePhoto() {
        val timestamp = System.currentTimeMillis()
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build(),
            getExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        this@MainActivity,
                        "Photo has been saved successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error saving photo: " + exception.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    @SuppressLint("RestrictedApi")
    private fun recordVideo() {
        if (videoCapture != null) {
            val timestamp = System.currentTimeMillis()
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                videoCapture.startRecording(
                    VideoCapture.OutputFileOptions.Builder(
                        contentResolver,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ).build(),
                    getExecutor(),
                    object : VideoCapture.OnVideoSavedCallback {
                        override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                            Toast.makeText(
                                this@MainActivity,
                                "Video has been saved successfully.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onError(
                            videoCaptureError: Int,
                            message: String,
                            cause: Throwable?
                        ) {
                            Toast.makeText(
                                this@MainActivity,
                                "Error saving video: $message",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

}