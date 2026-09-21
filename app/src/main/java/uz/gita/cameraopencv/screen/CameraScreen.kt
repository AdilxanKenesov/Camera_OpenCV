package uz.gita.cameraopencv.screen

import android.os.Bundle
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import dev.androidbroadcast.vbpd.viewBinding
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import uz.gita.cameraopencv.R
import uz.gita.cameraopencv.databinding.CameraScreenBinding
import uz.gita.cameraopencv.screen.OpenCVUtil.findShape
import uz.gita.cameraopencv.screen.OpenCVUtil.getCenterColorName
import uz.gita.cameraopencv.screen.OpenCVUtil.hasSquares
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraScreen : Fragment(R.layout.camera_screen) {

    private val viewBinding by viewBinding(CameraScreenBinding::bind)
    private var imageAnalyzerExecutor: ExecutorService? = null
    @Volatile
    private var isFind = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        imageAnalyzerExecutor = Executors.newSingleThreadExecutor()
        startCamera(previewView = viewBinding.previewView)
    }

    override fun onResume() {
        super.onResume()
        isFind = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageAnalyzerExecutor?.shutdown()
    }
    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder()
                .build()
                .apply { surfaceProvider = previewView.surfaceProvider }

            val cameraAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(imageAnalyzerExecutor!!, imageAnalyzer) }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner = viewLifecycleOwner,
                cameraSelector = cameraSelector,
                preview,
                cameraAnalyzer
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    private val imageAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        try {
            if (!isFind) analyzeFrame(imageProxy)
        } finally {
            imageProxy.close()
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val opencvImage = OpenCVUtil.getMatFromImage(imageProxy)
        val srcImage = OpenCVUtil.fixMatRotation(
            imageProxy.imageInfo.rotationDegrees,
            opencvImage
        )
        if (srcImage !== opencvImage) opencvImage.release()

        val grayImage = Mat()
        Imgproc.cvtColor(srcImage, grayImage, Imgproc.COLOR_RGB2GRAY)

        val blurImage = Mat()
        Imgproc.GaussianBlur(grayImage, blurImage, Size(5.0, 5.0), 0.0)
        grayImage.release()

        val edges = Mat()
        Imgproc.Canny(blurImage, edges, 75.0, 200.0)
        blurImage.release()

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        edges.release()
        hierarchy.release()

        val quads = contours.map { contour ->
            val curve = MatOfPoint2f(*contour.toArray())
            val perimeter = Geometry.arcLength(curve, true)
            val approx = MatOfPoint2f()
            Geometry.approxPolyDP(curve, approx, perimeter * 0.02, true)
            val polygon = MatOfPoint(*approx.toArray())
            curve.release()
            approx.release()
            contour.release()
            polygon
        }
        val bigContour = quads
            .filter { it.total() == 4L }
            .maxByOrNull { Geometry.contourArea(it) }
        val bigCorners = bigContour?.toList()
        quads.forEach { it.release() }

        if (bigCorners != null) {
            val orderedCorners = sortCorners(bigCorners)
            val wrappedImage = OpenCVUtil.warpSheet(srcImage, orderedCorners)

            if (wrappedImage.hasSquares()) {
                val height = wrappedImage.height()
                val width = wrappedImage.width()

                val imageShape = wrappedImage.submat(height / 6, height * 5 / 6, width / 5, width * 4 / 5)
                val contoursShape = imageShape.findShape()

                if (contoursShape != null) {
                    isFind = true

                    Repository.color = getCenterColorName(imageShape)
                    Repository.shape = getShapeName(contoursShape)
                    contoursShape.release()

                    view?.post { openResult() }
                }
                imageShape.release()
            } else {
                val bitmap = wrappedImage.toBitmap()
                view?.post {
                    viewBinding.image.setImageBitmap(bitmap)
                }
            }
            wrappedImage.release()
        }

        srcImage.release()
    }

    private fun getShapeName(contour: MatOfPoint): String {
        val area = Geometry.contourArea(contour)

        val curve = MatOfPoint2f(*contour.toArray())
        val box = Geometry.minAreaRect(curve)

        curve.release()

        val boxArea = box.size.width * box.size.height
        val ratio = area / boxArea

//        Log.d("TTT", "area: $area  boxArea: $boxArea  ratio: $ratio")

        val toTriangle = abs(ratio - 0.50)
        val toCircle = abs(ratio - 0.785)
        val toRectangle = abs(ratio - 1.00)

        return when {
            toTriangle < toCircle && toTriangle < toRectangle -> "Triangle"
            toCircle < toTriangle && toCircle < toRectangle -> "Circle"
            else -> "Rectangle"
        }
    }

    private fun openResult() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.main, ResultScreen())
            .addToBackStack(null)
            .commit()
    }


    private fun sortCorners(points: List<Point>): List<Point> {
        val topLeft = points.minByOrNull { it.x + it.y }!!
        val bottomRight = points.maxByOrNull { it.x + it.y }!!
        val topRight = points.minByOrNull { it.y - it.x }!!
        val bottomLeft = points.maxByOrNull { it.y - it.x }!!
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }
}