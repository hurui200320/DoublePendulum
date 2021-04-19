# DoublePendulum
Simulate double pendulum using OpenCL.

## Basic usage

1. Clone the project, import with IDEA and let Gradle download dependences
2. Change some parameters to fit your needs and your device
3. Run the program

### Major Dependences

`org.jocl:jocl` is a handful OpenCL binding and is used to handle OpenCL things.

Gradle plugin `org.openjfx.javafxplugin` is used to automatically handle JavaFX dependences on JDK11. If you are using other version of JDK, please change the JavaFX version [here](https://github.com/hurui200320/DoublePendulum/blob/master/build.gradle#L15).

This project has explicit instructions to use JDK11, if you are using JDK under 11, please change that in [`build.gradle`](https://github.com/hurui200320/DoublePendulum/blob/master/build.gradle#L37).

### Parameters need be changed before running

In [`info.skyblond.pendulum.Main.kt`](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/Main.kt), there are some parameters defined to decide program's behaver and should be determined BEFORE running the program.

```kotlin
const val pendulumCount = 1024
const val localWorkerNum = 1024
const val windowWidth = 800.0
const val windowHeight = 600.0
const val targetSPS = 200
```

`pendulumCount` decides how many pendulums are simulated concurrently using OpenGL.

`localWorkerNum` is the `local_work_size` passed to OpenCL, this parameter must be able to divide `pendulumCount`, and based on your OpenCL device, this is limited by the device and being a multiplier of 32 or other value may let you have a performance gain. Please check `clinfo` for this value.

`windowWidth` and `windowHeight` decide the window size of JavaFX application. Change to your needs.

`targetSPS` decides the precision of simulation. This project using [Riemann sum](https://en.wikipedia.org/wiki/Riemann_sum) to update value, thus we need a `deltaT` to compute `f'(t_0) * deltaT`, by specifying target Step-Per-Second,  `deltaT` is `1.0 / targetSPS`, and program will try to manage that SPS. However limited by Java's `Thread.sleep()`, the finest time resolution is 1ms, thus we limited max SPS is 1000.

### Other parameters that can be changed if you want

There are also some parameters that you can change before running.

If you changed Windows size, then you might want to change the scale of drawing. At [`info.skyblond.pendulum.MainWindow .kt`](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/MainWindow.kt#L23), you will have:

```kotlin
    private var scale = 150
```

This decides how rod length is interpreted to pixel length.

Also, due to CPU's computing model, rendering all pendulum at once is not always possible, thus we random choose some of them, default number is 64,if this is still to big for your CPU, you can change `cpuRenderLimit ` at [`info.skyblond.pendulum.MainWindow .kt`](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/MainWindow.kt#L24).

This simulation come with a default initial parameter for pendulum, if you want to try yours, then change them at [`info.skyblond.pendulum.Main.kt`](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/Main.kt#L20):

```kotlin
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
```

By default, each pendulum has rod length of `(1.0f, 1.0f)`, each for one of the pendulum.

The mass of all pendulum is set to 2Kg. The initial position is set with some randomness to show the sensitivity of double pendulum system. The coordinate is defined with 0 is straight down and positive is counter-clock wise. 

And there are no initial velocity by default.

You can change those parameters as you want and be carful with the type. In OpenCL, we are using `Float` instead of `Double`.

### User interface

There are also some user interface.

+ Scroll up and down to adjust amount of pendulums rendered on screen.
+ Shift + scroll up and down to adjust scale of pendulum's rod length, i.e. zoom in or out.
+ Left click to re-choose pendulums to be rendered

## Project structure

### Pendulum simulation

The formula is directly copied from [MyPhysicsLab](https://www.myphysicslab.com/pendulum/double-pendulum-en.html) and implemented them in OpenCL. You can find the kernel code at [here](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/resources/kernel/pendulum.cl). Please note that two macro defines are place holder, which will be replaced by actual parameter in [the code](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/opencl/PendulumSimulator.kt#L94).

### Interface with JOCL

[`PendulumSimulator`](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/opencl/PendulumSimulator.kt) is designed to handle all JOCL things in codes. It handles the initialization, invoking, read/write data from/to OpenCL device' memory, and release resource. It implemented `AutoCloseable` interface, thus you can use `pendulumSimulator.use {}` with it, in case you forget invoking `close()`.

This class only used to interface with JOCL, thus controlling SFS is main thread's responsibility. 

### Render pendulums

This project using JavaFX to render Pendulums, thus render events are all CPU' work. 

[`MainWindow ` ](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/MainWindow.kt) is designed to handle all JavaFX things. To update Canvas regularly, we used a `AnimationTimer` to accomplish the job. It seems like now it locking up with 60FPS, but if you render to much pendulums, you might experienced low fps. I tested on my laptop with 2500 pendulums rendered at same time, and I only get 3FPS.

### OpenCL device selection

To select the best OpenCL, a utility file are implemented [here](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/opencl/Utils.kt). It automatically list all the device in the system and choose the device with max `CL_DEVICE_MAX_COMPUTE_UNITS`. If you want to manually override it, you can return your own device info at [`getBestOpenCLDevice()` ](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/opencl/Utils.kt#L67) or let JOCL use your device's id at [here](https://github.com/hurui200320/DoublePendulum/blob/master/src/main/kotlin/info/skyblond/pendulum/opencl/PendulumSimulator.kt#L44).

## Screenshot

![Image 1](https://github.com/hurui200320/DoublePendulum/blob/master/pic/pic1.png)

![Image 1](https://github.com/hurui200320/DoublePendulum/blob/master/pic/pic2.png)

![Image 1](https://github.com/hurui200320/DoublePendulum/blob/master/pic/pic3.png)







