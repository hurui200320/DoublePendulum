package info.skyblond.pendulum.utils

import java.nio.charset.StandardCharsets

object ResourceUtil {
    fun readResourceFileContent(filename: String): String {
        return this.javaClass.getResourceAsStream(filename)!!.bufferedReader(StandardCharsets.UTF_8).lineSequence()
            .joinToString("\n")
    }
}