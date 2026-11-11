package app.tgdrive.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.runner.RunWith

private const val TARGET="app.tgdrive.next.benchmark"
private fun seed(visual:Boolean=true){
    val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.executeShellCommand("am start -W -n $TARGET/app.tgdrive.benchmark.SeedActivity --ez visual $visual")
    check(device.wait(Until.hasObject(By.text("Benchmark library ready")),45_000)){"Synthetic benchmark library was not prepared"}
    device.pressHome()
}
private fun MacrobenchmarkScope.tabsAndOverlays(){
    fun clickDescription(description:String){
        val node=device.wait(Until.findObject(By.desc(description)),15_000)?:error("Missing $description")
        node.click();device.waitForIdle()
    }
    check(device.wait(Until.hasObject(By.desc("Gallery")),15_000)){"Library was not shown"}
    clickDescription("Gallery")
    clickDescription("Search gallery");device.pressBack();device.waitForIdle()
    clickDescription("Sort gallery");device.pressBack();device.waitForIdle()
    clickDescription("Audio")
    clickDescription("Search audio");device.pressBack();device.waitForIdle()
    clickDescription("Settings")
    clickDescription("Home")
    val more=device.wait(Until.findObject(By.descContains("Options for")),15_000)?:error("Recent file menu was not shown")
    more.click();device.waitForIdle();device.pressBack();device.waitForIdle()
}
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val profile=BaselineProfileRule()
    @Before fun prepare()=seed()
    @Test fun capture()=profile.collect(packageName=TARGET,includeInStartupProfile=true){
        pressHome();startActivityAndWait();tabsAndOverlays()
    }
}
@RunWith(AndroidJUnit4::class)
class StartupFrameBenchmarks {
    @get:Rule val benchmark=MacrobenchmarkRule()
    @Test fun coldWithoutVisualPreparation(){seed(false);measure(CompilationMode.None())}
    @Test fun coldWithVisualPreparation(){seed(true);measure(CompilationMode.None())}
    @Test fun coldWithGeneratedProfile(){seed(true);measure(CompilationMode.Partial(baselineProfileMode=BaselineProfileMode.Require))}
    private fun measure(compilation:CompilationMode)=benchmark.measureRepeated(
        packageName=TARGET,metrics=listOf(StartupTimingMetric(),FrameTimingMetric()),
        compilationMode=compilation,startupMode=StartupMode.COLD,iterations=5,
        setupBlock={pressHome()},measureBlock={startActivityAndWait();tabsAndOverlays()}
    )
}
