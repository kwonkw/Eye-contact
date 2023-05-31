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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {

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
    val detectionThresholdCount = 60 // 탐지 인식을 위한 탐지 횟수 임계값
    val detectedObjectsCount: HashMap<String, Int> = HashMap() // 탐지된 물체와 탐지 횟수를 저장하는 변수
    val detectedObjects: HashMap<String, Long> = HashMap() // 탐지된 물체와 해당 시간을 저장하는 변수
    val startTime: Long = 0 // 시작 시간 변수
    private val ttsQueue: Queue<String> = LinkedList<String>() // TTS 큐
//    var cropLeft = 200  // 왼쪽
//    var cropTop = 800  // 위쪽
//    var cropWidth = 700 // 가로 크기
//    var cropHeight = 1100 // 세로 크기
//    val cropLeft = 0  //  왼쪽
//    val cropTop = 0  // 위쪽
//    val cropWidth = 0 // 가로 크기
//    val cropHeight = 0

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
                val ratio = 3f / 4f
                val width = p1
                val height = (width / ratio).toInt()
                textureView.layoutParams.width = width
                textureView.layoutParams.height = height

                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            @SuppressLint("SetTextI18n")
            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val originalWidth = bitmap.width
                val originalHeight = bitmap.height

                val cropLeft = originalWidth / 4
                val cropTop = originalHeight / 3
                val cropWidth = originalWidth / 2
                val cropHeight = originalHeight * 2/3

                // 크롭된 이미지 생성
                val croppedBitmap =
                    Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
                var croppedImage = TensorImage.fromBitmap(croppedBitmap)
                croppedImage = imageProcessor.process(croppedImage)

                val outputs = model.process(croppedImage)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                var mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)

                val h = mutableBitmap.height
                val w = mutableBitmap.width
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
                    detectedObjects.remove(disappearedObject)
                    val detectionCount = detectedObjectsCount[disappearedObject]!!
                    detectedObjectsCount.remove(disappearedObject) // 이전 탐지 횟수 맵에서 삭제
                    if (detectionCount > detectionThresholdCount) {
                        speakText(disappearedObject + "통과.")
                        Log.d("DetectionCount", "$disappearedObject: $detectionCount")
                    }
                }

                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        val left = locations[x + 1] * cropWidth + cropLeft
                        val top = locations[x] * cropHeight + cropTop
                        val right = locations[x + 3] * cropWidth + cropLeft
                        val bottom = locations[x + 2] * cropHeight + cropTop
                        canvas.drawRect(
                            RectF(
                                left,
                                top,
                                right,
                                bottom
                            ), paint
                        )
                        paint.style = Paint.Style.FILL
                        val label = labels[classes[index].toInt()]
                        canvas.drawText(
                            "$label $fl",
                            left,
                            top,
                            paint
                        )

                        updateDetectedObjectCount(label, currentTime) // 탐지된 물체의 탐지 횟수 업데이트
                    }
                }

                // 크롭된 영역 그리기
                val cropRect = RectF(
                    cropLeft.toFloat(),
                    cropTop.toFloat(),
                    (cropLeft + cropWidth).toFloat(),
                    (cropTop + cropHeight).toFloat()
                )
                paint.color = Color.RED
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                canvas.drawRect(cropRect, paint)

                // 결과 이미지를 ImageView에 표시
                runOnUiThread {
                    imageView.setImageBitmap(mutableBitmap)
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        textToSpeech = TextToSpeech(this, this)
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

            detectedObjects[detectedObject] = currentTime
            val detectionCount = detectedObjectsCount[detectedObject]!! + 1
            detectedObjectsCount[detectedObject] = detectionCount
            Log.d("DetectionCount", "$detectedObject: $detectionCount")

            if (detectionCount == detectionThresholdCount) {
                addTextToQueue(detectedObject + "발견.")
            }
        } else {
            // New object detected
            detectedObjects[detectedObject] = currentTime
            detectedObjectsCount[detectedObject] = 1
        }
    }

    private fun addTextToQueue(text: String) {
        ttsQueue.add(text)
        processTtsQueue()
    }

    private fun processTtsQueue() {
        if (ttsQueue.isNotEmpty() && !textToSpeech.isSpeaking) {
            val text = ttsQueue.poll()
            speakText(text)
        }
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setOnUtteranceCompletedListener(this)
            textToSpeech.language = Locale.KOREAN
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    override fun onUtteranceCompleted(utteranceId: String?) {
        processTtsQueue()
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

    var lastTimeBackPressed: Long = 0
    override fun onBackPressed() {
        if (System.currentTimeMillis() - lastTimeBackPressed >= 1500) {
            lastTimeBackPressed = System.currentTimeMillis()
            Toast.makeText(this, "'뒤로' 버튼을 한번 더 누르시면 종료됩니다.", Toast.LENGTH_LONG).show()
        } else {
            finish()
        }
    }
}
