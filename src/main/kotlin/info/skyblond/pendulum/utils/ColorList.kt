package info.skyblond.pendulum.utils

import javafx.scene.paint.Color
import kotlin.random.Random

/**
 * A color list that give you unlimited colors
 * */
class ColorList(
    private val randomized: Boolean = false,
    private val random: Random = Random
) {
    private val predefined = mutableListOf(
        Color.ALICEBLUE, Color.ANTIQUEWHITE, Color.AQUA, Color.AQUAMARINE, Color.AZURE,
        Color.BEIGE, Color.BISQUE, Color.BLACK, Color.BLANCHEDALMOND, Color.BLUE,
        Color.BLUEVIOLET, Color.BROWN, Color.BURLYWOOD, Color.CADETBLUE, Color.CHARTREUSE,
        Color.CHOCOLATE, Color.CORAL, Color.CORNFLOWERBLUE, Color.CORNSILK, Color.CRIMSON,
        Color.CYAN, Color.DARKBLUE, Color.DARKCYAN, Color.DARKGOLDENROD, Color.DARKGRAY,
        Color.DARKGREEN, Color.DARKGREY, Color.DARKKHAKI, Color.DARKMAGENTA, Color.DARKOLIVEGREEN,
        Color.DARKORANGE, Color.DARKORCHID, Color.DARKRED, Color.DARKSALMON, Color.DARKSEAGREEN,
        Color.DARKSLATEBLUE, Color.DARKSLATEGRAY, Color.DARKSLATEGREY, Color.DARKTURQUOISE, Color.DARKVIOLET,
        Color.DEEPPINK, Color.DEEPSKYBLUE, Color.DIMGRAY, Color.DIMGREY, Color.DODGERBLUE,
        Color.FIREBRICK, Color.FLORALWHITE, Color.FORESTGREEN, Color.FUCHSIA, Color.GAINSBORO,
        Color.GHOSTWHITE, Color.GOLD, Color.GOLDENROD, Color.GRAY, Color.GREEN,
        Color.GREENYELLOW, Color.GREY, Color.HONEYDEW, Color.HOTPINK, Color.INDIANRED,
        Color.INDIGO, Color.IVORY, Color.KHAKI, Color.LAVENDER, Color.LAVENDERBLUSH,
        Color.LAWNGREEN, Color.LEMONCHIFFON, Color.LIGHTBLUE, Color.LIGHTCORAL, Color.LIGHTCYAN,
        Color.LIGHTGOLDENRODYELLOW, Color.LIGHTGRAY, Color.LIGHTGREEN, Color.LIGHTGREY, Color.LIGHTPINK,
        Color.LIGHTSALMON, Color.LIGHTSEAGREEN, Color.LIGHTSKYBLUE, Color.LIGHTSLATEGRAY, Color.LIGHTSLATEGREY,
        Color.LIGHTSTEELBLUE, Color.LIGHTYELLOW, Color.LIME, Color.LIMEGREEN, Color.LINEN,
        Color.MAGENTA, Color.MAROON, Color.MEDIUMAQUAMARINE, Color.MEDIUMBLUE, Color.MEDIUMORCHID,
        Color.MEDIUMPURPLE, Color.MEDIUMSEAGREEN, Color.MEDIUMSLATEBLUE, Color.MEDIUMSPRINGGREEN, Color.MEDIUMTURQUOISE,
        Color.MEDIUMVIOLETRED, Color.MIDNIGHTBLUE, Color.MINTCREAM, Color.MISTYROSE, Color.MOCCASIN,
        Color.NAVAJOWHITE, Color.NAVY, Color.OLDLACE, Color.OLIVE, Color.OLIVEDRAB,
        Color.ORANGE, Color.ORANGERED, Color.ORCHID, Color.PALEGOLDENROD, Color.PALEGREEN,
        Color.PALETURQUOISE, Color.PALEVIOLETRED, Color.PAPAYAWHIP, Color.PEACHPUFF, Color.PERU,
        Color.PINK, Color.PLUM, Color.POWDERBLUE, Color.PURPLE, Color.RED,
        Color.ROSYBROWN, Color.ROYALBLUE, Color.SADDLEBROWN, Color.SALMON, Color.SANDYBROWN,
        Color.SEAGREEN, Color.SEASHELL, Color.SIENNA, Color.SILVER, Color.SKYBLUE,
        Color.SLATEBLUE, Color.SLATEGRAY, Color.SLATEGREY, Color.SNOW, Color.SPRINGGREEN,
        Color.STEELBLUE, Color.TAN, Color.TEAL, Color.THISTLE, Color.TOMATO,
        Color.TRANSPARENT, Color.TURQUOISE, Color.VIOLET, Color.WHEAT, Color.WHITE,
        Color.WHITESMOKE, Color.YELLOW, Color.YELLOWGREEN
    )

    private val generatedColorTable = HashMap<Int, Color>()

    private val uniqueColorSet = HashSet<Color>()

    init {
        if (randomized) {
            predefined.shuffle()
        }
        for (color in predefined) {
            uniqueColorSet.add(color)
        }
    }

    private fun generateRandomColor(random: Random): Color {
        var color: Color
        do {
            color = Color.rgb(random.nextInt(0, 256), random.nextInt(0, 256), random.nextInt(0, 256))
        } while (uniqueColorSet.contains(color))
        uniqueColorSet.add(color)
        return color
    }

    operator fun get(index: Int): Color {
        return when {
            index < predefined.size -> {
                predefined[index]
            }
            else -> {
                generatedColorTable[index] ?: generateRandomColor(random).also {
                    generatedColorTable[index] = it
                }
            }
        }
    }

    fun reset(){
        if (randomized) {
            predefined.shuffle()
        }
        uniqueColorSet.clear()
        for (color in predefined) {
            uniqueColorSet.add(color)
        }
        generatedColorTable.clear()
    }
}