package info.skyblond.pendulum.opencl

import org.jocl.CL.*
import org.jocl.Pointer
import org.jocl.Sizeof
import org.jocl.cl_device_id
import org.jocl.cl_platform_id

private fun getString(device: cl_device_id, paramName: Int): String {
    // Obtain the length of the string that will be queried
    val size = LongArray(1)
    checkCLError(clGetDeviceInfo(device, paramName, 0, null, size))

    // Create a buffer of the appropriate size and fill it with the info
    val buffer = ByteArray(size[0].toInt())
    checkCLError(clGetDeviceInfo(device, paramName, buffer.size.toLong(), Pointer.to(buffer), size))

    // Create a string from the buffer (excluding the trailing \0 byte)
    return String(buffer, 0, buffer.size - 1)
}

fun getLong(device: cl_device_id, paramName: Int): Long {
    return getLongs(device, paramName, 1)[0]
}

fun getLongs(device: cl_device_id, paramName: Int, numValues: Int): LongArray {
    val values = LongArray(numValues)
    checkCLError(clGetDeviceInfo(device, paramName, (Sizeof.cl_long * numValues).toLong(), Pointer.to(values), null))
    return values
}

fun getInt(device: cl_device_id, paramName: Int): Int {
    return getInts(device, paramName, 1)[0]
}

fun getInts(device: cl_device_id, paramName: Int, numValues: Int): IntArray {
    val values = IntArray(numValues)
    checkCLError(clGetDeviceInfo(device, paramName, (Sizeof.cl_int * numValues).toLong(), Pointer.to(values), null))
    return values
}


data class CLDeviceInfo(
    val platformId: cl_platform_id,
    val deviceId: cl_device_id,
    val deviceType: Long,
    val deviceName: String,
    val computeUnitCount: Int,
    val clockFrequency: Int,
    val localMemorySize: Long,
    val globalMemorySize: Long,
    val clVersion: String,
    val deviceVendor: String
)

fun checkCLError(code: IntArray) {
    checkCLError(code[0])
}

fun checkCLError(code: Int) {
    require(code == CL_SUCCESS) { "OpenCL error code: $code" }
}

/**
 * Choose best device by compute unit count.
 * */
fun getBestOpenCLDevice(): CLDeviceInfo {
    val deviceList = mutableListOf<CLDeviceInfo>()

    val numPlatform = IntArray(1)
    // query count
    checkCLError(clGetPlatformIDs(0, null, numPlatform))
    val platforms = arrayOfNulls<cl_platform_id>(numPlatform[0])
    // query platform ids
    checkCLError(clGetPlatformIDs(platforms.size, platforms, numPlatform))

    // for each platform, query the device info
    for (platformId in platforms) {
        val numDevice = IntArray(1)
        checkCLError(clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, 0, null, numDevice))
        val devices = arrayOfNulls<cl_device_id>(numDevice[0])
        checkCLError(clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, devices.size, devices, numDevice))

        // query platform cl version
        val size = LongArray(1)
        checkCLError(clGetPlatformInfo(platformId, CL_PLATFORM_VERSION, 0, null, size))
        val buffer = ByteArray(size[0].toInt())
        checkCLError(clGetPlatformInfo(platformId, CL_PLATFORM_VERSION, buffer.size.toLong(), Pointer.to(buffer), size))
        val clVersion = String(buffer, 0, buffer.size - 1)

        // for each device, query the info
        for (deviceId in devices) {
            deviceList.add(
                CLDeviceInfo(
                    platformId = platformId!!,
                    deviceId = deviceId!!,
                    deviceType = getLong(deviceId, CL_DEVICE_TYPE),
                    deviceName = getString(deviceId, CL_DEVICE_NAME),
                    deviceVendor = getString(deviceId, CL_DEVICE_VENDOR),
                    computeUnitCount = getInt(deviceId, CL_DEVICE_MAX_COMPUTE_UNITS),
                    clockFrequency = getInt(deviceId, CL_DEVICE_MAX_CLOCK_FREQUENCY),
                    localMemorySize = getLong(deviceId, CL_DEVICE_LOCAL_MEM_SIZE),
                    globalMemorySize = getLong(deviceId, CL_DEVICE_GLOBAL_MEM_SIZE),
                    clVersion = clVersion
                )
            )
        }
    }

    require(deviceList.isNotEmpty()) { "No OpenCL device available" }

    return deviceList.maxByOrNull { it.computeUnitCount }!!
}