package com.vpr.screenlate.background

/**
 * A system app's screen where apps are allowed to start on their own and keep running in the background.
 *
 * @param appLaunch the "App launch" list of Honor and Huawei, whose path the Background work screen spells out.
 */
internal data class StartupScreen(val packageName: String, val className: String, val appLaunch: Boolean = false)

/**
 * Startup and background management screens of phone makers' system apps. These stop background apps on their own,
 * apart from Android's battery optimization. A phone has at most one maker's screens; each maker's list goes from
 * current system versions to older ones.
 */
internal object StartupScreens {
    val known: List<StartupScreen> = listOf(
        // Honor (MagicOS): App launch.
        StartupScreen("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity", appLaunch = true),
        StartupScreen("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity", appLaunch = true),
        // Huawei (EMUI, HarmonyOS): App launch, then protected apps on old versions.
        StartupScreen("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity", appLaunch = true),
        StartupScreen("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity", appLaunch = true),
        StartupScreen("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        // Xiaomi, Redmi, POCO (MIUI, HyperOS): Autostart.
        StartupScreen("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // OPPO, realme, OnePlus (ColorOS, realme UI, OxygenOS): startup manager, then battery usage.
        StartupScreen("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        StartupScreen("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        StartupScreen("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        StartupScreen("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        StartupScreen("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
        // vivo, iQOO (OriginOS, Funtouch OS): background startup.
        StartupScreen("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        StartupScreen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        StartupScreen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        // Samsung (One UI): battery, where "Never sleeping apps" is.
        StartupScreen("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        StartupScreen("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        // ASUS: Auto-start manager.
        StartupScreen("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        StartupScreen("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
        // Tecno, Infinix, itel (HiOS, XOS): Auto-start.
        StartupScreen("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
        // Meizu (Flyme): background management.
        StartupScreen("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
        // Nokia (HMD, older versions): power saving exceptions.
        StartupScreen("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
        // LeEco: auto boot.
        StartupScreen("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        // HTC: power manager.
        StartupScreen("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity"),
    )

    /** The first known screen that [opens] reports as present and startable on this phone. */
    fun find(opens: (StartupScreen) -> Boolean): StartupScreen? = known.firstOrNull(opens)

    /** Whether the screen [packageName]/[className] is an "App launch" list (see [StartupScreen.appLaunch]). */
    fun isAppLaunch(packageName: String, className: String): Boolean =
        known.any { it.appLaunch && it.packageName == packageName && it.className == className }

    /** System apps whose screens are listed; the manifest must make them visible to the app (`<queries>`). */
    val packages: Set<String> get() = known.mapTo(linkedSetOf()) { it.packageName }
}
