package app.com.azusol.arrestothermal.ui.dialogs

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import app.com.azusol.arrestothermal.R
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.Dot
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.DrawableDotImageView
import app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal.OnPointCreatedListener
import com.flir.thermalsdk.image.Point
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

class PointCreatorDialog : DialogFragment() {

    lateinit var mListener: OnPointCreatedListener
    lateinit var seekBar: SeekBar
    lateinit var spot_heading: TextView
    lateinit var check_btn: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppTheme)
    }

     var bitmap: Bitmap?=null

   companion object{
       var TAG = "FullView"
       lateinit var intialPoint: ArrayList<Point>
       fun newInstance(appCompatActivity: AppCompatActivity, bitmap: Bitmap, intialPoints: ArrayList<Point>) {
        val fullScreenDialog = PointCreatorDialog()
        intialPoint = intialPoints
        val args = Bundle()
        args.putParcelable("imageBitmap", bitmap)
        fullScreenDialog.arguments = args
        fullScreenDialog.show(appCompatActivity.supportFragmentManager.beginTransaction(),TAG)
//        return fullScreenDialog;
    }
   }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            mListener = activity as OnPointCreatedListener
        } catch (e: ClassCastException) {
            throw ClassCastException(activity.localClassName + " must implement OnCompleteListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val view: View = inflater.inflate(R.layout.point_creator_dialog, container, false)
        bitmap = arguments?.getParcelable("imageBitmap")
        val imageView: DrawableDotImageView = view.findViewById(R.id.imageView)
        val save_btn = view.findViewById<MaterialButton>(R.id.save_btn)
        if (bitmap != null) {
            imageView.setBitmapWidth(bitmap!!.width, intialPoint, this@PointCreatorDialog)
            imageView.setImageBitmap(bitmap)
        }
        spot_heading = view.findViewById<TextView>(R.id.spot_heading)
        check_btn = view.findViewById<CheckBox>(R.id.check_btn)
        val cancel_btn = view.findViewById<ImageView>(R.id.cancel_btn)
        cancel_btn.setOnClickListener { dismiss() }
        save_btn.setOnClickListener {
            if (imageView.dots.size > 1) {
                val bitmapCanvas = Canvas(bitmap!!)
                imageView.drawEvent(bitmapCanvas, true)
                mListener.OnPointCreated(bitmap, imageView.dots)
                dismiss()
            } else show_snak("Please create at least two temperature points.")
        }
        seekBar = view.findViewById<SeekBar>(R.id.seekBar)
        seekBar.setThumb(getCurrentThumb(seekBar.getProgress() * 5))
        seekBar.setProgressDrawable(requireActivity().resources?.getDrawable(R.drawable.seek_progress_bg))
        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progrs = seekBar.progress * 5
                if (progrs < 1) {
                    progrs = 1
                }
                seekBar.thumb = getCurrentThumb(progrs)
                if (selectedDot != null && !check_btn.isChecked())
                    selectedDot!!.radius = progrs.toFloat()
                else imageView.setRadius(progrs)
                imageView.invalidate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        check_btn.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                spot_heading.setText("Spot size")
                selectedDot = null
            }
        })
        return view
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window!!.setLayout(width, height)
        }
    }

     var selectedDot: Dot?=null

    fun onDotSelect(dot: Dot) {
        if (!check_btn.isChecked) {
            selectedDot = dot
            spot_heading.text = "Spot " + dot.getDotName() + " size"
            seekBar.thumb = getCurrentThumb(dot.getRadius().toInt())
            seekBar.progress = ((dot.getRadius() / 5).roundToInt())
        } else
            Toast.makeText(context, "You selected change for all spots. Please unselect it first", Toast.LENGTH_LONG).show()
    }

    fun getCurrentThumb(currentProgress: Int): Drawable? {
        var writableBitmap: Bitmap? =
            drawable2Bitmap(requireActivity().resources.getDrawable(R.drawable.seek_thumb))

        if(writableBitmap!=null){
        writableBitmap = addText(writableBitmap, currentProgress)
        return bitmap2Drawable(writableBitmap)
        }
        else return null
    }

    fun addText(src: Bitmap, currentProgress: Int): Bitmap {
        var bitmapConfig = src.config
        if (bitmapConfig == null) bitmapConfig = Bitmap.Config.ARGB_8888
        val bitmap = src.copy(bitmapConfig, true)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 35f
        val rectangle = Rect()
        paint.getTextBounds(
            currentProgress.toString(),
            0,  // start
            currentProgress.toString().length,
            rectangle
        )
        canvas.drawText(
            currentProgress.toString(),
            canvas.width / 2.0f,
            canvas.height / 2.0f + Math.abs(rectangle.height()) / 2.0f,  // y
            paint // Paint
        )
        return bitmap
    }

    fun show_snak(msg: String) {
        Snackbar.make(requireActivity().findViewById(android.R.id.content), "" + msg, Snackbar.LENGTH_LONG).show()
    }

    fun bitmap2Drawable(bitmap: Bitmap?): Drawable? {
        return if (bitmap == null) null else BitmapDrawable(requireActivity().resources, bitmap)
    }

    fun drawable2Bitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            val bitmapDrawable = drawable
            if (bitmapDrawable.bitmap != null) {
                return bitmapDrawable.bitmap
            }
        }
        val bitmap: Bitmap
        bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1,
                if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight,
                if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}