package com.hpcnc.cameraxfaceml

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.os.Environment
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class FaceAnalyzer(private val listener: FaceListener) : ImageAnalysis.Analyzer {

    var imgCount = 0
    private var isAnalyzing = AtomicBoolean(false)

    // High-accuracy landmark detection and face classification
    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()

    private fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        outputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }

    override fun analyze(imageProxy: ImageProxy) {



        val detector = FaceDetection.getClient(highAccuracyOpts)

        Log.d("FaceAna", "Start")
        if (isAnalyzing.get())
            return
        else{
            isAnalyzing.set(true)
            val mediaImage = imageProxy.image!!
            val image = mediaImage?.let { InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees) }
            val bitmap = toBitmap( imageProxy?.image!! )

            Log.d("FaceAna", "Start face detection")


            val result = detector.process(image)
                .addOnSuccessListener { faces ->
                    Thread{
                        Log.d("FaceAna", "Found face")
                        for (face in faces) {
                            var FaceBB = face.boundingBox
                            var faceBitmap = cropRectFromBitmap( bitmap , FaceBB , true )

                            val filePath: String =
                                Environment.getExternalStorageDirectory().absolutePath
                                    .toString() +
                                        "/faceOut"
                            val dir = File(filePath)
                            if (!dir.exists()) dir.mkdirs()
                            var filename = "faces$imgCount.png"
                            File(filePath, filename).writeBitmap(faceBitmap, Bitmap.CompressFormat.PNG, 100)
                            imgCount += 1
                        }

                    }.start()



                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
        }
    }

    private fun cropRectFromBitmap(source: Bitmap, rect: Rect , preRotate : Boolean ): Bitmap {
        return Bitmap.createBitmap(
            if ( preRotate ) rotateBitmap( source , 90f )!! else source,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate( angle )
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix , false )
    }

    private fun toBitmap( image : Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val yuv = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }



    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {

        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)

        var pos = 0

        //may need to flip the buffers if you get underflow exception

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize)
            pos += ySize

        } else {
            var yBufferPos = (width - rowStride).toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += (rowStride - width).toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride

        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel = vBuffer.get(1)
            vBuffer.put(1, 0.toByte())
            if (uBuffer.get(0).toInt() == 0) {
                vBuffer.put(1, 255.toByte())
                if (uBuffer.get(0).toInt() == 255) {
                    vBuffer.put(1, savePixel)
                    vBuffer.get(nv21, ySize, uvSize)
                    //Log.d("NV211",DataConverter.jsonify(nv21))
                    return nv21 // shortcut
                }
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer.get(vuPos)
                nv21[pos++] = uBuffer.get(vuPos)
            }
        }
        //Log.d("NV212",DataConverter.jsonify(nv21))
        return nv21
    }

}
