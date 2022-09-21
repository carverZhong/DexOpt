package com.github.carver.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.carver.dexopt.DexOpt
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnLocalDir: Button
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnLocalDir = findViewById(R.id.btn_local_dir)
        btnLocalDir.setOnClickListener {
            selectLocalDirApk()
        }
        tvResult = findViewById(R.id.tv_result)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        Log.i(TAG, "onActivityResult: requestCode=$requestCode, uri=${uri}")
        if (requestCode == 100 && uri != null) {
            Thread {
                val apkFilePath = readApkFile(uri)
                if (apkFilePath != null) {
                    startDexOpt(apkFilePath)
                } else {
                    Log.i(TAG, "onActivityResult: apkFilePath get failed.")
                }
            }.start()
        }
    }

    private fun readApkFile(uri: Uri): String? {
        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri)
            val rootPath = filesDir
            val path = uri.path
            val index = path!!.lastIndexOf("/")
            val fileName = path.substring(index)
            val targetFile = File(rootPath, fileName)
            if (copyFile(inputStream, targetFile)) {
                return targetFile.absolutePath
            }
            return targetFile.absolutePath
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    private fun copyFile(inputStream: InputStream?, target: File?): Boolean {
        if (inputStream == null) {
            return false
        }
        var result: Boolean
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(target)
            val data = ByteArray(4096)
            var len: Int
            while (inputStream.read(data).also { len = it } != -1) {
                outputStream.write(data, 0, len)
            }
            outputStream.flush()
            result = true
        } catch (e: Throwable) {
            result = false
        } finally {
            inputStream.close()
            outputStream?.close()
        }
        return result
    }

    /**
     * 从本地文件选取 APK 文件
     */
    private fun selectLocalDirApk() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, 100)
    }

    private fun startDexOpt(apkFilePath: String) {
        Thread {
            val optResult = DexOpt.dexOpt(this@MainActivity, apkFilePath)
            if (optResult.isSuccess) {
                showResultText("成功，oat文件位于此路径：" + optResult.oatFilePath)
            } else {
                showResultText("失败：" + optResult.errorMsg)
            }
        }.start()
    }

    private fun showResultText(resultText: String) {
        runOnUiThread {
            tvResult.visibility = View.VISIBLE
            tvResult.text = resultText
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}