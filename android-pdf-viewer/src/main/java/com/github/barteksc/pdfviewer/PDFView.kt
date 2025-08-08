/**
 * Copyright 2016 Bartosz Schiller
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.AsyncTask
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.util.SizeF
import android.widget.RelativeLayout
import com.github.barteksc.pdfviewer.exception.PageRenderingException
import com.github.barteksc.pdfviewer.link.DefaultLinkHandler
import com.github.barteksc.pdfviewer.link.LinkHandler
import com.github.barteksc.pdfviewer.listener.Callbacks
import com.github.barteksc.pdfviewer.listener.OnDrawListener
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnLongPressListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.github.barteksc.pdfviewer.listener.OnPageScrollListener
import com.github.barteksc.pdfviewer.listener.OnRenderListener
import com.github.barteksc.pdfviewer.listener.OnTapListener
import com.github.barteksc.pdfviewer.model.PagePart
import com.github.barteksc.pdfviewer.scroll.ScrollHandle
import com.github.barteksc.pdfviewer.source.AssetSource
import com.github.barteksc.pdfviewer.source.ByteArraySource
import com.github.barteksc.pdfviewer.source.DocumentSource
import com.github.barteksc.pdfviewer.source.FileSource
import com.github.barteksc.pdfviewer.source.InputStreamSource
import com.github.barteksc.pdfviewer.source.UriSource
import com.github.barteksc.pdfviewer.util.Constants
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.github.barteksc.pdfviewer.util.MathUtils.limit
import com.github.barteksc.pdfviewer.util.SnapEdge
import com.github.barteksc.pdfviewer.util.Util.getDP
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.util.Config
import io.legere.pdfiumandroid.util.Size
import java.io.File
import java.io.InputStream

/**
 * It supports animations, zoom, cache, and swipe.
 *
 *
 * To fully understand this class you must know its principles :
 * - The PDF document is seen as if we always want to draw all the pages.
 * - The thing is that we only draw the visible parts.
 * - All parts are the same size, this is because we can't interrupt a native page rendering,
 * so we need these renderings to be as fast as possible, and be able to interrupt them
 * as soon as we can.
 * - The parts are loaded when the current offset or the current zoom level changes
 *
 *
 * Important :
 * - DocumentPage = A page of the PDF document.
 * - UserPage = A page as defined by the user.
 * By default, they're the same. But the user can change the pages order
 * using [.load]. In this
 * particular case, a userPage of 5 can refer to a documentPage of 17.
 */
open class PDFView(context: Context?, set: AttributeSet?) : RelativeLayout(context, set) {
    var minZoom: Float = DEFAULT_MIN_SCALE
    var midZoom: Float = DEFAULT_MID_SCALE
    var maxZoom: Float = DEFAULT_MAX_SCALE

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    internal enum class ScrollDir {
        NONE, START, END
    }

    private var scrollDir = ScrollDir.NONE

    /**
     * Rendered parts go to the cache manager
     */
    internal val cacheManager = CacheManager()

    /**
     * Animation manager manage all offset and zoom animation
     */
    private val animationManager = AnimationManager(this)

    /**
     * Drag manager manage all touch events
     */
    private val dragPinchManager = DragPinchManager(this, animationManager)

    @JvmField
    internal var pdfFile: PdfFile? = null

    /**
     * The index of the current sequence
     */
    var currentPage: Int = 0
        private set

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    var currentXOffset: Float = 0f
        private set

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    var currentYOffset: Float = 0f
        private set

    /**
     * The zoom level, always >= 1
     */
    var zoom: Float = 1f
        private set

    /**
     * True if the PDFView has been recycled
     */
    var isRecycled: Boolean = true
        private set

    /**
     * Current state of the view
     */
    private var state = State.DEFAULT

    /**
     * Async task used during the loading phase to decode a PDF document
     */
    private var decodingAsyncTask: DecodingAsyncTask? = null

    /**
     * The thread [.renderingHandler] will run on
     */
    private var renderingHandlerThread: HandlerThread? = HandlerThread("PDF renderer")

    /**
     * Handler always waiting in the background and rendering tasks
     */
    internal var renderingHandler: RenderingHandler? = null

    private val pagesLoader = PagesLoader(this)

    var callbacks = Callbacks()

    /**
     * Paint object for drawing
     */
    private val paint: Paint = Paint()

    /**
     * Paint object for drawing debug stuff
     */
    private val debugPaint: Paint = Paint()

    /**
     * Policy for fitting pages to screen
     */
    var pageFitPolicy = FitPolicy.WIDTH
        private set

    var isFitEachPage: Boolean = false
        private set

    private var defaultPage = 0

    var isOnDualPageMode: Boolean = false
        private set

    var isOnLandscapeOrientation: Boolean = false
        private set

    /**
     * True if should scroll through pages vertically instead of horizontally
     */
    var isSwipeVertical: Boolean = true
        private set

    var isSwipeEnabled: Boolean = true

    var isDoubletapEnabled: Boolean = true
        private set

    private var nightMode = false

    var isPageSnap: Boolean = true

    /**
     * Pdfium core for loading and rendering PDFs
     */
    private val pdfiumCore: PdfiumCore = PdfiumCore(context, this.createPdfiumCoreConfig())

