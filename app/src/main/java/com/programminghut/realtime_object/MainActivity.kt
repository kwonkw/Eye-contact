package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var labels: List<String>
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1

    lateinit var textToSpeech: TextToSpeech
    val detectionThresholdCount = 30 // 탐지 인식을 위한 탐지 횟수 임계값
    val detectedObjectsCount: HashMap<String, Int> = HashMap() // 탐지된 물체와 탐지 횟수를 저장하는 변수
    val detectedObjects: HashMap<String, Long> = HashMap() // 탐지된 물체와 해당 시간을 저장하는 변수
    var startTime: Long = 0 // 시작 시간 변수
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                startTime = System.currentTimeMillis() // 시작 시간 저장
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            @SuppressLint("SetTextI18n")
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x = 0

                val currentTime = System.currentTimeMillis()

                // 사라진 물체 찾기 및 음성 출력
                val disappearedObjects = ArrayList<String>()
                for (detectedObject in detectedObjects.keys) {
                    if (currentTime - detectedObjects[detectedObject]!! >= 3000) {
                        disappearedObjects.add(detectedObject)
                    }
                }
                for (disappearedObject in disappearedObjects) {
                    speakText(disappearedObject + "가 사라졌습니다.")
                    detectedObjects.remove(disappearedObject)
                    detectedObjectsCount.remove(disappearedObject) // 이전 탐지 횟수 맵에서 삭제
                }

                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            RectF(
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                locations.get(x + 3) * w,
                                locations.get(x + 2) * h
                            ), paint
                        )
                        paint.style = Paint.Style.FILL
                        val label = labels.get(classes.get(index).toInt())
                        canvas.drawText(
                            label + " " + fl.toString(),
                            locations.get(x + 1) * w,
                            locations.get(x) * h,
                            paint
                        )

                        updateDetectedObjectCount(label, currentTime) // 탐지된 물체의 탐지 횟수 업데이트
                    }
                }

                imageView.setImageBitmap(mutable)

                // 탐지된 물체 인식 음성 출력
//                speakRecognizedObjects()
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.KOREAN
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {

            }

            override fun onError(p0: CameraDevice, p1: Int) {

            }
        }, handler)
    }

    fun get_permission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    private fun updateDetectedObjectCount(detectedObject: String, currentTime: Long) {
        if (detectedObjects.containsKey(detectedObject)) {
            // Object already detected before
            val lastDetectionTime = detectedObjects[detectedObject]!!
            val timeElapsed = currentTime - lastDetectionTime

            if (timeElapsed >= 3000) {
                // Object not detected for 3 seconds, remove from the map
                detectedObjects.remove(detectedObject)
                detectedObjectsCount.remove(detectedObject)
                speakText(detectedObject + "가 사라졌습니다.")
            } else {
                // Object detected within 3 seconds, update the detection time
                detectedObjects[detectedObject] = currentTime
                val detectionCount = detectedObjectsCount[detectedObject]!! + 1
                detectedObjectsCount[detectedObject] = detectionCount

                Log.d("DetectionCount", "$detectedObject: $detectionCount")

                if (detectionCount == detectionThresholdCount) {
                    speakText(detectedObject + "를 인식했습니다.")
                }
            }
        } else {
            // New object detected
            detectedObjects[detectedObject] = currentTime
            detectedObjectsCount[detectedObject] = 1

        }
    }






    private fun speakRecognizedObjects() {
        for (detectedObject in detectedObjects.keys) {
            val detectionCount = detectedObjectsCount[detectedObject]!!
            if (detectionCount == 1) {
                // 탐지 횟수가 1인 경우에만 음성 출력
                speakText(detectedObject + "를 인식했습니다.")
            }
        }
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }
}
