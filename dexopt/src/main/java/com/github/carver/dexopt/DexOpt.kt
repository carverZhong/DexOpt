package com.github.carver.dexopt

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

object DexOpt {

    private const val TAG = "DexOpt"

    /**
     * 支持Android 7以上进行dex2oat
     */
    @TargetApi(Build.VERSION_CODES.N)
    fun dexOpt(context: Context, dexFilePath: String): OptResult {
        val dexFile = File(dexFilePath)
        return dexOpt(context.applicationContext, dexFile)
    }

    @TargetApi(Build.VERSION_CODES.N)
    fun dexOpt(context: Context, dexFile: File): OptResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return OptResult(false, "only supports android 8 and above.", null)
        }
        if (dexFile.exists() && dexFile.isFile) {
            return createApkOptimizer(context).dexOpt(dexFile)
        }
        return OptResult(false, "dexFile is not exists, path=${dexFile.absolutePath}", null)
    }

    private fun createApkOptimizer(context: Context): BaseApkOptimizer {
        // Android10及以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.i(TAG, "create ApkOptimizerQ")
            return ApkOptimizerQ(context)
        }
        // Android7-Android9情况
        Log.i(TAG, "create ApkOptimizerO")
        return ApkOptimizerN(context)
    }
}