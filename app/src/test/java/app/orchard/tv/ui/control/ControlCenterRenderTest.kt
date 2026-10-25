package app.orchard.tv.ui.control

import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.apps.CustomIcons
import app.orchard.tv.apps.SourceLooks
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Effects
import app.orchard.tv.ui.home.HomeScreen
import app.orchard.tv.wallpaper.Wallpapers
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** Real Home/Control Center rendering and remote-key wiring, without a TV or new test framework. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlCenterRenderTest {
    @Test fun panelRetainsAppearanceAndNavigation() {
        renderHome(waitBeforeOpening = true)
        renderHome(waitBeforeOpening = true, withWallpaper = true)
        renderHome(waitBeforeOpening = true, withWallpaper = true, brightWallpaper = true)
        renderHome(waitBeforeOpening = true, withWallpaper = true, effects = Effects.Lite, warmPanel = true)
        ControlCenterLayoutChecks.verify()
    }

    @Test fun openingImmediatelyRendersPanel() {
        renderHome(waitBeforeOpening = false)
    }

    private fun renderHome(waitBeforeOpening: Boolean, withWallpaper: Boolean = false, brightWallpaper: Boolean = false,
                           effects: Effects = Effects.Full, warmPanel: Boolean = false) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("settings", 0).edit().putBoolean("setup_offered", true).commit()
        shadowOf(app).grantPermissions(app.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        AerialSettings.init(app)
        AerialSettings.chooseEffects(effects)
        CustomIcons.init(app)
        SourceLooks.init(app)
        val backdrop = ReflectionHelpers.getField<MutableState<ImageBitmap?>>(Wallpapers, "backdrop\$delegate")
        val frost = ReflectionHelpers.getField<MutableState<ImageBitmap?>>(Wallpapers, "frosted\$delegate")
        val oldBackdrop = backdrop.value
        val oldFrost = frost.value
        if (withWallpaper) {
            // Deterministic, soft wallpaper: exercise the frosted shader, not only the flat fallback.
            val image = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
            Canvas(image).drawPaint(android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 480f, 270f,
                    intArrayOf(0xFF42758C.toInt(), 0xFF7C668D.toInt(), 0xFFC18A66.toInt()),
                    null, android.graphics.Shader.TileMode.CLAMP)
            })
            if (brightWallpaper) image.eraseColor(android.graphics.Color.WHITE)
            backdrop.value = image.asImageBitmap()
            frost.value = image.asImageBitmap()
        }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controller.windowFocusChanged(true)
        val activity = controller.get()
        val vm = ViewModelProvider(activity)[HomeViewModel::class.java]
        vm.resumed = warmPanel
        val warmer = Class.forName("app.orchard.tv.ui.home.ControlCenterWarmed")
        ReflectionHelpers.setStaticField(warmer, "done", false)
        if (!waitBeforeOpening) vm.overlay = Overlay.ControlCenter()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        // Drive Compose frames explicitly: advancing Robolectric's looper alone can leave recomposition idle.
        val renderClock = androidx.compose.runtime.BroadcastFrameClock()
        val renderScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + renderClock)
        val recomposer = androidx.compose.runtime.Recomposer(renderScope.coroutineContext)
        val rendering = renderScope.launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            activity.setContent(parent = recomposer) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    HomeScreen(vm, { _, _ -> error("Unexpected launch") }, { error("Unexpected system page") }, {}, {}, {})
                }
            }
            val view = activity.window.decorView
            fun frame() {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                renderClock.sendFrame(android.os.SystemClock.uptimeMillis() * 1_000_000)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 1920, 1080)
                bitmap.eraseColor(android.graphics.Color.BLACK)
                view.draw(Canvas(bitmap))
            }
            fun composeRoot(node: View): View? {
                if (node.javaClass.simpleName == "AndroidComposeView") return node
                if (node is ViewGroup) for (i in 0 until node.childCount) {
                    composeRoot(node.getChildAt(i))?.let { return it }
                }
                return null
            }
            fun key(code: Int) {
                val target = checkNotNull(composeRoot(view))
                target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
            fun settled() { repeat(60) { frame() } }
            frame()
            if (waitBeforeOpening) {
                settled()
                assertEquals(Overlay.None, vm.overlay)
            }
            if (warmPanel) {
                // Exercise Home's actual idle prewarm and ensure it does not open the panel.
                for (i in 0 until 360) {
                    frame()
                    if (ReflectionHelpers.getStaticField<Boolean>(warmer, "done")) break
                }
                assertTrue("Idle Home must prewarm Control Center", ReflectionHelpers.getStaticField(warmer, "done"))
                repeat(6) { frame() }
                assertEquals(Overlay.None, vm.overlay)
            }
            vm.overlay = Overlay.ControlCenter()
            settled()
            assertEquals(Overlay.ControlCenter(), vm.overlay)
            if (!waitBeforeOpening) {
                return
            }
            val preview = File("build/reports/control-center/" + when {
                warmPanel -> "prewarmed-lite.png"
                brightWallpaper -> "bright-wallpaper.png"
                withWallpaper -> "wallpaper.png"
                else -> "flat.png"
            })
            checkNotNull(preview.parentFile).mkdirs()
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val first = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            try {
                // The panel takes focus: OK activates Inputs, then Back returns to Main.
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                settled()
                assertEquals("wallpaper=$withWallpaper bright=$brightWallpaper warm=$warmPanel", Overlay.ControlCenter(CcPage.Inputs), vm.overlay)
                activity.onBackPressedDispatcher.onBackPressed()
                settled()
                assertEquals(Overlay.ControlCenter(), vm.overlay)
                activity.onBackPressedDispatcher.onBackPressed()
                settled()
                assertEquals(Overlay.None, vm.overlay)
                vm.overlay = Overlay.ControlCenter()
                settled()
                assertEquals(Overlay.ControlCenter(), vm.overlay)
                assertTrue("Reopening must preserve panel pixels and initial focus", first.sameAs(bitmap))
            } finally {
                first.recycle()
            }
        } finally {
            recomposer.cancel()
            rendering.cancel()
            bitmap.recycle()
            controller.pause().stop().destroy()
            backdrop.value = oldBackdrop
            frost.value = oldFrost
        }
    }
}
