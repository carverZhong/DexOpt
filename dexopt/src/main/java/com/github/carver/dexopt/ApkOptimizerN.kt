package com.github.carver.dexopt

import android.content.Context
import android.os.Build
import android.util.Log
import com.github.carver.dexopt.Util.closeQuietly
import java.io.File

internal class ApkOptimizerN(private val context: Context): BaseApkOptimizer() {

    override fun dexOpt(dexFile: File): OptResult {
        val oatFile = getOatFile(dexFile)
        if (checkOatFileExists(oatFile)) {
            Log.i(TAG, "dexOpt: oatFile exists.")
            return OptResult(true, "already exists oatFile", oatFile.absolutePath)
        }

        val commandAndParams: MutableList<String> = ArrayList()
        commandAndParams.add("dex2oat")
        commandAndParams.add("--runtime-arg")
        commandAndParams.add("-classpath")
        commandAndParams.add("--runtime-arg")
        commandAndParams.add("&")
        commandAndParams.add("--dex-file=${dexFile.absolutePath}")
        commandAndParams.add("--oat-file=${oatFile.absolutePath}")
        val targetISA = getCurrentInstructionSet()
        commandAndParams.add("--instruction-set=$targetISA")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            commandAndParams.add("--compiler-filter=quicken")
        } else {
            commandAndParams.add("--compiler-filter=interpret-only")
        }

        var result: OptResult
        val pb = ProcessBuilder(commandAndParams)
        pb.redirectErrorStream(true)
        val dex2oatProcess = pb.start()
        try {
            val ret = dex2oatProcess.waitFor()
            if (ret != 0) {
                Log.e(TAG, "dexOpt: failed, ret code: $ret")
                result = OptResult(false, "dex2oatProcess failed, ret=$ret", null)
            } else {
                result = OptResult(true, null, oatFile.absolutePath)
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "dexOpt: waitFor exception.", e)
            result = OptResult(false, "dex2oatProcess failed, exception=${e.message}", null)
        } finally {
            dex2oatProcess.errorStream.closeQuietly()
            dex2oatProcess.inputStream.closeQuietly()
        }
        return result
    }

    companion object {
        private const val TAG = "ApkOptimizerO"
    }

}