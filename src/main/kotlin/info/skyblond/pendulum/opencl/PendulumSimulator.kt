package info.skyblond.pendulum.opencl

import info.skyblond.pendulum.utils.ResourceUtil.readResourceFileContent
import org.jocl.*
import org.slf4j.LoggerFactory

class PendulumSimulator(
    private val pendulumCount: Int,
    localWorkerNum: Int,
    deltaT: Float = 0.003f,
    g: Float = 9.80665f,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(PendulumSimulator::class.java)

    private val pendulumTheta1 = FloatArray(pendulumCount)
    private val pendulumTheta1Pointer = Pointer.to(pendulumTheta1)
    private val pendulumTheta1Buffer: cl_mem
    private val pendulumTheta2 = FloatArray(pendulumCount)
    private val pendulumTheta2Pointer = Pointer.to(pendulumTheta2)
    private val pendulumTheta2Buffer: cl_mem

    private val pendulumOmega1 = FloatArray(pendulumCount)
    private val pendulumOmega1Pointer = Pointer.to(pendulumOmega1)
    private val pendulumOmega1Buffer: cl_mem
    private val pendulumOmega2 = FloatArray(pendulumCount)
    private val pendulumOmega2Pointer = Pointer.to(pendulumOmega2)
    private val pendulumOmega2Buffer: cl_mem

    private val pendulumLength1 = FloatArray(pendulumCount)
    private val pendulumLength1Pointer = Pointer.to(pendulumLength1)
    private val pendulumLength1Buffer: cl_mem
    private val pendulumLength2 = FloatArray(pendulumCount)
    private val pendulumLength2Pointer = Pointer.to(pendulumLength2)
    private val pendulumLength2Buffer: cl_mem

    private val pendulumMass1 = FloatArray(pendulumCount)
    private val pendulumMass1Pointer = Pointer.to(pendulumMass1)
    private val pendulumMass1Buffer: cl_mem
    private val pendulumMass2 = FloatArray(pendulumCount)
    private val pendulumMass2Pointer = Pointer.to(pendulumMass2)
    private val pendulumMass2Buffer: cl_mem

    // choose best device by core count
    private val clDeviceInfo = getBestOpenCLDevice()

    // global error code ret buffer
    private val errCodeRet = IntArray(1)

    // global opencl parameter
    private val context: cl_context
    private val commandQueue: cl_command_queue
    private val program: cl_program
    private val kernel: cl_kernel

    init {
        logger.info("Using device: ${clDeviceInfo.deviceName} by ${clDeviceInfo.deviceVendor}")

        val contextProperties = cl_context_properties()
        contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM.toLong(), clDeviceInfo.platformId)

        context = CL.clCreateContext(
            contextProperties, 1, arrayOf(clDeviceInfo.deviceId),
            { errInfo, _, _, _ ->
                logger.error("[LWJGL] cl_context_callback\n\tInfo: $errInfo")
            }, null, errCodeRet
        )
        checkCLError(errCodeRet)

        // create command queue
        val commandQueueProperties = cl_queue_properties()
        commandQueue = CL.clCreateCommandQueueWithProperties(
            context, clDeviceInfo.deviceId,
            commandQueueProperties, errCodeRet
        )
        checkCLError(errCodeRet)

        // create cl_mem, GPU memory reference
        pendulumTheta1Buffer = createClMem()
        pendulumTheta2Buffer = createClMem()
        pendulumOmega1Buffer = createClMem()
        pendulumOmega2Buffer = createClMem()
        pendulumLength1Buffer = createClMem()
        pendulumLength2Buffer = createClMem()
        pendulumMass1Buffer = createClMem()
        pendulumMass2Buffer = createClMem()

        // compile program
        // set lengths to null means all string a terminated by \0
        program =
            CL.clCreateProgramWithSource(
                context,
                1,
                arrayOf(
                    readResourceFileContent("/kernel/pendulum.cl")
                        .replace("\$G_CONSTANT\$", String.format("%.6f", g))
                        .replace("\$DT_CONSTANT\$", String.format("%.6f", deltaT))
                ),
                null,
                errCodeRet
            )
        checkCLError(errCodeRet)
        checkCLError(CL.clBuildProgram(program, 1, arrayOf(clDeviceInfo.deviceId), "", null, null))

        // build kernel
        kernel = CL.clCreateKernel(program, "pendulum", errCodeRet)
        checkCLError(errCodeRet)

        // Set the arguments of the kernel
        checkCLError(CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem.toLong(), Pointer.to(pendulumLength1Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 1, Sizeof.cl_mem.toLong(), Pointer.to(pendulumLength2Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 2, Sizeof.cl_mem.toLong(), Pointer.to(pendulumMass1Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 3, Sizeof.cl_mem.toLong(), Pointer.to(pendulumMass2Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 4, Sizeof.cl_mem.toLong(), Pointer.to(pendulumTheta1Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 5, Sizeof.cl_mem.toLong(), Pointer.to(pendulumTheta2Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 6, Sizeof.cl_mem.toLong(), Pointer.to(pendulumOmega1Buffer)))
        checkCLError(CL.clSetKernelArg(kernel, 7, Sizeof.cl_mem.toLong(), Pointer.to(pendulumOmega2Buffer)))

        // init is done
    }

    /**
     * Copy initial data into GPU memory
     * */
    fun sendInitData() {
        // theta
        clWriteBuffer(pendulumTheta1Buffer, pendulumTheta1Pointer)
        clWriteBuffer(pendulumTheta2Buffer, pendulumTheta2Pointer)
        // omega
        clWriteBuffer(pendulumOmega1Buffer, pendulumOmega1Pointer)
        clWriteBuffer(pendulumOmega2Buffer, pendulumOmega2Pointer)
        // length
        clWriteBuffer(pendulumLength1Buffer, pendulumLength1Pointer)
        clWriteBuffer(pendulumLength2Buffer, pendulumLength2Pointer)
        // mass
        clWriteBuffer(pendulumMass1Buffer, pendulumMass1Pointer)
        clWriteBuffer(pendulumMass2Buffer, pendulumMass2Pointer)
    }

    private val globalWorkerOffset = longArrayOf(0)
    private val globalWorkerCount = longArrayOf(pendulumCount.toLong())
    private val localWorkerCount = longArrayOf(localWorkerNum.toLong())

    private var oldTime = System.nanoTime()
    private var recentTimePerStep = Long.MAX_VALUE


    fun getNanosPerStep(): Long {
        return recentTimePerStep
    }

    /**
     * Execute kernel one time
     * */
    fun execute() {
        checkCLError(
            CL.clEnqueueNDRangeKernel(
                commandQueue, kernel, 1,
                globalWorkerOffset, globalWorkerCount, localWorkerCount,
                0, null, null
            )
        )
        // wait all operation is done
        CL.clFinish(commandQueue)
        // update counter
        val currentTime = System.nanoTime()
        recentTimePerStep = currentTime - oldTime
        oldTime = currentTime
    }

    /**
     * read back data from GPU memory
     * */
    fun syncData(readOmega: Boolean = false) {
        // wait computing is done
        CL.clFinish(commandQueue)

        // read theta
        clReadBuffer(pendulumTheta1Buffer, pendulumTheta1Pointer)
        clReadBuffer(pendulumTheta2Buffer, pendulumTheta2Pointer)


        if (readOmega) {
            // read omega
            clReadBuffer(pendulumOmega1Buffer, pendulumOmega1Pointer)
            clReadBuffer(pendulumOmega2Buffer, pendulumOmega2Pointer)
        }
    }

    /**
     * Clean up
     * */
    override fun close() {
        checkCLError(CL.clFlush(commandQueue))
        checkCLError(CL.clFinish(commandQueue))
        checkCLError(CL.clReleaseKernel(kernel))
        checkCLError(CL.clReleaseProgram(program))
        checkCLError(CL.clReleaseMemObject(pendulumTheta1Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumTheta2Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumOmega1Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumOmega2Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumLength1Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumLength2Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumMass1Buffer))
        checkCLError(CL.clReleaseMemObject(pendulumMass2Buffer))
        checkCLError(CL.clReleaseCommandQueue(commandQueue))
        checkCLError(CL.clReleaseContext(context))
    }

    /**
     * Return position in (theta1, theta2) format
     * */
    fun getPendulumPosition(): List<Pair<Double, Double>> {
        val theta1 = pendulumTheta1.toList().map { it.toDouble() }
        val theta2 = pendulumTheta2.toList().map { it.toDouble() }
        return theta1.zip(theta2)
    }

    fun setPendulumPosition(position: List<Pair<Float, Float>>) {
        require(position.size == pendulumCount)
        position.forEachIndexed { index, pair ->
            pendulumTheta1[index] = pair.first
            pendulumTheta2[index] = pair.second
        }
    }

    /**
     * Return angular velocity in (omega1, omega2) format
     * */
    fun getPendulumVelocity(): List<Pair<Double, Double>> {
        val omega1 = pendulumOmega1.toList().map { it.toDouble() }
        val omega2 = pendulumOmega2.toList().map { it.toDouble() }
        return omega1.zip(omega2)
    }

    fun setPendulumVelocity(velocity: List<Pair<Float, Float>>) {
        require(velocity.size == pendulumCount)
        velocity.forEachIndexed { index, pair ->
            pendulumOmega1[index] = pair.first
            pendulumOmega2[index] = pair.second
        }
    }

    fun getPendulumLength(): List<Pair<Double, Double>> {
        val length1 = pendulumLength1.toList().map { it.toDouble() }
        val length2 = pendulumLength2.toList().map { it.toDouble() }
        return length1.zip(length2)
    }

    fun setPendulumLength(length: List<Pair<Float, Float>>) {
        require(length.size == pendulumCount)
        length.forEachIndexed { index, pair ->
            pendulumLength1[index] = pair.first
            pendulumLength2[index] = pair.second
        }
    }

    fun getPendulumMass(): List<Pair<Double, Double>> {
        val mass1 = pendulumMass1.toList().map { it.toDouble() }
        val mass2 = pendulumMass2.toList().map { it.toDouble() }
        return mass1.zip(mass2)
    }

    fun setPendulumMass(mass: List<Pair<Float, Float>>) {
        require(mass.size == pendulumCount)
        mass.forEachIndexed { index, pair ->
            pendulumMass1[index] = pair.first
            pendulumMass2[index] = pair.second
        }
    }

    private fun createClMem(size: Long = pendulumCount.toLong()): cl_mem {
        val result = CL.clCreateBuffer(
            context,
            CL.CL_MEM_READ_WRITE,
            Sizeof.cl_float * size,
            null,
            errCodeRet
        )
        checkCLError(errCodeRet)
        return result
    }

    private fun clWriteBuffer(buffer: cl_mem, pointer: Pointer, size: Long = pendulumCount.toLong()) {
        checkCLError(
            CL.clEnqueueWriteBuffer(
                commandQueue,
                buffer,
                true,
                0,
                size * Sizeof.cl_float.toLong(),
                pointer,
                0, null, null
            )
        )
    }

    private fun clReadBuffer(buffer: cl_mem, pointer: Pointer, size: Long = pendulumCount.toLong()) {
        checkCLError(
            CL.clEnqueueReadBuffer(
                commandQueue,
                buffer,
                true,
                0, size * Sizeof.cl_float.toLong(),
                pointer,
                0, null, null
            )
        )
    }
}