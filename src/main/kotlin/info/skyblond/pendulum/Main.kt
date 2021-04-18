package info.skyblond.pendulum

import info.skyblond.pendulum.opencl.PendulumSimulator
import javafx.application.Application.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.random.Random

const val pendulumCount = 81920
const val windowWidth = 800.0
const val windowHeight = 600.0
const val targetSPS = 200

val pendulumSimulator = PendulumSimulator(pendulumCount, 512, (1.0 / targetSPS).toFloat())
val shutdownFlag = AtomicBoolean(false)

fun main() {
    // rod length of each double pendulum
    pendulumSimulator.setPendulumLength(Array(pendulumCount) { Pair(1.0f, 1.0f) }.toList())
    // mass of each double pendulum
    pendulumSimulator.setPendulumMass(Array(pendulumCount) { Pair(2.0f, 2.0f) }.toList())
    // initial position
    pendulumSimulator.setPendulumPosition(Array(pendulumCount) {
        Pair(
            PI.toFloat() / 2 + Random.nextDouble(-0.05, 0.05).toFloat(),
            PI.toFloat() + Random.nextDouble(-0.05, 0.05).toFloat()
        )
    }.toList())
    // initial angular velocity
    pendulumSimulator.setPendulumVelocity(Array(pendulumCount) { Pair(0f, 0f) }.toList())
    // send data to GPU
    pendulumSimulator.sendInitData()

    // launch window
    Thread { launch(MainWindow::class.java) }.start()

    // translate second into ms second
    require(targetSPS <= 1000) { "Max time resolution exceeds" }
    val targetStepTime = (1000.0 / targetSPS).toLong()
    while (!shutdownFlag.get()) {
        val start = System.currentTimeMillis()
        pendulumSimulator.execute()
        while (System.currentTimeMillis() - start < targetStepTime) {
            Thread.sleep(1)
        }
    }

    pendulumSimulator.close()
}

