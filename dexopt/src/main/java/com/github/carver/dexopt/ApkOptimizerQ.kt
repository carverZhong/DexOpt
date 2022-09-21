package com.github.carver.dexopt

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import java.io.File
import java.io.FileDescriptor
import java.lang.RuntimeException
import java.lang.reflect.Proxy

/**
 * Android Q 及以上 dex2oat 优化。
 */
internal class ApkOptimizerQ(private val context: Context): BaseApkOptimizer() {

    private var cachePMBinder: IBinder? = null
    private var cacheCustomPM: PackageManager? = null
    private val resultReceiver = ResultReceiver(Handler(Looper.getMainLooper()))

    override fun dexOpt(dexFile: File): OptResult{
        val oatFile = getOatFile(dexFile)
        if (checkOatFileExists(oatFile)) {
            Log.i(TAG, "dexOpt: oatFile exists.")
            return OptResult(true, "already exists oatFile", oatFile.absolutePath)
        }
        if (!registerDexModule(dexFile.absolutePath)) {
            return OptResult(false, "registerDexModule failed", null)
        }
        performDexOpt()
        reconcileSecondaryDexFiles()
        return OptResult(true, null, oatFile.absolutePath)
    }

    private fun registerDexModule(apkFilePath: String): Boolean {
        try {
            val callbackClazz = ReflectUtil.findClass("android.content.pm.PackageManager\$DexModuleRegisterCallback")
            ReflectUtil.callMethod(
                getCustomPM(),
                "registerDexModule",
                arrayOf(apkFilePath, null),
                arrayOf(String::class.java, callbackClazz)
            )
            return true
        } catch (thr: Throwable) {
            Log.e(TAG, "registerDexModule: thr.", thr)
        }
        return false
    }

    private fun performDexOpt() {
        val args = arrayOf(
            "compile", "-f", "--secondary-dex", "-m",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "verify" else "speed-profile",
            context.packageName
        )
        executeShellCommand(args)
    }

    private fun reconcileSecondaryDexFiles() {
        val args = arrayOf("reconcile-secondary-dex-files", context.packageName)
        executeShellCommand(args)
    }

    private fun executeShellCommand(args: Array<String>) {
        val lastIdentity = Binder.clearCallingIdentity()
        var data: Parcel? = null
        var reply: Parcel? = null
        try {
            data = Parcel.obtain()
            reply = Parcel.obtain()
            data.writeFileDescriptor(FileDescriptor.`in`)
            data.writeFileDescriptor(FileDescriptor.out)
            data.writeFileDescriptor(FileDescriptor.err)
            data.writeStringArray(args)
            data.writeStrongBinder(null)
            resultReceiver.writeToParcel(data, 0)
            getPMBinder().transact(SHELL_COMMAND_TRANSACTION, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.e(TAG, "executeShellCommand error.", t)
        } finally {
            data?.recycle()
            reply?.recycle()
        }
        Binder.restoreCallingIdentity(lastIdentity)
    }

    private fun getPMBinder(): IBinder {
        val iBinder = cachePMBinder
        if (iBinder != null && iBinder.isBinderAlive) {
            return iBinder
        }
        val serviceManagerClass = ReflectUtil.findClass("android.os.ServiceManager")
        val service = ReflectUtil.callStaticMethod(serviceManagerClass,
            "getService",
            arrayOf("package"),
            arrayOf(String::class.java)
        )
        if (service != null) {
            cachePMBinder = service as IBinder
            return service
        }
        throw RuntimeException("getPMBinder failed.")
    }

    /**
     * 创建一个自定义的 PackageManager，避免影响正常的 PackageManager
     */
    private fun getCustomPM(): PackageManager {
        val customPM = cacheCustomPM
        if (customPM != null && cachePMBinder?.isBinderAlive == true) {
            return customPM
        }
        val pmBinder = getPMBinder()
        val pmBinderDynamicProxy = Proxy.newProxyInstance(
            context.classLoader, ReflectUtil.getInterfaces(pmBinder::class.java)
        ) { _, method, args ->
            if ("transact" == method.name) {
                // FLAG_ONEWAY => NONE.
                args[3] = 0
            }
            method.invoke(pmBinder, *args)
        }

        val pmStubClass = ReflectUtil.findClass("android.content.pm.IPackageManager\$Stub")
        val pmStubProxy = ReflectUtil.callStaticMethod(pmStubClass,
            "asInterface",
            arrayOf(pmBinderDynamicProxy),
            arrayOf(IBinder::class.java))
        val contextImpl = if (context is ContextWrapper) context.baseContext else context
        val appPM = createAppPM(contextImpl, pmStubProxy!!)
        cacheCustomPM = appPM
        return appPM
    }

    private fun createAppPM(context: Context, pm: Any): PackageManager {
        val clazz = ReflectUtil.findClass("android.app.ApplicationPackageManager")
        val pmClazz = ReflectUtil.findClass("android.content.pm.IPackageManager")
        return ReflectUtil.newInstance(clazz, arrayOf(context, pm),
            arrayOf(context::class.java, pmClazz)) as PackageManager
    }

    companion object {
        private const val TAG = "ApkOptimizerQ"

        /**
         * @see IBinder.SHELL_COMMAND_TRANSACTION
         */
        private const val SHELL_COMMAND_TRANSACTION =
            '_'.toInt() shl 24 or ('C'.toInt() shl 16) or ('M'.code shl 8) or 'D'.code
    }
}