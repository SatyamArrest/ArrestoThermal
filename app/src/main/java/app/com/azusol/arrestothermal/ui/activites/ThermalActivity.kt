package app.com.azusol.arrestothermal.ui.activites

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.text.Html
import android.util.Log
import app.com.azusol.arrestothermal.BuildConfig
import app.com.azusol.arrestothermal.R
import app.com.azusol.arrestothermal.base.BaseActivity
import app.com.azusol.arrestothermal.data.models.ConstantModel
import app.com.azusol.arrestothermal.data.models.ThermalDataModel
import app.com.azusol.arrestothermal.databinding.ActivityThermalBinding
import app.com.azusol.arrestothermal.interfaces.OnImageCapture
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.Dot
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.OnPointCreatedListener
import app.com.azusol.arrestothermal.ui.dialogs.PointCreatorDialog
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid
import com.flir.thermalsdk.image.ImageFactory
import com.flir.thermalsdk.image.Point
import com.flir.thermalsdk.image.ThermalImageFile
import com.flir.thermalsdk.log.ThermalLog
import com.google.gson.Gson
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

class ThermalActivity : BaseActivity<ActivityThermalBinding>(), OnPointCreatedListener {
    var unique_id = "250"
    var image_dir = ""
    var thermalImageFile: ThermalImageFile? = null
    val temDataModel = ThermalDataModel()

    override fun getViewBinding() = ActivityThermalBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        val enableLoggingInDebug =
            if (BuildConfig.DEBUG) ThermalLog.LogLevel.DEBUG else ThermalLog.LogLevel.NONE
        ThermalSdkAndroid.init(applicationContext, enableLoggingInDebug)
        super.onCreate(savedInstanceState)
        val submit = binding.submitBtn
        val choose_btn = binding.chooseBtn

