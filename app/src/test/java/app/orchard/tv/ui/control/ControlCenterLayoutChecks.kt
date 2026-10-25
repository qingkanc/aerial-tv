package app.orchard.tv.ui.control

import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.ui.home.Lunar
import app.orchard.tv.ui.home.ShelfClock
import app.orchard.tv.Overlay
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Effects
import app.orchard.tv.system.NetKind
import app.orchard.tv.system.NetworkStatus
import app.orchard.tv.update.Updates
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/** Uses the real panel and its text-layout semantics in the existing native-graphics sandbox. */
internal object ControlCenterLayoutChecks {
    fun verify() {
        val dates = listOf("2026-03-20T23:59:00", "2027-10-08T23:59:00", "2026-10-25T23:59:00")
        val names = listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ123456", "家庭客厅无线网络专用")
        for (effects in listOf(Effects.Full, Effects.Lite)) {
            for (date in dates) for (name in names) checkPanel(LocalDateTime.parse(date), name, effects)
        }
    }

    private fun checkPanel(now: LocalDateTime, ssid: String, effects: Effects) {
        val app = RuntimeEnvironment.getApplication()
        AerialSettings.init(app)
        AerialSettings.chooseEffects(effects)
        val oldBoot = AerialSettings.bootApp
        AerialSettings.chooseBootApp("TV")
        val available = ReflectionHelpers.getField<MutableState<String?>>(Updates, "available\$delegate")
        val oldAvailable = available.value
        available.value = "1.49"
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controller.windowFocusChanged(true)
        val activity = controller.get()
        val vm = ViewModelProvider(activity)[HomeViewModel::class.java]
        ReflectionHelpers.getField<MutableState<NetworkStatus>>(vm, "network\$delegate").value = NetworkStatus(NetKind.Wifi, ssid)
        vm.overlay = Overlay.ControlCenter()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        // Use the same explicit frame clock as the Home rendering regression.
        val renderClock = androidx.compose.runtime.BroadcastFrameClock()
        val renderScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + renderClock)
        val recomposer = androidx.compose.runtime.Recomposer(renderScope.coroutineContext)
        val rendering = renderScope.launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            activity.setContent(parent = recomposer) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    val overlay = vm.overlay as? Overlay.ControlCenter
                    ControlCenter(overlay != null, overlay?.page ?: CcPage.Main, vm, now,
                        { error("Unexpected launch") }, { vm.overlay = Overlay.None })
                }
            }
            val view = activity.window.decorView
            fun settle() {
                repeat(60) {
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                    renderClock.sendFrame(android.os.SystemClock.uptimeMillis() * 1_000_000)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                    view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, 1920, 1080)
                    bitmap.eraseColor(android.graphics.Color.BLACK)
                    view.draw(Canvas(bitmap))
                }
            }
            fun composeRoot(node: View): View? {
                if (node.javaClass.simpleName == "AndroidComposeView") return node
                if (node is ViewGroup) for (i in 0 until node.childCount) composeRoot(node.getChildAt(i))?.let { return it }
                return null
            }
            settle()
            val label = "${now.toLocalDate()}-${effects.key}-${if (ssid.startsWith("ABC")) "latin" else "chinese"}"
            val preview = File("build/reports/control-center/layout-$label.png")
            checkNotNull(preview.parentFile).mkdirs()
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val owner = ReflectionHelpers.callInstanceMethod<SemanticsOwner>(checkNotNull(composeRoot(view)), "getSemanticsOwner")
            fun texts(node: SemanticsNode): List<SemanticsNode> =
                (if (node.config.getOrNull(SemanticsProperties.Text) != null) listOf(node) else emptyList()) + node.children.flatMap(::texts)
            val nodes = texts(owner.unmergedRootSemanticsNode)
            fun text(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("") { it.text }
            fun layout(node: SemanticsNode): TextLayoutResult {
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
                return results.single()
            }
            val date = nodes.single { text(it).contains("星期") }
            assertFalse("Control Center follows upstream without a lunar suffix", text(date).contains("农历"))
            assertFalse("Control Center must not append the large clock's lunar date",
                text(date).contains(checkNotNull(Lunar.text(now.toLocalDate()))))
            val wifi = nodes.single { text(it) == ssid }
            val clock = nodes.single { text(it) == "23:59" }
            println("$label date=${text(date)} lines=${layout(date).lineCount} bounds=${date.boundsInRoot}")
            assertTrue("$label: Wi-Fi must not squeeze the calendar into its own column",
                date.boundsInRoot.top >= wifi.boundsInRoot.bottom)
            assertTrue("$label: date needs the panel width, at most two lines", layout(date).lineCount <= 2)
            assertFalse("$label: date must not overflow", layout(date).hasVisualOverflow)
            assertEquals("$label: clock must stay on one line", 1, layout(clock).lineCount)
            assertEquals("$label: Wi-Fi must stay on one line", 1, layout(wifi).lineCount)
            assertTrue("$label: long SSID should be ellipsized", layout(wifi).isLineEllipsized(0))
            for (node in nodes) {
                val bounds = node.boundsInRoot
                assertTrue("$label: ${text(node)} must remain inside the panel content: $bounds",
                    bounds.left >= 1287 && bounds.right <= 1825 && bounds.top >= 79 && bounds.bottom <= 1048 && bounds.height > 0)
            }
            for ((i, node) in nodes.withIndex()) for (other in nodes.drop(i + 1)) {
                val a = node.boundsInRoot
                val b = other.boundsInRoot
                assertFalse("$label: ${text(node)} overlaps ${text(other)}", a.overlaps(b))
            }
            val footer = nodes.single { text(it).startsWith("Aerial ") }
            val setup = nodes.single { text(it) == "设为主屏幕" }
            assertTrue("$label: footer must stay below all actions", footer.boundsInRoot.top >= setup.boundsInRoot.bottom)
            assertTrue(nodes.any { text(it).contains("1.49") })
            assertTrue(nodes.any { text(it) == "TV ›" })
            val first = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            try {
                activity.onBackPressedDispatcher.onBackPressed()
                settle()
                assertEquals(Overlay.None, vm.overlay)
                vm.overlay = Overlay.ControlCenter()
                settle()
                assertTrue("$label: reopen must preserve layout", first.sameAs(bitmap))
                // Reach the bottom action through the same remote events as the TV.
                fun key(code: Int) {
                    val target = checkNotNull(composeRoot(view))
                    target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }
                repeat(8) { key(KeyEvent.KEYCODE_DPAD_DOWN); settle() }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                settle()
                assertEquals("$label: bottom Setup action must stay reachable", Overlay.ControlCenter(CcPage.Setup), vm.overlay)
                activity.onBackPressedDispatcher.onBackPressed()
                settle()
                assertEquals(Overlay.ControlCenter(), vm.overlay)
            } finally { first.recycle() }
            activity.setContent(parent = recomposer) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) { ShelfClock(now) }
            }
            settle()
            val clockOwner = ReflectionHelpers.callInstanceMethod<SemanticsOwner>(checkNotNull(composeRoot(view)), "getSemanticsOwner")
            val clockDate = texts(clockOwner.unmergedRootSemanticsNode).single { text(it).contains("星期") }
            val lunar = checkNotNull(Lunar.text(now.toLocalDate()))
            assertEquals("Large clock must display the upstream lunar date exactly once",
                1, text(clockDate).windowed(lunar.length).count { it == lunar })
        } finally {
            recomposer.cancel()
            rendering.cancel()
            controller.pause().stop().destroy()
            bitmap.recycle()
            available.value = oldAvailable
            AerialSettings.chooseBootApp(oldBoot)
        }
    }
}
