package com.github.carver.dexopt

import java.lang.StringBuilder
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

object ReflectUtil {

    private val sClassCache = HashMap<String, Class<*>>()
    private val sMethodCache = HashMap<String, Method>()
    private val sConstructorCache = HashMap<String, Constructor<*>>()
    private val sFiledCache = HashMap<String, Field>()

    fun findClass(className: String): Class<*> {
        synchronized(sClassCache) {
            val clazz = sClassCache[className]
            if (clazz != null) {
                return clazz
            }
        }
        val clazz = Class.forName(className)
        synchronized(sClassCache) {
            sClassCache[className] = clazz
        }
        return clazz
    }

    fun newInstance(
        clazz: Class<*>,
        args: Array<Any?>? = null,
        parameterTypes: Array<Class<*>?>? = null
    ): Any {
        val constructor = findConstructor(clazz, parameterTypes)
        return if (args == null) {
            constructor.newInstance()
        } else {
            constructor.newInstance(*args)
        }
    }

    fun callMethod(instance: Any, methodName: String, args: Array<Any?>? = null, paramTypes: Array<Class<*>?>? = null): Any? {
        val invokeArgs = args?: emptyArray()
        return findMethod(instance::class.java, methodName, paramTypes).invoke(instance, *invokeArgs)
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, args: Array<Any?>? = null, paramTypes: Array<Class<*>?>? = null): Any? {
        val invokeArgs = args?: emptyArray()
        return findMethod(clazz, methodName, paramTypes).invoke(clazz, *invokeArgs)
    }

    fun getInterfaces(clazz: Class<*>): Array<Class<*>> {
        val interfaceList = ArrayList<Class<*>>()
        var tempClass: Class<*>? = clazz
        while (tempClass != null) {
            interfaceList.addAll(tempClass.interfaces)
            tempClass = tempClass.superclass
        }
        return interfaceList.toTypedArray()
    }

    private fun findConstructor(clazz: Class<*>, parameterTypes: Array<Class<*>?>?): Constructor<*> {
        val key = getMethodKey(clazz, clazz.simpleName, parameterTypes)
        synchronized(sConstructorCache) {
            val constructor = sConstructorCache[key]
            if (constructor != null) {
                return constructor
            }
        }
        val constructor = if (parameterTypes == null) {
            clazz.getDeclaredConstructor()
        } else {
            clazz.getDeclaredConstructor(*parameterTypes)
        }
        synchronized(sConstructorCache) {
            sConstructorCache[key] = constructor
        }
        return constructor
    }

    private fun findMethod(clazz: Class<*>, name: String, parameterTypes: Array<Class<*>?>? = null) : Method {
        val methodKey = getMethodKey(clazz, name, parameterTypes)
        synchronized(sMethodCache) {
            val method = sMethodCache[methodKey]
            if (method != null) {
                method.isAccessible = true
                return method
            }
        }
        val method = findMatchingMethod(clazz, name, parameterTypes)
        method?.let {
            it.isAccessible = true
            synchronized(sMethodCache) {
                sMethodCache[methodKey] = it
            }
            return it
        }
        throw NoSuchMethodException("no such method:clazz=${clazz.name}, name=${name}")
    }

    private fun findMatchingMethod(clazz: Class<*>?,
                                   name: String,
                                   parameterTypes: Array<Class<*>?>? = null): Method? {
        if (clazz == null) {
            return null
        }
        var method: Method? = null
        try {
            method = if (parameterTypes == null) {
                clazz.getDeclaredMethod(name)
            } else {
                clazz.getDeclaredMethod(name, *parameterTypes)
            }
        } catch (ignored: NoSuchMethodException) {
            // do nothing.
        }
        if (method == null) {
            return findMatchingMethod(clazz.superclass, name, parameterTypes)
        }
        return method
    }

    private fun getMethodKey(clazz: Class<*>, name: String, paramTypes: Array<Class<*>?>? = null) : String {
        val keyBuilder = StringBuilder()
        keyBuilder.append(clazz.name).append("-")
            .append(name).append("-")
        paramTypes?.forEach {
            if (it != null) {
                keyBuilder.append(it.name).append("-")
            }
        }
        return keyBuilder.toString()
    }
}