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

object ArrayUtils {
    /** Transforms (0,1,2,2,3) to (0,1,2,3)  */
    fun deleteDuplicatedPages(pages: IntArray): IntArray {
        val result: MutableList<Int?> = ArrayList()
        var lastInt = -1
        for (currentInt in pages) {
            if (lastInt != currentInt) {
                result.add(currentInt)
            }
            lastInt = currentInt
        }
        val arrayResult = IntArray(result.size)
        for (i in result.indices) {
            arrayResult[i] = result[i]!!
        }
        return arrayResult
    }

    /** Transforms (0, 4, 4, 6, 6, 6, 3) into (0, 1, 1, 2, 2, 2, 3)  */
    fun calculateIndexesInDuplicateArray(originalUserPages: IntArray): IntArray {
        val result = IntArray(originalUserPages.size)
        if (originalUserPages.isEmpty()) {
            return result
        }

        var index = 0
        result[0] = 0
        for (i in 1..<originalUserPages.size) {
            if (originalUserPages[i] != originalUserPages[i - 1]) {
                index++
            }
            result[i] = index
        }

        return result
    }

    fun arrayToString(array: IntArray): String {
        val builder = StringBuilder("[")
        for (i in array.indices) {
            builder.append(array[i])
            if (i != array.size - 1) {
                builder.append(",")
            }
        }
        builder.append("]")
        return builder.toString()
    }
}
