package com.github.barteksc.pdfviewer.exception

open class PageRenderingException(@JvmField val page: Int, cause: Throwable?) : Exception(cause)