    var scrollHandle: ScrollHandle? = null
        private set

    private var isScrollHandleInit = false

    /**
     * True if bitmap should use ARGB_8888 format and take more memory
     * False if bitmap should be compressed by using RGB_565 format and take less memory
     */
    var isBestQuality: Boolean = false
        private set

    /**
     * True if annotations should be rendered
     * False otherwise
     */
    var isAnnotationRendering: Boolean = false
        private set

    /**
     * True if the view should render during scaling<br></br>
     * Can not be forced on older API versions (< Build.VERSION_CODES.KITKAT) as the GestureDetector does
     * not detect scrolling while scaling.<br></br>
     * False otherwise
     */
    private var renderDuringScale = false

    /**
     * Antialiasing and bitmap filtering
     */
    var isAntialiasing: Boolean = true
        private set
    private val antialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Spacing between pages, in px
     */
    var spacingPx: Int = 0
        private set

    /**
     * Add dynamic spacing to fit each page separately on the screen.
     */
    var isAutoSpacingEnabled: Boolean = false
        private set

    /**
     * Fling a single page at a time
     */
    var isPageFlingEnabled: Boolean = true
        private set

    /**
     * Pages numbers used when calling onDrawAllListener
     */
    private val onDrawPagesNums: MutableList<Int> = ArrayList(10)

    /**
     * Holds info whether view has been added to layout and has width and height
     */
    private var hasSize = false

    /**
     * Holds last used Configurator that should be loaded when view has size
     */
    private var waitingDocumentConfigurator: Configurator? = null

    /**
     * Construct the initial view
     */
    init {
        debugPaint.style = Paint.Style.STROKE

        setWillNotDraw(!isInEditMode)
    }

    /**
     * Method to be override to customize pdfiumCore's config for subclass
     */
    protected fun createPdfiumCoreConfig(): Config {
        return Config()
    }

    private fun load(docSource: DocumentSource, password: String?, userPages: IntArray? = null) {
        check(this.isRecycled) { "Don't call load on a PDF View without recycling it first." }

        this.isRecycled = false
        // Start decoding document
        decodingAsyncTask = DecodingAsyncTask(docSource, password, userPages, this, pdfiumCore)
        decodingAsyncTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * Go to the given page.
     *
     * @param page Page index.
     */
    @JvmOverloads
    fun jumpTo(page: Int, withAnimation: Boolean = false) {
        var page = page
        if (pdfFile == null) {
            return
        }

        page = pdfFile!!.determineValidPageNumberFrom(page)
        val offset = if (page == 0) 0f else -pdfFile!!.getPageOffset(page, zoom)
        if (this.isSwipeVertical) {
            if (withAnimation) {
                animationManager.startYAnimation(currentYOffset, offset)
            } else {
                moveTo(currentXOffset, offset)
            }
        } else {
            if (withAnimation) {
                animationManager.startXAnimation(currentXOffset, offset)
            } else {
                moveTo(offset, currentYOffset)
            }
        }
        showPage(page)
    }

    fun showPage(pageNb: Int) {
        var pageNb = pageNb
        if (this.isRecycled) {
            return
        }

        // Check the page number and makes the
        // difference between UserPages and DocumentPages
        pageNb = pdfFile!!.determineValidPageNumberFrom(pageNb)
        currentPage = pageNb

        loadPages()

        if (scrollHandle != null && !documentFitsView()) {
            scrollHandle!!.setPageNum(currentPage + 1)
        }

        callbacks.callOnPageChange(currentPage, pdfFile!!.pagesCount)
    }

    var positionOffset: Float
        /**
         * Get current position as ratio of document length to visible area.
         * 0 means that document start is visible, 1 that document end is visible
         *
         * @return offset between 0 and 1
         */
        get() {
            val offset = if (this.isSwipeVertical) {
                -currentYOffset / (pdfFile!!.getDocLen(zoom) - height)
            } else {
                -currentXOffset / (pdfFile!!.getDocLen(zoom) - width)
            }
            return limit(offset, 0f, 1f)
        }
        set(progress) {
            setPositionOffset(progress, true)
        }

    /**
     * @param progress   must be between 0 and 1
     * @param moveHandle whether to move scroll handle
     * @see PDFView.positionOffset
     */
    fun setPositionOffset(progress: Float, moveHandle: Boolean) {
        if (this.isSwipeVertical) {
            moveTo(
                currentXOffset,
                (-pdfFile!!.getDocLen(zoom) + height) * progress,
                moveHandle
            )
        } else {
            moveTo((-pdfFile!!.getDocLen(zoom) + width) * progress, currentYOffset, moveHandle)
        }
        loadPageByOffset()
    }

    fun stopFling() {
        animationManager.stopFling()
    }

    val pageCount: Int
        get() {
            return pdfFile?.pagesCount ?: 0
        }

    fun setNightMode(nightMode: Boolean) {
        this.nightMode = nightMode
        if (nightMode) {
            val colorMatrixInverted =
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )

            val filter = ColorMatrixColorFilter(colorMatrixInverted)
            paint.setColorFilter(filter)
        } else {
            paint.setColorFilter(null)
        }
    }

    fun enableDoubletap(enableDoubletap: Boolean) {
        this.isDoubletapEnabled = enableDoubletap
    }

