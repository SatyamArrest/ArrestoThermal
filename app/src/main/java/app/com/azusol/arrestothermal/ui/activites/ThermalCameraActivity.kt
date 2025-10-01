package app.com.azusol.arrestothermal.ui.activites

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import app.com.azusol.arrestothermal.BuildConfig
import app.com.azusol.arrestothermal.R
import app.com.azusol.arrestothermal.base.BaseActivity
import app.com.azusol.arrestothermal.databinding.ThermalCameraActivityBinding
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.CameraHandler
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.StatusChangeListener
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler.UsbPermissionListener
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.log.ThermalLog
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ThermalCameraActivity : BaseActivity<ThermalCameraActivityBinding>() , StatusChangeListener {

    override fun getViewBinding() = ThermalCameraActivityBinding.inflate(layoutInflater)
    val executor: Executor = Executors.newSingleThreadExecutor()
    val TAG: String = this.javaClass.getSimpleName()
    var image: ImageView? = null

    val handler = Handler()

    val connection_Runnable = Runnable {
        Log.e(TAG, "runConnectionTimer: now")
        executor.execute(cameraHandler::connect)
    }


    fun runConnectionTimer() {
        handler.removeCallbacks(connection_Runnable)
        handler.postDelayed(connection_Runnable, 3000)
    }

    override fun onFrameUpdated() {
        if (image != null) {
            runOnUiThread { image!!.setImageBitmap(cameraHandler.getMostRecentBitmap()) }
        }
    }

    override fun onNewCameraFound(identity: Identity) {
        Log.e(TAG, "Found device: $identity")
        runConnectionTimer()
    }

    override fun onCameraConnect(status: Any) {
        Log.e(TAG, "connect status: $status")
        executor.execute(cameraHandler::startStreaming)
    }

    override fun onCapture(isCapture: Boolean) {
        if (isCapture) {
            val sendBackResult = Intent()
            sendBackResult.putExtra("path", imagePath1)
            setResult(RESULT_OK, sendBackResult)
            finish()
        }
    }

    override fun onStatusUpdated(text: String) {
        Log.e(TAG, "onStatusUpdated:$text")
        if (text == "streaming" || text == "streamingError") {
            hide_progress()
        }
    }

     override fun showMessage(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }


    lateinit var cameraHandler: CameraHandler

    val usbPermissionHandler = UsbPermissionHandler()
    var imagePath1: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressDialog.mBuilder.text("Connecting to camera...")
        show_progress()
        image = findViewById(R.id.image)
        val enableLoggingInDebug =
            if (BuildConfig.DEBUG) ThermalLog.LogLevel.DEBUG else ThermalLog.LogLevel.NONE
        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug)
        cameraHandler = CameraHandler(this)
        executor.execute(cameraHandler::startDiscovering)
    }


    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        //Always close the connection with a connected mobile phone when going into background
        executor.execute {
            cameraHandler.disconnect()
            cameraHandler.stopDiscovering()
        }
    }


    fun saveImage(view: View?) {
        cameraHandler.saveThermalimage(imagePath1)
    }

    fun ensurePermissions(identity: Identity) {
        if (identity.communicationInterface == CommunicationInterface.INTEGRATED) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            }
        }
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(
                identity,
                this,
                object : UsbPermissionListener {
                    override fun permissionGranted(identity: Identity) {}
                    override fun permissionDenied(identity: Identity) {
                        showMessage("Permission was denied for identity ")
                    }

                    override fun error(
                        errorType: UsbPermissionListener.ErrorType,
                        identity: Identity
                    ) {
                        showMessage("Error when asking for permission for mobile phone, error:$errorType identity:$identity")
                    }
                })
        }
    }

}