package com.hpcnc.cameraxfaceml

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer



class FaceAnalyzer(private val listener: FaceListener) : ImageAnalysis.Analyzer {

    // High-accuracy landmark detection and face classification
    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }
    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val detector = FaceDetection.getClient(highAccuracyOpts)

            val result = detector.process(image)
                .addOnSuccessListener { faces ->
                    for (face in faces) {
                        val bounds = face.boundingBox

                    }

                }
                .addOnFailureListener {
                    ;
                }

        }
    }

//    override fun analyze(image: ImageProxy) {
//
//        val buffer = image.planes[0].buffer
//        val inputImage = InputImage.fromByteBuffer(
//            buffer,
//            /* image width */ 1280,
//            /* image height */ 720,
//            image.imageInfo.rotationDegrees,
//            InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
//        )

//        val data = buffer.toByteArray()
//        val pixels = data.map { it.toInt() and 0xFF }
//        val arr = FloatArray(12)
//        listener(arr)
//        image.close()
//    }
}
