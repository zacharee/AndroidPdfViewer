/**
 * Copyright 2017 Bartosz Schiller
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

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.SizeF
import android.util.SparseBooleanArray
import com.github.barteksc.pdfviewer.exception.PageRenderingException
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.github.barteksc.pdfviewer.util.PageSizeCalculator
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.util.Size
import kotlin.math.max

internal class PdfFile(
    private val pdfiumCore: PdfiumCore,
    private var pdfDocument: PdfDocument,
    private val pageFitPolicy: FitPolicy,
    viewSize: Size,
    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private var originalUserPages: IntArray?,
    /** True if dualPageMode is on */
    private val showTwoPages: Boolean,
    /** True if scrolling is vertical, else it's horizontal  */
    private val isVertical: Boolean,
    /** Fixed spacing between pages in pixels  */
    private val spacingPx: Int,
    /** Calculate spacing automatically so each page fits on it's own in the center of the view  */
    private val autoSpacing: Boolean,
    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
    private val fitEachPage: Boolean,
    private val isLandscape: Boolean
) {
    var pagesCount: Int = 0
        private set

    /** Original page sizes  */
    private val originalPageSizes: MutableList<Size> = ArrayList()

    /** Scaled page sizes  */
    private val pageSizes: MutableList<SizeF> = ArrayList()

    /** Opened pages with indicator whether opening was successful  */
    private val openedPages = SparseBooleanArray()

    /** Page with maximum width  */
    private var originalMaxWidthPageSize = Size(0, 0)

    /** Page with maximum height  */
    private var originalMaxHeightPageSize = Size(0, 0)

    /** Scaled page with maximum height  */
    private var maxHeightPageSize: SizeF? = SizeF(0f, 0f)

    /** Scaled page with maximum width  */
    private var maxWidthPageSize: SizeF? = SizeF(0f, 0f)

    /** Calculated offsets for pages  */
    private val pageOffsets: MutableList<Float?> = ArrayList()

    /** Calculated auto spacing for pages  */
    private val pageSpacing: MutableList<Float?> = ArrayList()

    /** Calculated document length (width or height, depending on swipe mode)  */
    private var documentLength = 0f

    init {
        setup(viewSize)
    }

    private fun setup(viewSize: Size) {
        pagesCount = originalUserPages?.size ?: pdfDocument.getPageCount()

        for (i in 0..<pagesCount) {
            val pageSize = pdfiumCore.getPageSize(pdfDocument, documentPage(i))
            if (pageSize.width > originalMaxWidthPageSize.width) {
                originalMaxWidthPageSize = pageSize
            }
            if (pageSize.height > originalMaxHeightPageSize.height) {
                originalMaxHeightPageSize = pageSize
            }
            originalPageSizes.add(pageSize)
        }

        recalculatePageSizes(viewSize)
    }

    /**
     * Call after view size change to recalculate page sizes, offsets and document length
     *
     * @param viewSize new size of changed view
     */
    fun recalculatePageSizes(viewSize: Size) {
        pageSizes.clear()
        val calculator = PageSizeCalculator(
            pageFitPolicy, originalMaxWidthPageSize,
            originalMaxHeightPageSize, viewSize, fitEachPage
        )
        maxWidthPageSize = calculator.optimalMaxWidthPageSize
        maxHeightPageSize = calculator.optimalMaxHeightPageSize

        for (size in originalPageSizes) {
            pageSizes.add(calculator.calculate(size, showTwoPages, isLandscape))
        }
        if (autoSpacing) {
            prepareAutoSpacing(viewSize)
        }
        prepareDocLen()
        preparePagesOffset()
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) {
            return SizeF(0f, 0f)
        }
        return pageSizes[pageIndex]
    }

    fun getScaledPageSize(pageIndex: Int, zoom: Float): SizeF {
        val size = getPageSize(pageIndex)
        return SizeF(size.width * zoom, size.height * zoom)
    }

    val maxPageSize: SizeF?
        /**
         * get page size with biggest dimension (width in vertical mode and height in horizontal mode)
         *
         * @return size of page
         */
        get() = if (isVertical) maxWidthPageSize else maxHeightPageSize

    val maxPageWidth: Float
        get() = this.maxPageSize!!.width

    val maxPageHeight: Float
        get() = this.maxPageSize!!.height

    private fun prepareAutoSpacing(viewSize: Size) {
        pageSpacing.clear()
        for (i in 0..<this.pagesCount) {
            val pageSize = pageSizes[i]
            var spacing = max(
                0f,
                if (isVertical) viewSize.height - pageSize.height else viewSize.width - pageSize.width
            )
            if (i < this.pagesCount - 1) {
                spacing += spacingPx.toFloat()
            }
            pageSpacing.add(spacing)
        }
    }

    private fun prepareDocLen() {
        var length = 0f
        for (i in 0..<this.pagesCount) {
            val pageSize = pageSizes[i]
            length += if (isVertical) pageSize.height else pageSize.width
            if (autoSpacing) {
                length += pageSpacing[i]!!
            } else if (i < this.pagesCount - 1) {
                length += spacingPx.toFloat()
            }
        }
        documentLength = length
    }

    private fun preparePagesOffset() {
        pageOffsets.clear()
        var offset = 0f
        for (i in 0..<this.pagesCount) {
            val pageSize = pageSizes[i]
            val size = if (isVertical) pageSize.height else pageSize.width
            if (autoSpacing) {
                offset += pageSpacing[i]!! / 2f
                if (i == 0) {
                    offset -= spacingPx / 2f
                } else if (i == this.pagesCount - 1) {
                    offset += spacingPx / 2f
                }
                pageOffsets.add(offset)
                offset += size + pageSpacing[i]!! / 2f
            } else {
                pageOffsets.add(offset)
                offset += size + spacingPx
            }
        }
    }

    fun getDocLen(zoom: Float): Float {
        return documentLength * zoom
    }

    /**
     * Get the page's height if swiping vertical, or width if swiping horizontal.
     */
    fun getPageLength(pageIndex: Int, zoom: Float): Float {
        val size = getPageSize(pageIndex)
        return (if (isVertical) size.height else size.width) * zoom
    }

    fun getPageSpacing(pageIndex: Int, zoom: Float): Float {
        val spacing = (if (autoSpacing) pageSpacing[pageIndex] else spacingPx.toFloat())!!
        return spacing * zoom
    }

    /** Get primary page offset, that is Y for vertical scroll and X for horizontal scroll  */
    fun getPageOffset(pageIndex: Int, zoom: Float): Float {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) {
            return 0f
        }
        return pageOffsets[pageIndex]!! * zoom
    }

    /** Get secondary page offset, that is X for vertical scroll and Y for horizontal scroll  */
    fun getSecondaryPageOffset(pageIndex: Int, zoom: Float): Float {
        val pageSize = getPageSize(pageIndex)
        if (isVertical) {
            val maxWidth = this.maxPageWidth
            return zoom * (maxWidth - pageSize.width) / 2 //x
        } else {
            val maxHeight = this.maxPageHeight
            return zoom * (maxHeight - pageSize.height) / 2 //y
        }
    }

    fun getPageAtOffset(offset: Float, zoom: Float): Int {
        var currentPage = 0
        for (i in 0..<this.pagesCount) {
            val off = pageOffsets[i]!! * zoom - getPageSpacing(i, zoom) / 2f
            if (off >= offset) {
                break
            }
            currentPage++
        }
        return if (--currentPage >= 0) currentPage else 0
    }

    @Throws(PageRenderingException::class)
    fun openPage(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) {
            return false
        }

        synchronized(lock) {
            if (openedPages.indexOfKey(docPage) < 0) {
                try {
                    pdfiumCore!!.openPage(pdfDocument!!, docPage)
                    openedPages.put(docPage, true)
                    return true
                } catch (e: Exception) {
                    openedPages.put(docPage, false)
                    throw PageRenderingException(pageIndex, e)
                }
            }
            return false
        }
    }

    fun pageHasError(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        return !openedPages.get(docPage, false)
    }

    fun renderPageBitmap(
        bitmap: Bitmap?,
        pageIndex: Int,
        bounds: Rect,
        annotationRendering: Boolean
    ) {
        val docPage = documentPage(pageIndex)
        pdfiumCore!!.renderPageBitmap(
            pdfDocument!!, bitmap, docPage,
            bounds.left, bounds.top, bounds.width(), bounds.height(), annotationRendering,
        )
    }

    val metaData: PdfDocument.Meta?
        get() {
            if (pdfDocument == null) {
                return null
            }
            return pdfDocument!!.getDocumentMeta()
        }

    val bookmarks: List<PdfDocument.Bookmark>
        get() {
            if (pdfDocument == null) {
                return ArrayList()
            }
            return pdfDocument!!.getTableOfContents()
        }

    fun getPageLinks(pageIndex: Int): List<PdfDocument.Link> {
        val docPage = documentPage(pageIndex)
        return pdfiumCore!!.getPageLinks(pdfDocument!!, docPage)
    }

    fun mapRectToDevice(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rect: RectF
    ): RectF {
        val docPage = documentPage(pageIndex)
        return RectF(
            pdfDocument!!.openPage(docPage).mapRectToDevice(startX, startY, sizeX, sizeY, 0, rect)
        )
    }

    fun dispose() {
        pdfDocument.close()
    }

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage A page number.
     * @return A restricted valid page number (example : -2 => 0)
     */
    fun determineValidPageNumberFrom(userPage: Int): Int {
        if (userPage <= 0) {
            return 0
        }
        if (originalUserPages != null) {
            if (userPage >= originalUserPages!!.size) {
                return originalUserPages!!.size - 1
            }
        } else {
            if (userPage >= this.pagesCount) {
                return this.pagesCount - 1
            }
        }
        return userPage
    }

    fun documentPage(userPage: Int): Int {
        var documentPage = userPage
        if (originalUserPages != null) {
            if (userPage < 0 || userPage >= originalUserPages!!.size) {
                return -1
            } else {
                documentPage = originalUserPages!![userPage]
            }
        }

        if (documentPage < 0 || userPage >= this.pagesCount) {
            return -1
        }

        return documentPage
    }

    companion object {
        private val lock = Any()
    }
}
