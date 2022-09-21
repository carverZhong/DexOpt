package com.github.carver.dexopt

import java.io.Closeable
import java.io.File
import java.io.IOException

internal object Util {

    fun Closeable?.closeQuietly() {
        if (this == null) {
            return
        }
        try {
            this.close()
        } catch (e: IOException) {
            // ignore
        }
    }

    /**
     * 删除文件。如果该文件是目录，会删除目录下所有文件。
     * @param file 要删除的文件。
     */
    fun deleteFile(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files == null || files.isEmpty()) {
                return
            }
            for (value in files) {
                deleteFile(value)
            }
        }
        file.delete()
    }

}