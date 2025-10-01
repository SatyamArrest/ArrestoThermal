package app.com.azusol.arrestothermal.base

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import app.com.azusol.arrestothermal.BuildConfig
import app.com.azusol.arrestothermal.R
import app.com.azusol.arrestothermal.constants.Check_permissions
import app.com.azusol.arrestothermal.interfaces.OnImageCapture
import app.com.azusol.arrestothermal.thirdPartyLibs.progress_lib.ACProgressConstant
import app.com.azusol.arrestothermal.thirdPartyLibs.progress_lib.ACProgressFlower
import app.com.azusol.arrestothermal.thirdPartyLibs.progress_lib.ACProgressFlower.Builder
import app.com.azusol.arrestothermal.ui.activites.ThermalCameraActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

abstract class BaseActivity<B : ViewBinding> : AppCompatActivity() {
    var THERMAL_IMAGE_REQUEST = 10009
    var THERMAL_GALLERY_REQUEST = 10010

    lateinit var progressDialog: ACProgressFlower
    lateinit var onImageCapture: OnImageCapture
    var imagePath = ""
    var directory: String = ""
    var imageDirectory: String = ""

    lateinit var binding: B

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = getViewBinding()
        setContentView(binding.root)
        progressDialog = Builder(this).direction(ACProgressConstant.DIRECT_CLOCKWISE)
            .themeColor(Color.YELLOW) //                .bgColor(Color.BLACK)
            .text("Please Wait...").textSize(16).textMarginTop(5).petalThickness(2)
            .sizeRatio(0.22.toFloat()).fadeColor(Color.WHITE).build()
        progressDialog.setCancelable(false)
        setUpDirectory()
    }

    abstract fun getViewBinding(): B

    open fun setUpDirectory() {
        if (externalMediaDirs.size > 0) directory =
            externalMediaDirs[0].toString() + "/ArrestoThermal/"
        else {
            directory = getExternalFilesDir(null).toString() + "/Arresto/"
            directory = directory.replace("data", "media")
            directory = directory.replace("files/", "")
        }
    }

    protected fun showThermalDialog(path: String, onImageCapture: OnImageCapture) {
        if (!isStoragePermissionGranted()) return
        imageDirectory = path;
        this.onImageCapture = onImageCapture;
        val dialog = Dialog(this, R.style.theme_dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_choose_image)

        val close_btn = dialog.findViewById<ImageView>(R.id.close_btn)
        val capture_tv = dialog.findViewById<TextView>(R.id.capture_tv)
        capture_tv.text = "Take Thermal Photo"
        val choose_tv = dialog.findViewById<TextView>(R.id.choose_tv)
        choose_tv.text = "Choose Thermal Photo"
        val cancel_btn = dialog.findViewById<MaterialButton>(R.id.cancel_btn)
        close_btn.setOnClickListener { dialog.dismiss() }
        cancel_btn.setOnClickListener { dialog.dismiss() }
        capture_tv.setOnClickListener {
            if (isSupportThermalSensor()) {
                capture_Thermal_Image(onImageCapture)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Temperature Sensor Not Detected", Toast.LENGTH_LONG).show()
            }
        }
        choose_tv.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, THERMAL_GALLERY_REQUEST)
            dialog.dismiss()
        }
        dialog.show()
    }

    var fileName: String = ""
    open fun capture_Thermal_Image(onImageCapture: OnImageCapture) {
        this.onImageCapture = onImageCapture
        checkDir(imageDirectory)
        if (fileName == "") {
            fileName = System.currentTimeMillis().toString() + ".jpg"
            imagePath = imageDirectory + fileName
        }
        startCamera(THERMAL_IMAGE_REQUEST, fileName, true, false, true)
    }

    open fun startCamera(
        request_code: Int, name: String?, isTime: Boolean, isScan: Boolean, isThermal: Boolean
    ) {
        val camera = Intent()
//        if (isThermal)
        camera.setClass(this, ThermalCameraActivity::class.java)
//        else camera.setClass(this,CameraActivity::class.java)
        camera.putExtra("name", name)
        camera.putExtra("path", imageDirectory)
        camera.putExtra("istime", isTime)
        camera.putExtra("isScan", isScan)
        startActivityForResult(camera, request_code)
    }

    private fun isSupportThermalSensor(): Boolean {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        return sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null
    }

    fun checkDir(path: String?) {
        val f1 = File(path)
        if (!f1.exists()) {
            f1.mkdirs()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == THERMAL_IMAGE_REQUEST) {
                onImageCapture.onCapture(imagePath)
            } else if (requestCode == THERMAL_GALLERY_REQUEST) {
                show_progress()
                val selectedImage = data!!.data
                imagePath = selectedImage?.let { getRealPathFromURI(it) }.toString()
                Log.e("onActivityResult run ", "onActivityResult==  $imagePath")
                onImageCapture.onCapture(imagePath)
                hide_progress()
            } else {
                show_snak("Please Try Again")
            }
        }
    }

    open fun show_progress() {
        if (progressDialog != null && !progressDialog.isShowing()) progressDialog.show()
    }

    open fun hide_progress() {
        if (progressDialog != null && !isFinishing()) progressDialog.cancel()
    }

    open fun show_snak(msg: String) {
        Snackbar.make(this.findViewById(android.R.id.content), "" + msg, Snackbar.LENGTH_LONG)
            .show()
    }

    open fun getRealPathFromURI(contentUri: Uri): String {
        return try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(contentUri, proj, null, null, null)
            val column_index = cursor!!.getColumnIndexOrThrow(proj[0])
            cursor.moveToFirst()
            cursor.getString(column_index)
        } catch (e: Exception) {
            contentUri.path.toString()
        }
    }

    open fun isStoragePermissionGranted(): Boolean {
        if (VERSION.SDK_INT < Build.VERSION_CODES.Q && !Check_permissions.hasPermissions(
                this, Check_permissions.CAMERA_STORAGE_PERMISSIONS)) {
            Check_permissions.request_permissions(
                this, Check_permissions.CAMERA_STORAGE_PERMISSIONS)
            return false
        } else if (VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Check_permissions.hasPermissions(
                this, Check_permissions.CAMERA_STORAGE_PERMISSIONS_10)) {
            Check_permissions.request_permissions(
                this, Check_permissions.CAMERA_STORAGE_PERMISSIONS_10)
            return false
        }
        return true
    }

    fun saveImage(image: Bitmap, path: String) {
        try {
            val imageFile = File(path)
            val out: OutputStream = FileOutputStream(imageFile)
            if (out != null) {
                image.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
                out.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    fun printLog(msg: String?) {
        if (BuildConfig.DEBUG) Log.e("baseActivity", msg!!)
    }

}