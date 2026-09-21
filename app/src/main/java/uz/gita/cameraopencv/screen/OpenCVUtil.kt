package uz.gita.cameraopencv.screen

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc

object OpenCVUtil {
    fun getMatFromImage(image: ImageProxy): Mat {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)          // NV21: Y + V + U
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuv = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuv.put(0, 0, nv21)//NV21

        val rgb = Mat()
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21, 3)
        yuv.release()

        return rgb
    }

    fun fixMatRotation(rotationDegrees: Int, src: Mat): Mat {
        return when (rotationDegrees) {
            90 -> {
                val dst = Mat()
                Core.transpose(src, dst)
                Core.flip(dst, dst, 1) // clockwise
                dst
            }

            270 -> {
                val dst = Mat()
                Core.transpose(src, dst)
                Core.flip(dst, dst, 0)
                dst
            }

            180 -> {
                val dst = Mat()
                Core.flip(src, dst, -1)
                dst
            }

            else -> src
        }
    }



    fun warpSheet(src: Mat, orderedCorners: List<Point>): Mat {
        // orderedCorners: TL, TR, BR, BL
        val width = src.width().toDouble()
        val height = src.height().toDouble()
        val srcPts = MatOfPoint2f(*orderedCorners.toTypedArray())
        val dstPts = MatOfPoint2f(Point(0.0, 0.0), Point(width - 1, 0.0), Point(width - 1, height - 1), Point(0.0, height - 1))
        val matrix = Geometry.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat()
        Imgproc.warpPerspective (src, warped, matrix, src.size())
        matrix.release()
        srcPts.release()
        dstPts.release()
        return warped
    }

    fun Mat.hasSquares(): Boolean {
        val height = this.height()
        val width = this.width()

        val corners = listOf(
            this.submat(0, height / 5, 0, width / 4),                          // TL
            this.submat(0, height / 5, width * 3 / 4, width),                  // TR
            this.submat(height * 4 / 5, height, width * 3 / 4, width),         // BR
            this.submat(height * 4 / 5, height, 0, width / 4)                  // BL
        )

        var count = 0

        for (corner in corners) {
            val gray = Mat()
            Imgproc.cvtColor(corner, gray, Imgproc.COLOR_RGB2GRAY)

            val dark = Mat()
            Imgproc.threshold(gray, dark, 80.0, 255.0, Imgproc.THRESH_BINARY_INV)

            val ratio = Core.countNonZero(dark).toDouble() / (corner.width() * corner.height())
            if (ratio > 0.03 && ratio < 0.40) count++

            gray.release()
            dark.release()
            corner.release()
        }

        return count == 4
    }

    fun Mat.findShape(): MatOfPoint? {
        val gray = Mat()
        Imgproc.cvtColor(this, gray, Imgproc.COLOR_RGB2GRAY)

        val binary = Mat()
        Imgproc.threshold(gray, binary, 200.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binary, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        hierarchy.release()

        val minArea = this.width() * this.height() * 0.02
        val biggest = contours
            .filter { Geometry.contourArea(it) > minArea }
            .maxByOrNull { Geometry.contourArea(it) }

        val result = if (biggest == null) null else MatOfPoint(*biggest.toArray())

        contours.forEach { it.release() }
        gray.release()
        binary.release()

        return result
    }

    fun getCenterColorName(mat: Mat): String {
        val height = mat.height()
        val width = mat.width()

        val center = mat.submat(
            height * 2 / 5, height * 3 / 5,
            width * 2 / 5, width * 3 / 5
        )

        val mean = Core.mean(center)
        center.release()

        val red = mean.`val`[0]
        val green = mean.`val`[1]
        val blue = mean.`val`[2]


        return when {
            red > 200 && green > 200 && blue > 200 -> "White"
            red < 70 && green < 70 && blue < 70 -> "Black"
            red > 150 && green > 150 && blue < 100 -> "Yellow"
            red > 150 && green < 100 && blue < 100 -> "Red"
            green > 100 && red < 150 && blue < 100 -> "Green"
            blue > 150 && red < 100 -> "Blue"
            else -> "Unknown"
        }
    }
}

fun Mat.toBitmap(): Bitmap {
    val bitmap = createBitmap(this.width(), this.height())
    Utils.matToBitmap(this, bitmap)
    return bitmap
}