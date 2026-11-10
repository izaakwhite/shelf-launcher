package app.shelf.launcher

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object Palette {
    val bgTop = Color(0xFF161D2C)
    val bgBottom = Color(0xFF07090E)
    val text = Color(0xFFF4F6FA)
    val textDim = Color(0xB3F4F6FA)
}

object Type {
    val clock = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 22.sp, color = Palette.text)
    val shelfTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 34.sp, color = Palette.text)
    val shelfHint = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = Palette.textDim)
    val tileLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Palette.text)
    val header = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Palette.textDim)
    val panelTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Palette.text)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp)
    val pill = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp)
}

enum class Dir(val focus: FocusDirection, val vec: Offset) {
    LEFT(FocusDirection.Left, Offset(-1f, 0f)),
    RIGHT(FocusDirection.Right, Offset(1f, 0f)),
    UP(FocusDirection.Up, Offset(0f, -1f)),
    DOWN(FocusDirection.Down, Offset(0f, 1f)),
}

/** Remembers the last D-pad move so the tile receiving focus knows where focus came from. */
object Nav {
    private var dir: Dir? = null
    private var at = 0L
    fun mark(d: Dir) { dir = d; at = android.os.SystemClock.uptimeMillis() }
    fun clear() { dir = null }
    fun recent(): Dir? = dir?.takeIf { android.os.SystemClock.uptimeMillis() - at < 250 }
}
