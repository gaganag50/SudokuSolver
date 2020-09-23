package com.example.sudokusolver.helpers

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

fun Mat.prepareImageForOcr(): Mat {
    val ret = this.clone()
    val kernel = Mat.ones(Size(3.0, 3.0), CvType.CV_8U)
    val cols_to_remove = (ret.cols() * 0.05).toInt()
    val rows_to_remove = (ret.rows() * 0.05).toInt()
    val retfinal = ret.submat(
        rows_to_remove,
        ret.rows() - rows_to_remove,
        cols_to_remove,
        ret.cols() - cols_to_remove
    )
    Imgproc.erode(retfinal, retfinal, kernel, Point(), 1)
    return retfinal
}