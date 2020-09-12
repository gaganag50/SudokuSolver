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
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


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



    private fun detectEdges(bitmap: Bitmap) {
        Log.d(TAG, "detectEdges: $bitmap")
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Log.d(TAG, "detectEdges: ${rgba}")
        val edges = Mat(rgba.size(), CvType.CV_8UC1)
        Log.d(TAG, "detectEdges: ${edges}")
        Imgproc.cvtColor(rgba, edges, Imgproc.COLOR_RGB2GRAY, 4)
        Imgproc.Canny(edges, edges, 80.0, 100.0)

        // Don't do that at home or work it's for visualization purpose.
//        BitmapHelper.showBitmap(this, bitmap, imageView);
        val resultBitmap =
            Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)



        Log.d(
            TAG,
            "detectEdges: ${bitmap.byteCount} ${bitmap.colorSpace} ${bitmap.density} ${bitmap.height} ${bitmap.width}"
        )
        Log . d (
                TAG,
        "detectEdges: ${resultBitmap.byteCount} ${resultBitmap.colorSpace} ${resultBitmap.density} ${resultBitmap.height} ${resultBitmap.width}"
        )


        Log.d(TAG, "detectEdges: $resultBitmap")
        Utils.matToBitmap(edges, resultBitmap)
        photoImageView.setImageBitmap(resultBitmap)
//        BitmapHelper.showBitmap(this, resultBitmap, photoImageView);
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
        Log.d(
            TAG,
            "setScaledBitmap: ${bmOptions.inBitmap} ${bmOptions.inDensity} ${bmOptions.outColorSpace}"
        )
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