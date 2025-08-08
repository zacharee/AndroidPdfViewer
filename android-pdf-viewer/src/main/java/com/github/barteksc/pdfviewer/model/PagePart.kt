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
package com.github.barteksc.pdfviewer.model

import android.graphics.Bitmap
import android.graphics.RectF

class PagePart(
    @JvmField val page: Int,
    @JvmField val renderedBitmap: Bitmap?,
    @JvmField val pageRelativeBounds: RectF,
    val isThumbnail: Boolean,
    @JvmField var cacheOrder: Int
) {
    override fun equals(other: Any?): Boolean {
        if (other !is PagePart) {
            return false
        }

        return other.page == page &&
                other.pageRelativeBounds.left == pageRelativeBounds.left &&
                other.pageRelativeBounds.right == pageRelativeBounds.right &&
                other.pageRelativeBounds.top == pageRelativeBounds.top &&
                other.pageRelativeBounds.bottom == pageRelativeBounds.bottom
    }

    override fun hashCode(): Int {
        var result = page
        result = 31 * result + pageRelativeBounds.hashCode()
        return result
    }
}
