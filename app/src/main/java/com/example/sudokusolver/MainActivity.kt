package com.example.sudokusolver

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {


    val CAMERA_REQUEST_CODE = 0
    private var digitClassifier = DigitClassifier(this)

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Toast.makeText(this, "Could not create file!", Toast.LENGTH_SHORT).show()
                    null

                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        it
                    )
                    Log.d(TAG, "dispatchTakePictureIntent: $photoURI")
                    //content: com.example.android.fileprovider/external_files/Pictures/JPEG_20200911_211611_7149317982996869606.jpg
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        OpenCVLoader.initDebug();
        cameraButton.setOnClickListener { dispatchTakePictureIntent() }


        digitClassifier
            .initialize()
            .addOnFailureListener { e -> Log.e(TAG, "Error to setting up digit classifier.", e) }
    }

    override fun onDestroy() {
        digitClassifier.close()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
/*                if(resultCode == Activity.RESULT_OK && data != null) {
                    photoImageView.setImageBitmap(data.extras.get("data") as Bitmap)
                }*/
                if (resultCode == Activity.RESULT_OK) {
//                    val extras = data!!.extras
//                    val imageBitmap = extras?.get("data") as Bitmap
//                    Log.d(TAG, "onActivityResult: ${extras}")
//                    Log.d(TAG, "onActivityResult: ${imageBitmap}")
//                    photoImageView.setImageBitmap(setScaledBitmap())
                    detectEdges(setScaledBitmap())
                }
            }
            else -> {
                Toast.makeText(this, "Unrecognized request code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    lateinit var currentPhotoPath: String

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!

        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }


    private fun dist(a: Point, b: Point): Double {
        val x1 = a.x
        val y1 = a.y
        val x2 = b.x
        val y2 = b.y
        return Math.sqrt(
            Math.pow(x2 - x1.toDouble(), 2.0) +
                    Math.pow(y2 - y1.toDouble(), 2.0) * 1.0
        )
    }

    private fun order_points(pts: Array<Point>): MatOfPoint2f {
        for (i in pts) {
            Log.d(TAG, "order_points: ${i.x} ${i.y}")
        }

        pts.sortBy { point -> point.x }
        for (i in pts) {
            Log.d(TAG, "order_points: ${i.x} ${i.y}")
        }
        val leftMost = pts.take(2).toTypedArray()
        val rightMost = pts.takeLast(2).toTypedArray()
        for (i in leftMost) {
            Log.d(TAG, "order_points: ${i.x} ${i.y}")
        }
        for (i in rightMost) {
            Log.d(TAG, "order_points: ${i.x} ${i.y}")
        }
        leftMost.sortBy { point -> point.y }
        Log.d(TAG, "order_points: ${leftMost[0]} ${leftMost[1]}")
        Log.d(TAG, "order_points: ${rightMost[0]} ${rightMost[1]}")

        val (tl, bl) = Pair(leftMost[0], leftMost[1])
        val f = dist(tl, rightMost[0])
        val s = dist(tl, rightMost[1])

        return if (f < s) {
            val (br, tr) = Pair(rightMost[1], rightMost[0])
            MatOfPoint2f(tl, tr, br, bl)
        } else {
            val (br, tr) = Pair(rightMost[0], rightMost[1])
            MatOfPoint2f(tl, tr, br, bl)
        }
    }

    private fun four_point_transform(image: Mat, pts: Array<Point>): Mat {
        val rect = order_points(pts)

        val tl = rect.toArray()[0]
        val tr = rect.toArray()[1]
        val br = rect.toArray()[2]
        val bl = rect.toArray()[3]

        Log.d(TAG, "four_point_transform: $tl $tr $br $bl")

        val widthA = Math.sqrt(Math.pow(br.x - bl.x, 2.0) + Math.pow(br.y - bl.y, 2.0))
        val widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2.0) + Math.pow(tr.y - tl.y, 2.0))
        val maxWidth = Math.max(widthA.toInt(), widthB.toInt())


        val heightA = Math.sqrt(Math.pow(tr.x - br.x, 2.0) + Math.pow(tr.y - br.y, 2.0))
        val heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2.0) + Math.pow(tl.y - bl.y, 2.0))
        val maxHeight = Math.max(heightA.toInt(), heightB.toInt())


        val dst: Mat = MatOfPoint2f(
            Point(0.toDouble(), 0.toDouble()),
            Point((maxWidth - 1).toDouble(), 0.toDouble()),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.toDouble(), (maxHeight - 1).toDouble())
        )

        val transform = Imgproc.getPerspectiveTransform(rect, dst)
        val warped: Mat = Mat()
        Imgproc.warpPerspective(
            image, warped, transform, Size(
                maxWidth.toDouble(),
                maxHeight.toDouble()
            )
        )
        return warped

    }

    private fun show(thresh: Mat) {

        val resultBitmap =
            Bitmap.createBitmap(thresh.cols(), thresh.rows(), Bitmap.Config.ARGB_8888)

        Utils.matToBitmap(thresh, resultBitmap)
        photoImageView.setImageBitmap(resultBitmap)
    }

    private fun extract_digit(cell: Mat): Mat? {
        val thresh = Mat(cell.size(), CvType.CV_8UC1)
        Imgproc.threshold(cell, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)


        val contours: MutableList<MatOfPoint> = ArrayList()
        val hierarchy = Mat()
        Imgproc.findContours(
            thresh,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )


        if (contours.isNotEmpty()) {


            val c = contours.maxBy { Imgproc.contourArea(it) }
            val mask = Mat(thresh.size(), 0)


            val C: List<MatOfPoint?> = listOf(c)
            Imgproc.drawContours(mask, C, -1, Scalar(255.0, 0.0, 0.0), -1)

            val h = thresh.size().height
            val w = thresh.size().width
            val percentFilled = Core.countNonZero(mask) / (h * w)
            if (percentFilled >= 0.03) {
                val digit: Mat = Mat(thresh.size(), 0)
                Core.bitwise_and(thresh, thresh, digit, mask)
                show(digit)
                return digit
            }

        }
        return null
    }



    private fun detectEdges(bitmap: Bitmap) {
        Log.d(TAG, "detectEdges: $bitmap")
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Log.d(TAG, "detectEdges: ${rgba}")
        val gray = Mat(rgba.size(), CvType.CV_8UC1)
        val blurred = Mat(rgba.size(), CvType.CV_8UC1)
        val thresh = Mat(rgba.size(), CvType.CV_8UC1)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGB2GRAY, 4)
        Imgproc.GaussianBlur(gray, blurred, Size(7.0, 7.0), 3.0)
        Imgproc.adaptiveThreshold(
            blurred,
            thresh,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11,
            2.0
        )
        Core.bitwise_not(thresh, thresh)


        // contours will contain all the contours as vectors with coordinates
        // hierarchy->
        val contours: MutableList<MatOfPoint> = ArrayList()
        val hierarchy = Mat()


        Imgproc.findContours(
            thresh,
            contours,
            hierarchy,
            Imgproc.RETR_TREE,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        contours.sortByDescending { Imgproc.contourArea(it) }

        var isP = false
        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)

            // select biggest 4 angles polygon
            if (approx.toArray().size == 4) {
                isP = true
                val puzzleCnt = approx.toArray()
                Log.d(TAG, "detectEdges2222: ${puzzleCnt.size}")
                Log.d(TAG, "detectEdges2222: ${puzzleCnt.get(0)}")
                Log.d(TAG, "detectEdges2222: ${puzzleCnt.get(1)}")
                Log.d(TAG, "detectEdges2222: ${puzzleCnt.get(2)}")
                val puzzle = four_point_transform(rgba, puzzleCnt)
                val warped = four_point_transform(gray, puzzleCnt)
                show(puzzle)
                break
            }
        }
        if (!isP) {
            Toast.makeText(
                applicationContext,
                "This is a message displayed in a Toast",
                Toast.LENGTH_SHORT
            ).show()


        }


    }


    companion object {
        private const val TAG = "MainActivity"
    }

    fun setScaledBitmap(): Bitmap {
        val imageViewWidth = photoImageView.width
        val imageViewHeight = photoImageView.height

        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
//        Log.d(
//            TAG,
//            "setScaledBitmap: ${bmOptions.inBitmap} ${bmOptions.inDensity} ${bmOptions.outColorSpace}"
//        )
        val bitmapWidth = bmOptions.outWidth
        val bitmapHeight = bmOptions.outHeight

        val scaleFactor = Math.min(bitmapWidth / imageViewWidth, bitmapHeight / imageViewHeight)

        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor

        return BitmapFactory.decodeFile(currentPhotoPath, bmOptions)

    }

    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = File(currentPhotoPath)
            mediaScanIntent.data = Uri.fromFile(f)
            sendBroadcast(mediaScanIntent)
        }
    }


}