        submit.setOnClickListener {
            onSubmitButton()
        }
        choose_btn.setOnClickListener {
            selectThermalImage()
        }
        allImages = ArrayList()
        image_dir = directory + "inspection/" + unique_id + "/thermal/"
        checkDir(image_dir)
        selectThermalImage()
    }

    private fun selectThermalImage() {
        showThermalDialog(image_dir, object : OnImageCapture {
            override fun onCapture(path: String) {
                Log.e("onCapture run ", "onCapture==  $imagePath")
                thermalImageFile = openIncludedImage(imagePath)
                proccessImage()
            }
        })
    }

    private fun proccessImage() {
        if (thermalImageFile != null) {
            val flirBitmap = BitmapAndroid.createBitmap(thermalImageFile!!.image).bitMap
            if (flirBitmap != null) {
//                allImages.add(BitmapAndroid.createBitmap(thermalImageFile.getImage()).getBitMap()); //new Object because it's modified later
                convertScaleBitmap(thermalImageFile!!)
                val intialPoints = ArrayList<Point>()
                 val h_ratio = thermalImageFile!!.image.getHeight()
                    .toDouble() / thermalImageFile!!.height.toDouble()
                val w_ratio = thermalImageFile!!.image.getWidth()
                    .toDouble() / thermalImageFile!!.width.toDouble()
                var hotSpot = thermalImageFile!!.statistics.hotSpot
                var coldSpot = thermalImageFile!!.statistics.coldSpot
                hotSpot = Point((hotSpot.x * h_ratio).toInt(), (hotSpot.y * w_ratio).toInt())
                coldSpot = Point((coldSpot.x * h_ratio).toInt(), (coldSpot.y * w_ratio).toInt())
                intialPoints.add(hotSpot)
                intialPoints.add(coldSpot)
                printLog("intialPoints===$intialPoints")

                PointCreatorDialog.newInstance(this, flirBitmap, intialPoints)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        var userId = "No data received"
        val intent = intent
        if (intent != null && intent.action != null && intent.action.equals("app.com.azusol.arrestothermal")) {
            val bundle = intent.getBundleExtra("data")
            if (bundle != null) {
                userId = bundle.getString("user_id").toString()
                userId = " User id is $userId"
            }
        }
    }

    fun openIncludedImage(imagePath: String): ThermalImageFile? {
        var thermalImageFile: ThermalImageFile? = null
        try {
            thermalImageFile = ImageFactory.createImage(imagePath) as ThermalImageFile
        } catch (e: IOException) {
            ThermalLog.e("TAG", "failed to open IR file, exception:$e")
            show_snak("Selected image is not a thermography image.")
            finish()
        }
        return thermalImageFile
    }

    lateinit var scalImage: Bitmap
    lateinit var allImages: ArrayList<Any>

    fun convertScaleBitmap(thermalImageFile: ThermalImageFile) {
//        allImages.add(BitmapAndroid.createBitmap(thermalImageFile.getImage()).getBitMap());
        val thermal_bitmap = BitmapAndroid.createBitmap(thermalImageFile.image).bitMap
        val scale = thermalImageFile.scale
        scalImage = BitmapAndroid.createBitmap(scale.fixedFullRangeScaleImage!!).bitMap
        putOverlay(
            thermal_bitmap,
            scalImage,
            thermalImageFile.statistics.min.asCelsius().value,
            thermalImageFile.statistics.max.asCelsius().value
        )
        allImages.add(0, thermal_bitmap)
    }

    fun putOverlay(bitmap: Bitmap, overlay: Bitmap?, minTemp: Double, maxTemp: Double) {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val top = bitmap.height / 4
        canvas.drawBitmap(overlay!!, 20f, top.toFloat(), paint)
        val scale = resources.displayMetrics.density
        val tvPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        tvPaint.color = Color.WHITE
        tvPaint.textSize = (12 * scale).toInt().toFloat()
        canvas.drawText(
            "" + round(
                maxTemp, 1
            ), 10f, (top - 15).toFloat(), tvPaint
        )
        canvas.drawText(
            "" + round(minTemp, 1), 10f, (top * 3 + 35).toFloat(), tvPaint
        )
    }

    fun round(value: Double, places: Int): Double {
        return BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()
    }

    fun onSubmitButton() {
        if (imagePath != null && imagePath != "") {
            val fileName: String = imagePath.substring(imagePath.lastIndexOf('/') + 1)
            val thermal_imagepath = image_dir + fileName
            val actual_imagepath = image_dir + "actual_" + System.currentTimeMillis() + ".jpg"
            val marked_imagepath = image_dir + "marked_" + System.currentTimeMillis() + ".jpg"
            val scale_imagepath = image_dir + "scale_" + System.currentTimeMillis() + ".jpg"
            saveImage(allImages[0] as Bitmap, thermal_imagepath)
            saveImage(allImages[1] as Bitmap, marked_imagepath)
            saveImage(allImages[2] as Bitmap, actual_imagepath)
            if (scalImage != null) saveImage(scalImage, scale_imagepath)

            temDataModel.thermal_imagepath = thermal_imagepath
            temDataModel.actual_imagepath = actual_imagepath
            temDataModel.marked_imagepath = marked_imagepath
            temDataModel.scale_imagepath = scale_imagepath

            var jsonData: String = Gson().toJson(temDataModel)
            val intent = intent
            val bundle = Bundle()
            bundle.putString("thermalJson", jsonData)
            intent.putExtra("data", bundle)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
        show_snak("File Saved successfully.")
    }


    override fun OnPointCreated(finalBitmap: Bitmap?, points: ArrayList<Dot>) {
        if (finalBitmap != null) {
            allImages.add(finalBitmap)
        }
        setUpTemperaturePoints(points)
    }

    private fun setUpTemperaturePoints(points: ArrayList<Dot>) {
        val visualBitmap = BitmapAndroid.createBitmap(thermalImageFile!!.fusion!!.photo!!).bitMap
        allImages.add(visualBitmap)
        fetched_temprature = ArrayList()
        if (!thermalImageFile!!.measurements.circles.isEmpty()) thermalImageFile!!.measurements.circles.addAll(
            thermalImageFile!!.measurements.circles
        )
        val xRatio = thermalImageFile!!.width.toFloat() / (allImages[0] as Bitmap).width
        if (points != null && points.size > 0) {
            for (dot in points) {
                val radius = (xRatio * dot.getRadius()).toInt()
                thermalImageFile!!.measurements.addCircle(thermalImageFile!!.width/2, thermalImageFile!!.height/2, radius)
            }
        }
        if (!thermalImageFile!!.measurements.circles.isEmpty()) {
            val cilrcles = thermalImageFile!!.measurements.circles
            for (i in cilrcles.indices) {
                val cilrcle = cilrcles[i]
                val maxValue: String = round(cilrcle.maxValue.asCelsius().value, 2).toString()
                val minValue: String = round(cilrcle.minValue.asCelsius().value, 2).toString()
                var temp_model = ConstantModel()
                if (points[i].dotName.equals("T1")) {
                    temp_model.name =
                        Html.fromHtml(points[i].dotName + "<sup><small>H</small></sup>").toString()
                    temp_model.temp = (maxValue)
                    fetched_temprature.add(temp_model)
                } else if (points[i].dotName.equals("T2")) {
                    temp_model.name =
                        Html.fromHtml(points[i].dotName + "<sup><small>C</small></sup>").toString()
                    temp_model.temp = (minValue)
                    fetched_temprature.add(temp_model)
                } else {
                    temp_model.name =
                        Html.fromHtml(points[i].dotName + "<sup><small>Max</small></sup>")
                            .toString()
                    temp_model.temp = (maxValue)
                    fetched_temprature.add(temp_model)
                    temp_model = ConstantModel()
                    temp_model.name =
                        Html.fromHtml(points[i].dotName + "<sup><small>Min</small></sup>")
                            .toString()
                    temp_model.temp = (minValue)
                    fetched_temprature.add(temp_model)
                }
            }
            temDataModel.tempData = fetched_temprature
            temDataModel.emissivity =
                round(thermalImageFile!!.imageParameters.emissivity, 2).toString()
            temDataModel.humidity =
                round(thermalImageFile!!.imageParameters.relativeHumidity, 2).toString()
            temDataModel.air_temp = round(
                thermalImageFile!!.imageParameters.atmosphericTemperature.asCelsius().value,
                2
            ).toString()
            temDataModel.cameraModel = thermalImageFile!!.cameraInformation.model.toString()
        }
    }

    private lateinit var fetched_temprature: ArrayList<ConstantModel>
}

