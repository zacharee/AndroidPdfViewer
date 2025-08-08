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
package com.github.barteksc.pdfviewer.util

object Constants {
    @JvmField
    var DEBUG_MODE: Boolean = false

    /** Between 0 and 1, the thumbnails quality (default 0.3). Increasing this value may cause performance decrease  */
    @JvmField
    var THUMBNAIL_RATIO: Float = 0.3f

    /**
     * The size of the rendered parts (default 256)
     * Tinier : a little bit slower to have the whole page rendered but more reactive.
     * Bigger : user will have to wait longer to have the first visual results
     */
    @JvmField
    var PART_SIZE: Float = 256f

    /** Part of document above and below screen that should be preloaded, in dp  */
    @JvmField
    var PRELOAD_OFFSET: Int = 20

    object Cache {
        /** The size of the cache (number of bitmaps kept)  */
        @JvmField
        var CACHE_SIZE: Int = 120

        @JvmField
        var THUMBNAILS_CACHE_SIZE: Int = 8
    }

    object Pinch {
        @JvmField
        var MAXIMUM_ZOOM: Float = 10f

        @JvmField
        var MINIMUM_ZOOM: Float = 1f
    }
}
