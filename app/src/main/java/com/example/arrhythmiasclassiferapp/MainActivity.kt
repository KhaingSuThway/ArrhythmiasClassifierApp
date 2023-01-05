@file:Suppress("DEPRECATION")

package com.example.arrhythmiasclassiferapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.arrhythmiasclassiferapp.databinding.ActivityMainBinding
import com.example.arrhythmiasclassiferapp.ml.Mv2
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.single.PermissionListener
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.common.ops.QuantizeOp
import org.tensorflow.lite.support.image.ColorSpaceType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var result: TextView
    private lateinit var tv :TextView
    private val CAMERA_REQUEST_CODE=1
    private val GALLERY_REQUEST_CODE=2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        result=findViewById(R.id.result)
        tv=findViewById (R.id.textView)

        binding.btnCamera.setOnClickListener {
            cameraCheckPermission()        }
        binding.btnGallery.setOnClickListener {
            galleryCheckPermission()        }

        binding.imageView.setOnClickListener {
            val pictureDialog = AlertDialog.Builder(this)
            pictureDialog.setTitle("Select Action")
            val pictureDialogItem = arrayOf("Select photo from Gallery",
                "Capture photo from Camera")
            pictureDialog.setItems(pictureDialogItem) { _, which ->

                when (which) {
                    0 -> gallery()
                    1 -> camera()
                }
            }
            pictureDialog.show()
        }

    }

    private fun classifyImage(bitmap: Bitmap) = try{

            val model = Mv2.newInstance(this)
            val imgprocessor = ImageProcessor.Builder()
            // .add(ResizeWithCropOrPadOp(64, 64))
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(127.5f, 127.5f))
            .add(QuantizeOp(128.0f, 1 / 128.0f)).build()

            val newBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val tfimage = TensorImage(DataType.FLOAT32)
            tfimage.load(newBitmap)
            val inputImage=imgprocessor.process(tfimage)
            val tensorbuffr=inputImage.tensorBuffer
            val tensorimg = TensorImage(DataType.FLOAT32)
            tensorimg.load(tensorbuffr, ColorSpaceType.RGB)
            val byteBuffer: ByteBuffer =tensorimg.buffer


    // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            //val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*224*224*3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues=IntArray(224*224)
            bitmap.getPixels(intValues,0,224,0,0,224,224)

            for (pixelValue in intValues) {
                val r = (pixelValue shr 16 and 0xFF)
                val g = (pixelValue shr 8 and 0xFF)
                val b = (pixelValue and 0xFF)

                // Normalize pixel value to [0..1]
                val normalizedPixelValue = (r + g + b)  /255.0f
                byteBuffer.putFloat(normalizedPixelValue)
            }

            inputFeature0.loadBuffer(byteBuffer)

    // Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            // Get the data of the TensorBuffer as a float array
            val confidences: FloatArray = outputFeature0.floatArray

            // Find the largest number and its index
            val (largestNumberIndex,largestNumber) = confidences.withIndex().maxBy { it.value }
            val confidence = largestNumber.toInt()  / 255.0f
            val classes : Array<String> = arrayOf("AF","NSR","PAC","PVC")
            result.text = classes[largestNumberIndex]
            tv.text = ""
            val arraySize:Int=confidences.size
            for (i in 0 until arraySize) {
                tv.append(confidences[i].toString());
                tv.append(",")
        }

//            tv.text = confidences[0].toString()

            // Releases model resources if no longer used.
            model.close()

        }
        catch (e: IllegalStateException){
            Log.e(ContentValues.TAG, "TFLite failed to load model with error: " )
        }



    private fun cameraCheckPermission() {
        Dexter.withContext(this)
            .withPermissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA).withListener(

                object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        report?.let {
                            if (report.areAllPermissionsGranted()) {
                                camera()    }
                        }
                    }
                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?) {
                        showRotationalDialogForPermission()    }
                }
            ).onSameThread().check()    }

    @Suppress("DEPRECATION")
    private fun camera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST_CODE)    }

    private fun galleryCheckPermission() {

        Dexter.withContext(this).withPermission(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ).withListener(object : PermissionListener {
            override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                gallery()   }
            override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                Toast.makeText(
                    this@MainActivity,
                    "You have denied the storage permission to select image",
                    Toast.LENGTH_SHORT
                ).show()
                showRotationalDialogForPermission()
            }

            override fun onPermissionRationaleShouldBeShown(
                p0: PermissionRequest?, p1: PermissionToken?) {
                showRotationalDialogForPermission()
            }
        }).onSameThread().check()    }

    @Suppress("DEPRECATION")
    private fun gallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, GALLERY_REQUEST_CODE)    }

    private fun showRotationalDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions"
                    + "required for this feature. It can be enable under App settings!!!")

            .setPositiveButton("Go TO SETTINGS") { _, _ ->

                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)   }
                catch (e: ActivityNotFoundException) {
                    e.printStackTrace()   }
            }  .setNegativeButton("CANCEL") { dialog, _ ->
                dialog.dismiss()   }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {

                    var image = data?.extras?.get("data") as Bitmap
                    binding.imageView.load(image){
                        crossfade(true)
                        crossfade(100)
                        transformations(RoundedCornersTransformation(8F))
                    }
                    val dimension = min(image.width, image.height)
                    image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
                    image = Bitmap.createScaledBitmap(image, 224, 224, false)
                    classifyImage(image)


                }
                GALLERY_REQUEST_CODE -> {
                    binding.imageView.load(data?.data)
                    val dat =data?.data
                    val image= MediaStore.Images.Media.getBitmap(this.contentResolver, dat)
                    classifyImage(image)

                }
            }
        }
    }



}