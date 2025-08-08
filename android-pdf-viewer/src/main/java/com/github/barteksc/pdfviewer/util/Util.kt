/*
 * Copyright (C) 2016 Bartosz Schiller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer.util

import android.content.Context
import android.util.TypedValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object Util {
    private const val DEFAULT_BUFFER_SIZE = 1024 * 4

    @JvmStatic
    fun getDP(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }

    @Throws(IOException::class)
    fun toByteArray(inputStream: InputStream): ByteArray {
        val os = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var n: Int
        while (-1 != (inputStream.read(buffer).also { n = it })) {
            os.write(buffer, 0, n)
        }
        return os.toByteArray()
    }
}
