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
package com.github.barteksc.sample

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import io.legere.pdfiumandroid.PdfDocument

class PDFViewActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener, OnPageErrorListener {
    private lateinit var pdfView: PDFView

    private var uri: Uri? = null

    private var pageNumber = 0

    private var pdfFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.pickFile) {
            pickFile()

            return true
        }

        return false
    }

    private fun initViews() {
        pdfView = findViewById(R.id.pdfView)

        afterViews()
    }

    fun pickFile() {
        val permissionCheck = ContextCompat.checkSelfPermission(
            this,
            READ_EXTERNAL_STORAGE
        )

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(READ_EXTERNAL_STORAGE),
                PERMISSION_CODE
            )

            return
        }

        launchPicker()
    }

    fun launchPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType("application/pdf")
        try {
            startActivityForResult(intent, REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            //alert user that file manager not working
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun afterViews() {
        pdfView.setBackgroundColor(Color.LTGRAY)
        if (uri != null) {
            displayFromUri(uri!!)
        } else {
            displayFromAsset(SAMPLE_FILE)
        }
        setTitle(pdfFileName)
    }

    private fun displayFromAsset(assetFileName: String) {
        var isLandscape: Boolean
        val orientation = resources.configuration.orientation
        isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        pdfFileName = assetFileName

        pdfView.fromAsset(assetFileName)
            .defaultPage(pageNumber)
            .onPageChange(this)
            .enableAnnotationRendering(true)
            .onLoad(this)
            .landscapeOrientation(isLandscape)
            .dualPageMode(false)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(0) // in dp
            .enableSwipe(true)
            .swipeHorizontal(true)
            .pageFling(true)
            .fitEachPage(false)
            .onPageError(this)
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
    }

    private fun displayFromUri(uri: Uri) {
        pdfFileName = getFileName(uri)

        pdfView.fromUri(uri)
            .defaultPage(pageNumber)
            .onPageChange(this)
            .enableAnnotationRendering(true)
            .onLoad(this)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(0) // in dp
            .dualPageMode(true)
            .enableSwipe(true)
            .swipeHorizontal(true)
            .pageFling(true)
            .onPageError(this)
            .load()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        onResult(resultCode, data!!)
    }

    fun onResult(resultCode: Int, intent: Intent) {
        if (resultCode == RESULT_OK) {
            uri = intent.data
            displayFromUri(uri!!)
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        pageNumber = page
        setTitle(String.format("%s %s / %s", pdfFileName, page + 1, pageCount))
    }

    fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result!!
    }

    override fun loadComplete(nbPages: Int) {
        val meta = pdfView.documentMeta!!
        Log.e(TAG, "title = " + meta.title)
        Log.e(TAG, "author = " + meta.author)
        Log.e(TAG, "subject = " + meta.subject)
        Log.e(TAG, "keywords = " + meta.keywords)
        Log.e(TAG, "creator = " + meta.creator)
        Log.e(TAG, "producer = " + meta.producer)
        Log.e(TAG, "creationDate = " + meta.creationDate)
        Log.e(TAG, "modDate = " + meta.modDate)

        printBookmarksTree(pdfView.tableOfContents, "-")
    }

    fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
        for (b in tree) {
            Log.e(TAG, String.format("%s %s, p %d", sep, b.title, b.pageIdx))

            if (!b.children.isEmpty()) {
                printBookmarksTree(b.children, "$sep-")
            }
        }
    }

    /**
     * Listener for response to user permission request
     *
     * @param requestCode  Check that permission request code matches
     * @param permissions  Permissions that requested
     * @param grantResults Whether permissions granted
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPicker()
            }
        }
    }

    override fun onPageError(page: Int, t: Throwable?) {
        Log.e(TAG, "Cannot load page $page")
    }

    companion object {
        private val TAG: String = PDFViewActivity::class.java.getSimpleName()

        private const val REQUEST_CODE = 42
        const val PERMISSION_CODE: Int = 42042

        const val SAMPLE_FILE: String = "sample.pdf"
        const val READ_EXTERNAL_STORAGE: String = "android.permission.READ_EXTERNAL_STORAGE"
    }
}