    fun onPageError(ex: PageRenderingException) {
        if (!callbacks.callOnPageError(ex.page, ex.cause)) {
            Log.e(TAG, "Cannot open page " + ex.page, ex.cause)
        }
    }

    fun recycle() {
        waitingDocumentConfigurator = null

        animationManager.stopAll()
        dragPinchManager.disable()

        // Stop tasks
        renderingHandler?.stop()
        renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        decodingAsyncTask?.cancel(true)

        // Clear caches
        cacheManager.recycle()

        if (isScrollHandleInit) {
            scrollHandle?.destroyLayout()
        }

        pdfFile?.dispose()
        pdfFile = null

        renderingHandler = null
        scrollHandle = null
        isScrollHandleInit = false
        currentYOffset = 0f
        currentXOffset = currentYOffset
        zoom = 1f
        this.isRecycled = true
        callbacks = Callbacks()
        state = State.DEFAULT
    }

    /**
     * Handle fling animation
     */
    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) {
            return
        }
        animationManager.computeFling()
    }

    override fun onDetachedFromWindow() {
        recycle()
        renderingHandlerThread?.quitSafely()
        renderingHandlerThread = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        hasSize = true
        waitingDocumentConfigurator?.load()
        if (isInEditMode || state != State.SHOWN) {
            return
        }

        // calculates the position of the point which in the center of view relative to big strip
        val centerPointInStripXOffset = -currentXOffset + oldw * 0.5f
        val centerPointInStripYOffset = -currentYOffset + oldh * 0.5f

        val relativeCenterPointInStripXOffset: Float
        val relativeCenterPointInStripYOffset: Float

        if (this.isSwipeVertical) {
            relativeCenterPointInStripXOffset = centerPointInStripXOffset / pdfFile!!.maxPageWidth
            relativeCenterPointInStripYOffset =
                centerPointInStripYOffset / pdfFile!!.getDocLen(zoom)
        } else {
            relativeCenterPointInStripXOffset =
                centerPointInStripXOffset / pdfFile!!.getDocLen(zoom)
            relativeCenterPointInStripYOffset = centerPointInStripYOffset / pdfFile!!.maxPageHeight
        }

        animationManager.stopAll()
        pdfFile!!.recalculatePageSizes(Size(w, h))

        if (this.isSwipeVertical) {
            currentXOffset = -relativeCenterPointInStripXOffset * pdfFile!!.maxPageWidth + w * 0.5f
            currentYOffset =
                -relativeCenterPointInStripYOffset * pdfFile!!.getDocLen(zoom) + h * 0.5f
        } else {
            currentXOffset =
                -relativeCenterPointInStripXOffset * pdfFile!!.getDocLen(zoom) + w * 0.5f
            currentYOffset = -relativeCenterPointInStripYOffset * pdfFile!!.maxPageHeight + h * 0.5f
        }
        moveTo(currentXOffset, currentYOffset)
        loadPageByOffset()
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (pdfFile == null) {
            return true
        }

        if (this.isSwipeVertical) {
            if (direction < 0 && currentXOffset < 0) {
                return true
            } else if (direction > 0 && currentXOffset + toCurrentScale(pdfFile!!.maxPageWidth) > width) {
                return true
            }
        } else {
            if (direction < 0 && currentXOffset < 0) {
                return true
            } else if (direction > 0 && currentXOffset + pdfFile!!.getDocLen(zoom) > width) {
                return true
            }
        }
        return false
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (pdfFile == null) {
            return true
        }

        if (this.isSwipeVertical) {
            if (direction < 0 && currentYOffset < 0) {
                return true
            } else if (direction > 0 && currentYOffset + pdfFile!!.getDocLen(zoom) > height) {
                return true
            }
        } else {
            if (direction < 0 && currentYOffset < 0) {
                return true
            } else if (direction > 0 && currentYOffset + toCurrentScale(pdfFile!!.maxPageHeight) > height) {
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) {
            return
        }

        // As I said in this class javadoc, we can think of this canvas as a huge
        // strip on which we draw all the images. We actually only draw the rendered
        // parts, of course, but we render them in the place they belong in this huge
        // strip.

        // That's where Canvas.translate(x, y) becomes very helpful.
        // This is the situation :
        //  _______________________________________________
        // |   			 |					 			   |
        // | the actual  |					The big strip  |
        // |	canvas	 | 								   |
        // |_____________|								   |
        // |_______________________________________________|
        //
        // If the rendered part is on the bottom right corner of the strip
        // we can draw it but we won't see it because the canvas is not big enough.

        // But if we call translate(-X, -Y) on the canvas just before drawing the object :
        //  _______________________________________________
        // |   			  					  _____________|
        // |   The big strip     			 |			   |
        // |		    					 |	the actual |
        // |								 |	canvas	   |
        // |_________________________________|_____________|
        //
        // The object will be on the canvas.
        // This technique is massively used in this method, and allows
        // abstraction of the screen position when rendering the parts.

        // Draws background
        if (this.isAntialiasing) {
            canvas.setDrawFilter(antialiasFilter)
        }

        val bg = background
        if (bg == null) {
            canvas.drawColor(if (nightMode) Color.BLACK else Color.WHITE)
        } else {
            bg.draw(canvas)
        }

        if (this.isRecycled) {
            return
        }

        if (state != State.SHOWN) {
            return
        }

        // Moves the canvas before drawing any element
        val currentXOffset = this.currentXOffset
        val currentYOffset = this.currentYOffset
        canvas.translate(currentXOffset, currentYOffset)

        // Draws thumbnails
        for (part in cacheManager.getThumbnails()) {
            drawPart(canvas, part)
        }

        // Draws parts
        for (part in cacheManager.pageParts) {
            drawPart(canvas, part)
            if (callbacks.onDrawAll != null
                && !onDrawPagesNums.contains(part.page)
            ) {
                onDrawPagesNums.add(part.page)
            }
        }

        for (page in onDrawPagesNums) {
            drawWithListener(canvas, page, callbacks.onDrawAll)
        }
        onDrawPagesNums.clear()

        drawWithListener(canvas, currentPage, callbacks.onDraw)

        // Restores the canvas position
        canvas.translate(-currentXOffset, -currentYOffset)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        if (listener != null) {
            val translateX: Float
            val translateY: Float
            if (this.isSwipeVertical) {
                translateX = 0f
                translateY = pdfFile!!.getPageOffset(page, zoom)
            } else {
                translateY = 0f
                translateX = pdfFile!!.getPageOffset(page, zoom)
            }

            canvas.translate(translateX, translateY)
            val size = pdfFile!!.getPageSize(page)
            listener.onLayerDrawn(
                canvas,
                toCurrentScale(size.width),
                toCurrentScale(size.height),
                page
            )

            canvas.translate(-translateX, -translateY)
        }
    }

    /**
     * Draw a given PagePart on the canvas
     */
    private fun drawPart(canvas: Canvas, part: PagePart) {
        // Can seem strange, but avoid lot of calls
        val pageRelativeBounds = part.pageRelativeBounds
        val renderedBitmap = part.renderedBitmap

        if (renderedBitmap!!.isRecycled) {
            return
        }

        // Move to the target page
        var localTranslationX: Float
        var localTranslationY: Float
        val size = pdfFile!!.getPageSize(part.page)

        if (this.isSwipeVertical) {
            localTranslationY = pdfFile!!.getPageOffset(part.page, zoom)
            val maxWidth = pdfFile!!.maxPageWidth
            localTranslationX = toCurrentScale(maxWidth - size.width) / 2
        } else {
            localTranslationX = pdfFile!!.getPageOffset(part.page, zoom)
            val maxHeight = pdfFile!!.maxPageHeight
            localTranslationY = toCurrentScale(maxHeight - size.height) / 2
        }
        canvas.translate(localTranslationX, localTranslationY)

        val srcRect = Rect(
            0, 0, renderedBitmap.getWidth(),
            renderedBitmap.getHeight()
        )

        val offsetX = toCurrentScale(pageRelativeBounds.left * size.width)
        val offsetY = toCurrentScale(pageRelativeBounds.top * size.height)
        val width = toCurrentScale(pageRelativeBounds.width() * size.width)
        val height = toCurrentScale(pageRelativeBounds.height() * size.height)

        // If we use float values for this rectangle, there will be
        // a possible gap between page parts, especially when
        // the zoom level is high.
        val dstRect = RectF(
            offsetX.toInt().toFloat(), offsetY.toInt().toFloat(),
            (offsetX + width).toInt().toFloat(),
            (offsetY + height).toInt().toFloat()
        )

        // Check if bitmap is in the screen
        val translationX = currentXOffset + localTranslationX
        val translationY = currentYOffset + localTranslationY
        if (translationX + dstRect.left >= getWidth() || translationX + dstRect.right <= 0 || translationY + dstRect.top >= getHeight() || translationY + dstRect.bottom <= 0) {
            canvas.translate(-localTranslationX, -localTranslationY)
            return
        }

        canvas.drawBitmap(renderedBitmap, srcRect, dstRect, paint)

        if (Constants.DEBUG_MODE) {
            debugPaint.setColor(if (part.page % 2 == 0) Color.RED else Color.BLUE)
            canvas.drawRect(dstRect, debugPaint)
        }

        // Restore the canvas position
        canvas.translate(-localTranslationX, -localTranslationY)
    }

    /**
     * Load all the parts around the center of the screen,
     * taking into account X and Y offsets, zoom level, and
     * the current page displayed
     */
    fun loadPages() {
        if (pdfFile == null || renderingHandler == null) {
            return
        }

        // Cancel all current tasks
        renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        cacheManager.makeANewSet()

        pagesLoader.loadPages()
        redraw()
    }

    /**
     * Called when the PDF is loaded
     */
    internal fun loadComplete(pdfFile: PdfFile) {
        state = State.LOADED

        this.pdfFile = pdfFile

        if (renderingHandlerThread == null) {
            renderingHandlerThread = HandlerThread("PDF renderer")
        }

        if (!renderingHandlerThread!!.isAlive) {
            renderingHandlerThread!!.start()
        }

        renderingHandler = RenderingHandler(renderingHandlerThread!!.getLooper(), this)
        renderingHandler!!.start()

        if (scrollHandle != null) {
            scrollHandle!!.setupLayout(this)
            isScrollHandleInit = true
        }

        dragPinchManager.enable()

        callbacks.callOnLoadComplete(pdfFile.pagesCount)

        jumpTo(defaultPage, false)
    }

    fun loadError(t: Throwable) {
        state = State.ERROR
        // store reference, because callbacks will be cleared in recycle() method
        val onErrorListener = callbacks.onError
        recycle()
        invalidate()
        if (onErrorListener != null) {
            onErrorListener.onError(t)
        } else {
            Log.e("PDFView", "load pdf error", t)
        }
    }

    fun redraw() {
        invalidate()
    }

    /**
     * Called when a rendering task is over and
     * a PagePart has been freshly created.
     *
     * @param part The created PagePart.
     */
    fun onBitmapRendered(part: PagePart) {
        // when it is first rendered part
        if (state == State.LOADED) {
            state = State.SHOWN
            callbacks.callOnRender(pdfFile!!.pagesCount)
        }

        if (part.isThumbnail) {
            cacheManager.cacheThumbnail(part)
        } else {
            cacheManager.cachePart(part)
        }
        redraw()
    }

    /**
     * Move to the given X and Y offsets, but check them ahead of time
     * to be sure not to go outside the the big strip.
     *
     * @param offsetX    The big strip X offset to use as the left border of the screen.
     * @param offsetY    The big strip Y offset to use as the right border of the screen.
     * @param moveHandle whether to move scroll handle or not
     */
    @JvmOverloads
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean = true) {
        var offsetX = offsetX
        var offsetY = offsetY
        if (this.isSwipeVertical) {
            // Check X offset
            val scaledPageWidth = toCurrentScale(pdfFile!!.maxPageWidth)
            if (scaledPageWidth < width) {
                offsetX = width / 2 - scaledPageWidth / 2
            } else {
                if (offsetX > 0) {
                    offsetX = 0f
                } else if (offsetX + scaledPageWidth < width) {
                    offsetX = width - scaledPageWidth
                }
            }

            // Check Y offset
            val contentHeight = pdfFile!!.getDocLen(zoom)
            if (contentHeight < height) { // whole document height visible on screen
                offsetY = (height - contentHeight) / 2
            } else {
                if (offsetY > 0) { // top visible
                    offsetY = 0f
                } else if (offsetY + contentHeight < height) { // bottom visible
                    offsetY = -contentHeight + height
                }
            }

            scrollDir = if (offsetY < currentYOffset) {
                ScrollDir.END
            } else if (offsetY > currentYOffset) {
                ScrollDir.START
            } else {
                ScrollDir.NONE
            }
        } else {
            // Check Y offset
            val scaledPageHeight = toCurrentScale(pdfFile!!.maxPageHeight)
            if (scaledPageHeight < height) {
                offsetY = height / 2 - scaledPageHeight / 2
            } else {
                if (offsetY > 0) {
                    offsetY = 0f
                } else if (offsetY + scaledPageHeight < height) {
                    offsetY = height - scaledPageHeight
                }
            }

            // Check X offset
            val contentWidth = pdfFile!!.getDocLen(zoom)
            if (contentWidth < width) { // whole document width visible on screen
                offsetX = (width - contentWidth) / 2
            } else {
                if (offsetX > 0) { // left visible
                    offsetX = 0f
                } else if (offsetX + contentWidth < width) { // right visible
                    offsetX = -contentWidth + width
                }
            }

            scrollDir = if (offsetX < currentXOffset) {
                ScrollDir.END
            } else if (offsetX > currentXOffset) {
                ScrollDir.START
            } else {
                ScrollDir.NONE
            }
        }

        currentXOffset = offsetX
        currentYOffset = offsetY
        val positionOffset = this.positionOffset

        if (moveHandle && !documentFitsView()) {
            scrollHandle?.setScroll(positionOffset)
        }

        callbacks.callOnPageScroll(this.currentPage, positionOffset)

        redraw()
    }

    fun loadPageByOffset() {
        if (0 == pdfFile?.pagesCount) {
            return
        }

        val offset: Float
        val screenCenter: Float
        if (this.isSwipeVertical) {
            offset = currentYOffset
            screenCenter = (height.toFloat()) / 2
        } else {
            offset = currentXOffset
            screenCenter = (width.toFloat()) / 2
        }

        val page = pdfFile!!.getPageAtOffset(-(offset - screenCenter), zoom)

        if (page >= 0 && page <= pdfFile!!.pagesCount - 1 && page != this.currentPage) {
            showPage(page)
        } else {
            loadPages()
        }
    }

    /**
     * Animate to the nearest snapping position for the current SnapPolicy
     */
    @JvmOverloads
    fun performPageSnap(withAnimation: Boolean = true) {
        if (!this.isPageSnap || pdfFile == null || pdfFile!!.pagesCount == 0) {
            return
        }
        val centerPage = findFocusPage(currentXOffset, currentYOffset)
        val edge = findSnapEdge(centerPage)
        if (edge == SnapEdge.NONE) {
            return
        }

        val offset = snapOffsetForPage(centerPage, edge)
        if (this.isSwipeVertical) {
            if (withAnimation) {
                animationManager.startYAnimation(currentYOffset, -offset)
            } else {
                moveTo(currentXOffset, -offset)
            }
        } else {
            if (withAnimation) {
                animationManager.startXAnimation(currentXOffset, -offset)
            } else {
                moveTo(-offset, currentYOffset)
            }
        }
    }

    /**
     * Find the edge to snap to when showing the specified page
     */
    fun findSnapEdge(page: Int): SnapEdge {
        if (!this.isPageSnap || page < 0) {
            return SnapEdge.NONE
        }
        val currentOffset = if (this.isSwipeVertical) currentYOffset else currentXOffset
        val offset = -pdfFile!!.getPageOffset(page, zoom)
        val length = if (this.isSwipeVertical) height else width
        val pageLength = pdfFile!!.getPageLength(page, zoom)

        return if (length >= pageLength) {
            SnapEdge.CENTER
        } else if (currentOffset >= offset) {
            SnapEdge.START
        } else if (offset - pageLength > currentOffset - length) {
            SnapEdge.END
        } else {
            SnapEdge.NONE
        }
    }

    /**
     * Get the offset to move to in order to snap to the page
     */
    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge?): Float {
        var offset = pdfFile!!.getPageOffset(pageIndex, zoom)

        val length = (if (this.isSwipeVertical) height else width).toFloat()
        val pageLength = pdfFile!!.getPageLength(pageIndex, zoom)

        if (edge == SnapEdge.CENTER) {
            offset = offset - length / 2f + pageLength / 2f
        } else if (edge == SnapEdge.END) {
            offset = offset - length + pageLength
        }
        return offset
    }

    fun findFocusPage(xOffset: Float, yOffset: Float): Int {
        val currOffset = if (this.isSwipeVertical) yOffset else xOffset
        val length = (if (this.isSwipeVertical) height else width).toFloat()
        // make sure first and last page can be found
        if (currOffset > -1) {
            return 0
        } else if (currOffset < -pdfFile!!.getDocLen(zoom) + length + 1) {
            return pdfFile!!.pagesCount - 1
        }
        // else find page in center
        val center = currOffset - length / 2f
        return pdfFile!!.getPageAtOffset(-center, zoom)
    }

    /**
     * @return true if single page fills the entire screen in the scrolling direction
     */
    fun pageFillsScreen(): Boolean {
        val start = -pdfFile!!.getPageOffset(currentPage, zoom)
        val end = start - pdfFile!!.getPageLength(currentPage, zoom)
        return if (this.isSwipeVertical) {
            start > currentYOffset && end < currentYOffset - height
        } else {
            start > currentXOffset && end < currentXOffset - width
        }
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference you want to apply.
     * @param dy The Y difference you want to apply.
     * @see .moveTo
     */
    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(currentXOffset + dx, currentYOffset + dy)
    }

    /**
     * Change the zoom level
     */
    fun zoomTo(zoom: Float) {
        this.zoom = zoom
    }

    /**
     * Change the zoom level, relatively to a pivot point.
     * It will call moveTo() to make sure the given point stays
     * in the middle of the screen.
     *
     * @param zoom  The zoom level.
     * @param pivot The point on the screen that should stays.
     */
    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val dzoom = zoom / this.zoom
        zoomTo(zoom)
        var baseX = currentXOffset * dzoom
        var baseY = currentYOffset * dzoom
        baseX += (pivot.x - pivot.x * dzoom)
        baseY += (pivot.y - pivot.y * dzoom)
        moveTo(baseX, baseY)
    }

    /**
     * @see .zoomCenteredTo
     */
    fun zoomCenteredRelativeTo(dzoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom * dzoom, pivot)
    }

    /**
     * Checks if whole document can be displayed on screen, doesn't include zoom
     *
     * @return true if whole document can displayed at once, false otherwise
     */
    fun documentFitsView(): Boolean {
        val len = pdfFile!!.getDocLen(1f)
        return if (this.isSwipeVertical) {
            len < height
        } else {
            len < width
        }
    }

    fun fitToWidth(page: Int) {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet")
            return
        }
        zoomTo(width / pdfFile!!.getPageSize(page).width)
        jumpTo(page)
    }

    fun getPageSize(pageIndex: Int): SizeF {
        if (pdfFile == null) {
            return SizeF(0f, 0f)
        }
        return pdfFile!!.getPageSize(pageIndex)
    }

    fun toRealScale(size: Float): Float {
        return size / zoom
    }

    fun toCurrentScale(size: Float): Float {
        return size * zoom
    }

    val isZooming: Boolean
        get() = zoom != minZoom

    private fun setDefaultPage(defaultPage: Int) {
        this.defaultPage = defaultPage
    }

    fun resetZoom() {
        zoomTo(minZoom)
    }

    fun resetZoomWithAnimation() {
        zoomWithAnimation(minZoom)
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        animationManager.startZoomAnimation(centerX, centerY, zoom, scale)
    }

    fun zoomWithAnimation(scale: Float) {
        animationManager.startZoomAnimation(
            (width / 2f),
            (height / 2f),
            zoom,
            scale,
        )
    }

    /**
     * Get page number at given offset
     *
     * @param positionOffset scroll offset between 0 and 1
     * @return page number at given offset, starting from 0
     */
    fun getPageAtPositionOffset(positionOffset: Float): Int {
        return pdfFile!!.getPageAtOffset(pdfFile!!.getDocLen(zoom) * positionOffset, zoom)
    }

    fun useBestQuality(bestQuality: Boolean) {
        this.isBestQuality = bestQuality
    }

    fun setLandscapeOrientation(landscapeOrientation: Boolean) {
        this.isOnLandscapeOrientation = landscapeOrientation
    }

    fun setDualPageMode(dualPageMode: Boolean) {
        this.isOnDualPageMode = dualPageMode
    }

    fun enableAnnotationRendering(annotationRendering: Boolean) {
        this.isAnnotationRendering = annotationRendering
    }

    fun enableRenderDuringScale(renderDuringScale: Boolean) {
        this.renderDuringScale = renderDuringScale
    }

    fun enableAntialiasing(enableAntialiasing: Boolean) {
        this.isAntialiasing = enableAntialiasing
    }

    fun setPageFling(pageFling: Boolean) {
        this.isPageFlingEnabled = pageFling
    }

    private fun setSpacing(spacingDp: Int) {
        this.spacingPx = getDP(context, spacingDp)
    }

    private fun setAutoSpacing(autoSpacing: Boolean) {
        this.isAutoSpacingEnabled = autoSpacing
    }

    fun doRenderDuringScale(): Boolean {
        return renderDuringScale
    }

    val documentMeta: PdfDocument.Meta?
        /**
         * Returns null if document is not loaded
         */
        get() {
            return pdfFile?.metaData
        }

    val tableOfContents: List<PdfDocument.Bookmark>
        /**
         * Will be empty until document is loaded
         */
        get() {
            return pdfFile?.bookmarks ?: listOf()
        }

    /**
     * Will be empty until document is loaded
     */
    fun getLinks(page: Int): List<PdfDocument.Link> {
        return pdfFile?.getPageLinks(page) ?: listOf()
    }

    /**
     * Use an asset file as the pdf source
     */
    fun fromAsset(assetName: String): Configurator {
        return Configurator(AssetSource(assetName), this)
    }

    /**
     * Use a file as the pdf source
     */
    fun fromFile(file: File?): Configurator {
        return Configurator(FileSource(file), this)
    }

    /**
     * Use URI as the pdf source, for use with content providers
     */
    fun fromUri(uri: Uri): Configurator {
        return Configurator(UriSource(uri), this)
    }

    /**
     * Use bytearray as the pdf source, documents is not saved
     */
    fun fromBytes(bytes: ByteArray?): Configurator {
        return Configurator(ByteArraySource(bytes), this)
    }

    /**
     * Use stream as the pdf source. Stream will be written to bytearray, because native code does not support Java Streams
     */
    fun fromStream(stream: InputStream): Configurator {
        return Configurator(InputStreamSource(stream), this)
    }

    /**
     * Use custom source as pdf source
     */
    fun fromSource(docSource: DocumentSource): Configurator {
        return Configurator(docSource, this)
    }

    private enum class State {
        DEFAULT, LOADED, SHOWN, ERROR
    }

    class Configurator(private val documentSource: DocumentSource, private val pdfView: PDFView) {
        private var pageNumbers: IntArray? = null

        private var enableSwipe = true

        private var enableDoubletap = true

        private var onDrawListener: OnDrawListener? = null

        private var onDrawAllListener: OnDrawListener? = null

        private var onLoadCompleteListener: OnLoadCompleteListener? = null

        private var onErrorListener: OnErrorListener? = null

        private var onPageChangeListener: OnPageChangeListener? = null

        private var onPageScrollListener: OnPageScrollListener? = null

        private var onRenderListener: OnRenderListener? = null

        private var onTapListener: OnTapListener? = null

        private var onLongPressListener: OnLongPressListener? = null

        private var onPageErrorListener: OnPageErrorListener? = null

        private var linkHandler: LinkHandler? = DefaultLinkHandler(pdfView)

        private var defaultPage = 0

        private var landscapeOrientation = false

        private var dualPageMode = false

        private var swipeHorizontal = false

        private var annotationRendering = false

        private var password: String? = null

        private var scrollHandle: ScrollHandle? = null

        private var antialiasing = true

        private var spacing = 0

        private var autoSpacing = false

        private var pageFitPolicy: FitPolicy = FitPolicy.WIDTH

        private var fitEachPage = false

        private var pageFling = false

        private var pageSnap = false

        private var nightMode = false

        fun pages(vararg pageNumbers: Int): Configurator {
            this.pageNumbers = pageNumbers
            return this
        }

        fun enableSwipe(enableSwipe: Boolean): Configurator {
            this.enableSwipe = enableSwipe
            return this
        }

        fun enableDoubletap(enableDoubletap: Boolean): Configurator {
            this.enableDoubletap = enableDoubletap
            return this
        }

        fun enableAnnotationRendering(annotationRendering: Boolean): Configurator {
            this.annotationRendering = annotationRendering
            return this
        }

        fun onDraw(onDrawListener: OnDrawListener?): Configurator {
            this.onDrawListener = onDrawListener
            return this
        }

        fun onDrawAll(onDrawAllListener: OnDrawListener?): Configurator {
            this.onDrawAllListener = onDrawAllListener
            return this
        }

        fun onLoad(onLoadCompleteListener: OnLoadCompleteListener?): Configurator {
            this.onLoadCompleteListener = onLoadCompleteListener
            return this
        }

        fun onPageScroll(onPageScrollListener: OnPageScrollListener?): Configurator {
            this.onPageScrollListener = onPageScrollListener
            return this
        }

        fun onError(onErrorListener: OnErrorListener?): Configurator {
            this.onErrorListener = onErrorListener
            return this
        }

        fun onPageError(onPageErrorListener: OnPageErrorListener?): Configurator {
            this.onPageErrorListener = onPageErrorListener
            return this
        }

        fun onPageChange(onPageChangeListener: OnPageChangeListener?): Configurator {
            this.onPageChangeListener = onPageChangeListener
            return this
        }

        fun onRender(onRenderListener: OnRenderListener?): Configurator {
            this.onRenderListener = onRenderListener
            return this
        }

        fun onTap(onTapListener: OnTapListener?): Configurator {
            this.onTapListener = onTapListener
            return this
        }

        fun onLongPress(onLongPressListener: OnLongPressListener?): Configurator {
            this.onLongPressListener = onLongPressListener
            return this
        }

        fun linkHandler(linkHandler: LinkHandler?): Configurator {
            this.linkHandler = linkHandler
            return this
        }

        fun defaultPage(defaultPage: Int): Configurator {
            this.defaultPage = defaultPage
            return this
        }

        fun landscapeOrientation(landscapeOrientation: Boolean): Configurator {
            this.landscapeOrientation = landscapeOrientation
            return this
        }

        fun dualPageMode(dualPageMode: Boolean): Configurator {
            this.dualPageMode = dualPageMode
            return this
        }

        fun swipeHorizontal(swipeHorizontal: Boolean): Configurator {
            this.swipeHorizontal = swipeHorizontal
            return this
        }

        fun password(password: String?): Configurator {
            this.password = password
            return this
        }

        fun scrollHandle(scrollHandle: ScrollHandle?): Configurator {
            this.scrollHandle = scrollHandle
            return this
        }

        fun enableAntialiasing(antialiasing: Boolean): Configurator {
            this.antialiasing = antialiasing
            return this
        }

        fun spacing(spacing: Int): Configurator {
            this.spacing = spacing
            return this
        }

        fun autoSpacing(autoSpacing: Boolean): Configurator {
            this.autoSpacing = autoSpacing
            return this
        }

        fun pageFitPolicy(pageFitPolicy: FitPolicy): Configurator {
            this.pageFitPolicy = pageFitPolicy
            return this
        }

        fun fitEachPage(fitEachPage: Boolean): Configurator {
            this.fitEachPage = fitEachPage
            return this
        }

        fun pageSnap(pageSnap: Boolean): Configurator {
            this.pageSnap = pageSnap
            return this
        }

        fun pageFling(pageFling: Boolean): Configurator {
            this.pageFling = pageFling
            return this
        }

        fun nightMode(nightMode: Boolean): Configurator {
            this.nightMode = nightMode
            return this
        }

        fun disableLongpress(): Configurator {
            pdfView.dragPinchManager.disableLongpress()
            return this
        }

        fun load() {
            if (!pdfView.hasSize) {
                pdfView.waitingDocumentConfigurator = this
                return
            }
            pdfView.recycle()
            pdfView.callbacks.setOnLoadComplete(onLoadCompleteListener)
            pdfView.callbacks.onError = onErrorListener
            pdfView.callbacks.onDraw = onDrawListener
            pdfView.callbacks.onDrawAll = onDrawAllListener
            pdfView.callbacks.setOnPageChange(onPageChangeListener)
            pdfView.callbacks.setOnPageScroll(onPageScrollListener)
            pdfView.callbacks.setOnRender(onRenderListener)
            pdfView.callbacks.setOnTap(onTapListener)
            pdfView.callbacks.setOnLongPress(onLongPressListener)
            pdfView.callbacks.setOnPageError(onPageErrorListener)
            pdfView.callbacks.setLinkHandler(linkHandler)
            pdfView.isSwipeEnabled = enableSwipe
            pdfView.setNightMode(nightMode)
            pdfView.enableDoubletap(enableDoubletap)
            pdfView.setDefaultPage(defaultPage)
            pdfView.setLandscapeOrientation(landscapeOrientation)
            pdfView.setDualPageMode(dualPageMode)
            pdfView.isSwipeVertical = !swipeHorizontal
            pdfView.enableAnnotationRendering(annotationRendering)
            pdfView.scrollHandle = scrollHandle
            pdfView.enableAntialiasing(antialiasing)
            pdfView.setSpacing(spacing)
            pdfView.setAutoSpacing(autoSpacing)
            pdfView.pageFitPolicy = pageFitPolicy
            pdfView.isFitEachPage = fitEachPage
            pdfView.isPageSnap = pageSnap
            pdfView.setPageFling(pageFling)

            if (pageNumbers != null) {
                pdfView.load(documentSource, password, pageNumbers)
            } else {
                pdfView.load(documentSource, password)
            }
        }
    }

    companion object {
        private val TAG: String = PDFView::class.java.getSimpleName()

        const val DEFAULT_MAX_SCALE: Float = 3.0f
        const val DEFAULT_MID_SCALE: Float = 1.75f
        const val DEFAULT_MIN_SCALE: Float = 1.0f
    }
}
