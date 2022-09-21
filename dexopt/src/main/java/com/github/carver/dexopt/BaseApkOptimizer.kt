package com.github.carver.dexopt

import java.io.File

abstract class BaseApkOptimizer {

    /**
     * 执行 dex2oat
     */
    abstract fun dexOpt(dexFile: File): OptResult

    /**
     * 获取 oat 文件存放目录
     */
    fun getOatFile(dexFile: File): File {
        var apkFileName = dexFile.name
        val separatorIndex = apkFileName.lastIndexOf(".")
        if (separatorIndex > 0) {
            apkFileName = apkFileName.substring(0, separatorIndex)
        }
        val oatParentPath = dexFile.parentFile.absolutePath +
                File.separator + "oat" +
                File.separator + getCurrentInstructionSet()
        val oatParentFile = File(oatParentPath)
        if (!oatParentFile.exists()) {
            oatParentFile.mkdirs()
        }
        return File(oatParentPath, apkFileName + ODEX_SUFFIX)
    }

    protected fun getCurrentInstructionSet(): String {
        val currentInstructionSet: String = try {
            val vmRuntimeClazz = Class.forName("dalvik.system.VMRuntime")
            ReflectUtil.callStaticMethod(
                vmRuntimeClazz,
                "getCurrentInstructionSet"
            ) as String
        } catch (e: Throwable) {
            "arm64"
        }
        return currentInstructionSet
    }

    /**
     * 检查 oat 文件是否生成
     * @return true：已生成，false：未生成
     */
    protected fun checkOatFileExists(oatFile: File): Boolean {
        if (oatFile.exists() && oatFile.canRead() && oatFile.length() > 0) {
            return true
        }
        // 防止出现 oatFile 损坏的情况，执行一遍删除
        Util.deleteFile(oatFile)
        return false
    }

    companion object {
        const val ODEX_SUFFIX = ".odex"

        /**
         * 用于 oat 相关 log
         */
        const val OAT_TAG = "minibox_oat"
    }
}