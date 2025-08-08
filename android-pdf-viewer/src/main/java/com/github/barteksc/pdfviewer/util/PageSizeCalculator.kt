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
package com.github.barteksc.pdfviewer.util

import android.util.SizeF
import io.legere.pdfiumandroid.util.Size
import kotlin.math.floor

class PageSizeCalculator(
    private val fitPolicy: FitPolicy,
    private val originalMaxWidthPageSize: Size,
    private val originalMaxHeightPageSize: Size,
    private val viewSize: Size,
    private val fitEachPage: Boolean,
) {
    var optimalMaxWidthPageSize: SizeF = SizeF(0f, 0f)
        private set
    var optimalMaxHeightPageSize: SizeF = SizeF(0f, 0f)
        private set
    private var widthRatio = 0f
    private var heightRatio = 0f

    init {
        calculateMaxPages()
    }

    fun calculate(pageSize: Size, showTwoPages: Boolean, isLandscape: Boolean): SizeF {
        if (pageSize.width <= 0 || pageSize.height <= 0) {
            return SizeF(0f, 0f)
        }
        val maxWidth = if (showTwoPages && !isLandscape) {
            if (fitEachPage) viewSize.width.toFloat() else pageSize.width / 2 * widthRatio
        } else {
            if (fitEachPage) viewSize.width.toFloat() else pageSize.width * widthRatio
        }
        val maxHeight =
            if (fitEachPage) viewSize.height.toFloat() else pageSize.height * heightRatio
        return when (fitPolicy) {
            FitPolicy.HEIGHT -> fitHeight(pageSize, maxHeight)
            FitPolicy.BOTH -> fitBoth(pageSize, maxWidth, maxHeight)
            else -> fitWidth(pageSize, maxWidth)
        }
    }

    private fun calculateMaxPages() {
        when (fitPolicy) {
            FitPolicy.HEIGHT -> {
                optimalMaxHeightPageSize =
                    fitHeight(originalMaxHeightPageSize, viewSize.height.toFloat())
                heightRatio =
                    optimalMaxHeightPageSize.height / originalMaxHeightPageSize.height
                optimalMaxWidthPageSize = fitHeight(
                    originalMaxWidthPageSize,
                    originalMaxWidthPageSize.height * heightRatio,
                )
            }

            FitPolicy.BOTH -> {
                val localOptimalMaxWidth = fitBoth(
                    originalMaxWidthPageSize,
                    viewSize.width.toFloat(),
                    viewSize.height.toFloat(),
                )
                val localWidthRatio =
                    localOptimalMaxWidth.width / originalMaxWidthPageSize.width
                this.optimalMaxHeightPageSize = fitBoth(
                    originalMaxHeightPageSize, originalMaxHeightPageSize.width * localWidthRatio,
                    viewSize.height.toFloat(),
                )
                heightRatio =
                    optimalMaxHeightPageSize.height / originalMaxHeightPageSize.height
                optimalMaxWidthPageSize = fitBoth(
                    originalMaxWidthPageSize,
                    viewSize.width.toFloat(),
                    originalMaxWidthPageSize.height * heightRatio,
                )
                widthRatio = optimalMaxWidthPageSize.width / originalMaxWidthPageSize.width
            }

            else -> {
                optimalMaxWidthPageSize =
                    fitWidth(originalMaxWidthPageSize, viewSize.width.toFloat())
                widthRatio = optimalMaxWidthPageSize.width / originalMaxWidthPageSize.width
                optimalMaxHeightPageSize = fitWidth(
                    originalMaxHeightPageSize,
                    originalMaxHeightPageSize.width * widthRatio,
                )
            }
        }
    }

    private fun fitWidth(pageSize: Size, maxWidth: Float): SizeF {
        var w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
        val ratio = w / h
        w = maxWidth
        h = floor((maxWidth / ratio).toDouble()).toFloat()
        return SizeF(w, h)
    }

    private fun fitHeight(pageSize: Size, maxHeight: Float): SizeF {
        var w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
        val ratio = h / w
        h = maxHeight
        w = floor((maxHeight / ratio).toDouble()).toFloat()
        return SizeF(w, h)
    }

    private fun fitBoth(pageSize: Size, maxWidth: Float, maxHeight: Float): SizeF {
        var w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
        val ratio = w / h
        w = maxWidth
        h = floor((maxWidth / ratio).toDouble()).toFloat()
        if (h > maxHeight) {
            h = maxHeight
            w = floor((maxHeight * ratio).toDouble()).toFloat()
        }
        return SizeF(w, h)
    }
}
