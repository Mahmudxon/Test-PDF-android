package uz.mahmudxon.pdfview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.drawToBitmap
import androidx.core.widget.NestedScrollView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private var filename = "grimm10.pdf"
    private var index = 0
    private var currentZoomLevel = 1.0
    private lateinit var renderer: PdfRenderer
    var page: PdfRenderer.Page? = null
    lateinit var imageView: ImageView
    lateinit var qrView: ImageView
    lateinit var vScroll: NestedScrollView
    lateinit var hScroll: HorizontalScrollView
    private var qrIndex = 0
    private var dX = 0f
    private var dY = 0f
    private var isSaved = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        imageView = findViewById(R.id.image)
        vScroll = findViewById(R.id.vertical_scroll)
        hScroll = findViewById(R.id.horizontal_scroll)
        val input = ParcelFileDescriptor.open(
            getCacheFileFromAssets(this),
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        renderer = PdfRenderer(input)
        showPage()
        findViewById<Button>(R.id.previous).setOnClickListener {
            if (index == 0)
                return@setOnClickListener
            index -= 1
            showPage()
        }

        findViewById<Button>(R.id.next).setOnClickListener {
            if (index == renderer.pageCount)
                return@setOnClickListener
            index += 1
            showPage()
        }
        findViewById<Button>(R.id.plus).setOnClickListener {
            currentZoomLevel *= 1.25
            showPage()
        }
        findViewById<Button>(R.id.minus).setOnClickListener {
            currentZoomLevel /= 1.25
            showPage()
        }
        findViewById<View>(R.id.button).setOnTouchListener { view, event ->
            val newX: Float
            val newY: Float

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {

                    newX = event.rawX + dX
                    newY = event.rawY + dY

                    if ((newX <= 0 || newX >= imageView.width - view.width) || (newY <= 0 || newY >= imageView.height - view.height)) {
                        return@setOnTouchListener true
                    }

                    view.animate()
                        .x(newX)
                        .y(newY)
                        .setDuration(0)
                        .start()
                }
            }

            return@setOnTouchListener true
        }

        findViewById<Button>(R.id.save).setOnClickListener { if (!isSaved) save() }
    }

    @Throws(IOException::class)
    fun getCacheFileFromAssets(context: Context): File {
        val cacheFile = File(context.cacheDir, filename)
        try {
            val inputStream = context.assets.open(filename)
            val outputStream = FileOutputStream(cacheFile)
            try {
                inputStream.copyTo(outputStream)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: IOException) {
            throw IOException("Could not open splash_video", e)
        }
        return cacheFile
    }

    private fun showPage() {

        if (index < 0 || index >= renderer.pageCount)
            return

        qrView = findViewById(R.id.button)
        qrView.visibility = if (index == qrIndex) View.VISIBLE else View.GONE

        page?.close()
        page = renderer.openPage(index)
        val width: Int = if (isSaved) (page?.width ?: 1 * currentZoomLevel).toInt() else
            ((resources.displayMetrics.densityDpi * currentZoomLevel) * (page?.width
                ?: 1) / 120).toInt()
        val height: Int = if (isSaved) (page?.height ?: 1 * currentZoomLevel).toInt() else
            ((resources.displayMetrics.densityDpi * currentZoomLevel) * (page?.height
                ?: 1) / 120).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        page?.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        imageView.setImageBitmap(bitmap)
    }

    private fun save() {
        val document = PdfDocument()

        page?.close()
        for (i in 0..5) {
            val pdfPage = renderer.openPage(i)

            val width: Int =
                (resources.displayMetrics.densityDpi) * (pdfPage?.width
                    ?: 1) / 120
            val height: Int =
                (resources.displayMetrics.densityDpi) * (pdfPage?.height
                    ?: 1) / 120

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            pdfPage?.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            if (i == qrIndex) {
                val matrix = Matrix()
                matrix.setTranslate(
                    qrView.x + (hScroll.scrollX / currentZoomLevel).toFloat(),
                    qrView.y + (vScroll.scrollY / currentZoomLevel).toFloat()
                )
                canvas.drawBitmap(qrView.drawToBitmap(), matrix, Paint())
            }


            val pageInfo =
                PageInfo.Builder(width, height, i + 1).create()
            val matrix = Matrix()
            val page: PdfDocument.Page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, matrix, Paint())
            document.finishPage(page)
            pdfPage?.close()
        }
        page = null
        document.writeTo(File(cacheDir, "temp.pdf").outputStream())
        index = 0
        filename = "temp.pdf"
        val input = ParcelFileDescriptor.open(
            File(cacheDir, "temp.pdf"),
            ParcelFileDescriptor.MODE_READ_ONLY

        )
        renderer = PdfRenderer(input)
        qrIndex = -1
        isSaved = true
        showPage()
    }
}