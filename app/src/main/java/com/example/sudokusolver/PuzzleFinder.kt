package com.example.sudokusolver

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import java.util.*


internal class PuzzleFinder(frame: Mat) {
    private val rgbc: Mat
    private var contour: List<MatOfPoint>
    private var puzzleNum: Int
    var wrap: Boolean
    private fun sortBasedOnArea(input: List<MatOfPoint>): List<MatOfPoint> {
        val area = arrayOfNulls<Double>(input.size)
        val ip = arrayOfNulls<MatOfPoint>(input.size)
        for (i in input.indices) {
            ip[i] = input[i]
        }
        for (i in input.indices) {
            val contourArea = Imgproc.contourArea(contour[i])
            area[i] = contourArea
        }
        for (i in 0 until input.size - 1) {
            var index = i
            for (j in i + 1 until input.size) if (area[j]!! > area[index]!!) index = j
            val smallerNumber = area[index]
            area[index] = area[i]
            area[i] = smallerNumber
            val smaller = ip[index]
            ip[index] = ip[i]
            ip[i] = smaller
        }
        return ArrayList<MatOfPoint>(Arrays.asList(*ip).subList(0, input.size))
    }

    private fun approx(cnt: MatOfPoint): MatOfPoint2f {
        val double_max_area_contours = MatOfPoint2f(*cnt.toArray())
        val peri = Imgproc.arcLength(double_max_area_contours, true)
        val app = MatOfPoint2f()
        Imgproc.approxPolyDP(double_max_area_contours, app, 0.01 * peri, true)
        return app
    }

    private fun get_rectangle_corners(img: MatOfPoint2f): List<Point> {
        var temp_double = img[0, 0]
        val p1 = Point(temp_double[0], temp_double[1])
        temp_double = img[1, 0]
        val p2 = Point(temp_double[0], temp_double[1])
        temp_double = img[2, 0]
        val p3 = Point(temp_double[0], temp_double[1])
        temp_double = img[3, 0]
        val p4 = Point(temp_double[0], temp_double[1])
        val source: MutableList<Point> = ArrayList()
        source.add(p1)
        source.add(p2)
        source.add(p3)
        source.add(p4)
        return source
    }

    private fun warp(inputMat: Mat, startM: Mat): Mat {
        val resultWidth = 1000
        val resultHeight = 1000
        val outputMat = Mat(resultWidth, resultHeight, CvType.CV_8UC4)
        val ocvPOut1 = org.opencv.core.Point(0.0, 0.0)
        val ocvPOut2 = Point(0.0, resultHeight.toDouble())
        val ocvPOut3 = Point(
            resultWidth.toDouble(),
            resultHeight.toDouble()
        )
        val ocvPOut4 = Point(resultWidth.toDouble(), 0.0)
        val dest: MutableList<Point> = ArrayList()
        dest.add(ocvPOut1)
        dest.add(ocvPOut2)
        dest.add(ocvPOut3)
        dest.add(ocvPOut4)
        val endM = Converters.vector_Point2f_to_Mat(dest)
        val perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM)
        Imgproc.warpPerspective(
            inputMat,
            outputMat,
            perspectiveTransform,
            Size(resultWidth.toDouble(), resultHeight.toDouble()),
            Imgproc.INTER_CUBIC
        )
        return outputMat
    }

    fun getPuzzle(wraped: Boolean): Mat {
        Log.d("In PuzzleFinder", "starting getPUzzle")
        contour = sortBasedOnArea(contour)
        val optimizedLen = if (contour.size < 5) contour.size else 5
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
        println("Optimized length is$optimizedLen")
        val temp = rgbc.clone()
        for (i in 0 until optimizedLen) {
            print("Countour is")
            println(approx(contour[i]).dump())
            print("First ele is")
            for (i1 in approx(contour[i])[0, 0].indices) println(approx(contour[i])[0, 0][i1])
            print("size is ")
            println(approx(contour[i]).rows())
            if (approx(contour[i]).total() == 4L) {
                puzzleNum = i
            }
        }
        println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
        Imgproc.drawContours(temp, contour, puzzleNum, Scalar(0.0, 255.0, 0.0), 2)
        if (puzzleNum != -1 && wraped) {
            val rect = approx(contour[puzzleNum])
            Log.d("In PuzzleFinder", "starting get_rect")
            val corners = get_rectangle_corners(rect)
            val startM = Converters.vector_Point2f_to_Mat(corners)
            val result = warp(temp, startM)
            wrap = true
            return result
        }
        return temp
    }

    init {
        rgbc = frame.clone()
        val temp = Mat()
        Imgproc.cvtColor(rgbc, temp, Imgproc.COLOR_BGR2GRAY)
        val rgbg = temp.clone()
        Imgproc.Canny(rgbg, temp, 150.0, 255.0)
        Imgproc.GaussianBlur(temp, temp, Size(5.0, 5.0), 0.0)
        val rgb_blur = temp.clone()
        contour = ArrayList()
        Imgproc.findContours(
            rgb_blur,
            contour,
            Mat(),
            Imgproc.CV_SHAPE_RECT,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        puzzleNum = -1
        wrap = false
    }
}
