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

import android.graphics.RectF
import com.github.barteksc.pdfviewer.model.PagePart
import com.github.barteksc.pdfviewer.util.Constants.Cache.CACHE_SIZE
import com.github.barteksc.pdfviewer.util.Constants.Cache.THUMBNAILS_CACHE_SIZE
import java.util.PriorityQueue

internal class CacheManager {
    private val passiveCache: PriorityQueue<PagePart>

    private val activeCache: PriorityQueue<PagePart>

    private val thumbnails: MutableList<PagePart>

    private val passiveActiveLock = Any()

    private val orderComparator = PagePartComparator()

    init {
        activeCache = PriorityQueue<PagePart>(CACHE_SIZE, orderComparator)
        passiveCache = PriorityQueue<PagePart>(CACHE_SIZE, orderComparator)
        thumbnails = ArrayList<PagePart>()
    }

    fun cachePart(part: PagePart?) {
        synchronized(passiveActiveLock) {
            // If cache too big, remove and recycle
            makeAFreeSpace()

            // Then add part
            activeCache.offer(part)
        }
    }

    fun makeANewSet() {
        synchronized(passiveActiveLock) {
            passiveCache.addAll(activeCache)
            activeCache.clear()
        }
    }

    private fun makeAFreeSpace() {
        synchronized(passiveActiveLock) {
            while ((activeCache.size + passiveCache.size) >= CACHE_SIZE && !passiveCache.isEmpty()) {
                val part = passiveCache.poll()
                part?.renderedBitmap?.recycle()
            }
            while ((activeCache.size + passiveCache.size) >= CACHE_SIZE && !activeCache.isEmpty()) {
                activeCache.poll()?.renderedBitmap?.recycle()
            }
        }
    }

    fun cacheThumbnail(part: PagePart) {
        synchronized(thumbnails) {
            // If cache too big, remove and recycle
            while (thumbnails.size >= THUMBNAILS_CACHE_SIZE) {
                thumbnails.removeAt(0).renderedBitmap?.recycle()
            }

            // Then add thumbnail
            addWithoutDuplicates(thumbnails, part)
        }
    }

    fun upPartIfContained(page: Int, pageRelativeBounds: RectF, toOrder: Int): Boolean {
        val fakePart = PagePart(page, null, pageRelativeBounds, false, 0)

        synchronized(passiveActiveLock) {
            find(passiveCache, fakePart)?.let { found ->
                passiveCache.remove(found)
                found.cacheOrder = toOrder
                activeCache.offer(found)
                return true
            }

            return find(activeCache, fakePart) != null
        }
    }

    /**
     * Return true if already contains the described PagePart
     */
    fun containsThumbnail(page: Int, pageRelativeBounds: RectF): Boolean {
        val fakePart = PagePart(page, null, pageRelativeBounds, true, 0)
        synchronized(thumbnails) {
            for (part in thumbnails) {
                if (part == fakePart) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Add part if it doesn't exist, recycle bitmap otherwise
     */
    private fun addWithoutDuplicates(collection: MutableCollection<PagePart>, newPart: PagePart) {
        for (part in collection) {
            if (part == newPart) {
                newPart.renderedBitmap?.recycle()
                return
            }
        }
        collection.add(newPart)
    }

    val pageParts: List<PagePart>
        get() {
            synchronized(passiveActiveLock) {
                val parts: MutableList<PagePart> =
                    ArrayList(passiveCache)
                parts.addAll(activeCache)
                return parts
            }
        }

    fun getThumbnails(): List<PagePart> {
        synchronized(thumbnails) {
            return thumbnails
        }
    }

    fun recycle() {
        synchronized(passiveActiveLock) {
            for (part in passiveCache) {
                part.renderedBitmap?.recycle()
            }
            passiveCache.clear()
            for (part in activeCache) {
                part.renderedBitmap?.recycle()
            }
            activeCache.clear()
        }
        synchronized(thumbnails) {
            for (part in thumbnails) {
                part.renderedBitmap?.recycle()
            }
            thumbnails.clear()
        }
    }

    internal class PagePartComparator : Comparator<PagePart> {
        override fun compare(part1: PagePart, part2: PagePart): Int {
            if (part1.cacheOrder == part2.cacheOrder) {
                return 0
            }
            return if (part1.cacheOrder > part2.cacheOrder) 1 else -1
        }
    }

    companion object {
        private fun find(vector: PriorityQueue<PagePart>, fakePart: PagePart?): PagePart? {
            for (part in vector) {
                if (part.equals(fakePart)) {
                    return part
                }
            }
            return null
        }
    }
}
