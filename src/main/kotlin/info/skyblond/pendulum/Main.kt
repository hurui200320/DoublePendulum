package info.skyblond.pendulum

import info.skyblond.pendulum.opencl.PendulumSimulator
import info.skyblond.pendulum.utils.ColorList
import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Application.launch
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.input.MouseButton
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.exitProcess

private const val pendulumCount = 81920
private const val windowWidth = 800.0
private const val windowHeight = 600.0

private val pendulumSimulator = PendulumSimulator(pendulumCount, 512)
private val shutdownFlag = AtomicBoolean(false)

fun main() {
    val logger = LoggerFactory.getLogger("Application")
    Thread { launch(MainWindow::class.java) }.start()

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

    // report simulation status every 10s
    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            logger.info("Simulation step duration: ${pendulumSimulator.getNanosPerStep()}ns, SPS: ${1_000_000_000 / pendulumSimulator.getNanosPerStep()}")
        }
    }, 5_000L, 10_000L)

    // we want simulate with 15ms each step, around 60 steps per second
    val targetStepTimeNanos = 15_000_000
    var delta = 0L
    var catchUpCount = 0L
    while (!shutdownFlag.get()) {
        pendulumSimulator.execute()
        val time = pendulumSimulator.getNanosPerStep()
        if (time < targetStepTimeNanos) {
            Thread.sleep(max(0, (targetStepTimeNanos - time) / 1_000_000 - delta))
            catchUpCount++
        } else {
            logger.warn("We are late!!! Current step use ${time}ns, current delta: $delta")
            // if we are late, we sleep less at next time
            delta++
            catchUpCount = 0
        }
        // set delta back if we are on time for a long time
        if (catchUpCount >= 5_0000) {
            catchUpCount = 0
            delta--
        }
    }

    pendulumSimulator.close()
    timer.cancel()
}

class MainWindow : Application() {
    private val logger = LoggerFactory.getLogger(MainWindow::class.java)
    private var cpuRenderLimit = 64
    private var scale = 150
    private lateinit var renderChoice: List<Int>
    private val colorList = ColorList(true, Random(System.currentTimeMillis()))
    private var lastUpdateTimeMs = System.currentTimeMillis()

    override fun start(primaryStage: Stage) {
        val root = Pane()
        val scene = Scene(root, windowWidth, windowHeight)

        val canvas = Canvas()
        canvas.widthProperty().bind(root.widthProperty())
        canvas.heightProperty().bind(root.heightProperty())
        root.children.add(canvas)
        root.style = "-fx-background-color: #2F2F2F"

        // choose which pendulum we want to render
        require(cpuRenderLimit <= pendulumCount) { "Cannot render non-exists pendulum" }
        renderChoice = List(pendulumCount) { it }.toList().shuffled().take(cpuRenderLimit)

        logger.info("Start canvas update timer")
        val timer = object : AnimationTimer() {
            private val nodeRadius = 8.0
            private val lineWidth = 3.0
            override fun handle(now: Long) {
                // read data from GPU
                pendulumSimulator.syncData()
                val position = pendulumSimulator.getPendulumPosition()
                val length = pendulumSimulator.getPendulumLength()
                val centerX = canvas.width / 2
                // center positioned at top 1/3
                val centerY = canvas.height / 3

                val graphicsContext = canvas.graphicsContext2D
                graphicsContext.clearRect(0.0, 0.0, canvas.width, canvas.height)
                // reader pendulums
                for (i in renderChoice.indices) {
                    val x1 = centerX + scale * length[i].first * sin(position[i].first)
                    val y1 = centerY + scale * length[i].first * cos(position[i].first)

                    val x2 = x1 + scale * length[i].second * sin(position[i].second)
                    val y2 = y1 + scale * length[i].second * cos(position[i].second)

                    graphicsContext.stroke = colorList[i].brighter()
                    graphicsContext.lineWidth = lineWidth
                    graphicsContext.beginPath()
                    graphicsContext.moveTo(centerX, centerY)
                    graphicsContext.lineTo(x1, y1)
                    graphicsContext.lineTo(x2, y2)
                    graphicsContext.stroke()
                    graphicsContext.fill = colorList[i].brighter()
                    graphicsContext.fillOval(x1 - nodeRadius, y1 - nodeRadius, 2 * nodeRadius, 2 * nodeRadius)
                    graphicsContext.fillOval(x2 - nodeRadius, y2 - nodeRadius, 2 * nodeRadius, 2 * nodeRadius)
                }
                // render some status
                graphicsContext.fill = Color.WHITE
                graphicsContext.fillText(
                    "Pendulum count: $cpuRenderLimit${if (cpuRenderLimit != renderChoice.size) ", click to apply" else ""}",
                    5.0, 15.0
                )
                graphicsContext.fillText(
                    "Scale: $scale",
                    5.0, 30.0
                )
                val currentTime = System.currentTimeMillis()
                graphicsContext.fillText(
                    "FPS: ${1000 / (currentTime - lastUpdateTimeMs)}",
                    5.0, 45.0
                )
                lastUpdateTimeMs = currentTime
            }
        }
        timer.start()

        primaryStage.scene = scene
        primaryStage.title = "Double pendulum simulation using OpenCL"
        primaryStage.isResizable = false

        primaryStage.setOnCloseRequest {
            logger.info("Application shutdown")
            timer.stop()
            shutdownFlag.set(true)
        }

        // Left click to re-chosen pendulums
        canvas.onMouseClicked = EventHandler { event ->
            if (event.button == MouseButton.PRIMARY) {
                renderChoice = List(pendulumCount) { it }.toList().shuffled().take(cpuRenderLimit)
                colorList.reset()
            }
        }

        // scroll to change pendulum count and rod scale
        canvas.onScroll = EventHandler {
            cpuRenderLimit = when {
                it.deltaY > 0 -> {
                    min(cpuRenderLimit + 1, Int.MAX_VALUE)
                }
                it.deltaY < 0 -> {
                    max(1, cpuRenderLimit - 1)
                }
                else -> {
                    cpuRenderLimit
                }
            }

            scale = when {
                it.deltaX > 0 -> {
                    min(scale + 1, Int.MAX_VALUE)
                }
                it.deltaX < 0 -> {
                    max(1, scale - 1)
                }
                else -> {
                    scale
                }
            }

        }

        primaryStage.show()
    }
}