package com.tablet.splitqs;

import android.content.res.Configuration;
import android.content.res.Resources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TabletLandscapeModule implements IXposedHookLoadPackage {

    private static final String SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {

        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        try {
            hookSplitShadeDecision(lpparam);
            hookSystemBooleanResource();
            hookSystemIntegerResource();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void hookSplitShadeDecision(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cls = XposedHelpers.findClass(
            "com.android.systemui.statusbar.policy.SplitShadeStateControllerImpl",
            lpparam.classLoader
        );
        XposedBridge.hookAllMethods(
            cls,
            "shouldUseSplitNotificationShade",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Configuration c =
                        Resources.getSystem().getConfiguration();
                    param.setResult(
                        c.orientation == Configuration.ORIENTATION_LANDSCAPE
                    );
                }
            }
        );
    }

    private void hookSystemBooleanResource() {
        XposedHelpers.findAndHookMethod(
            Resources.class,
            "getBoolean",
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Resources res = (Resources) param.thisObject;
                    int id = (int) param.args[0];
                    try {
                        String entryName = res.getResourceEntryName(id);
                        Configuration c = res.getConfiguration();
                        switch (entryName) {
                            case "config_skinnyNotifsInLandscape":
                                // false only in landscape
                                param.setResult(c.orientation != Configuration.ORIENTATION_LANDSCAPE);
                                break;
                            case "can_use_one_handed_bouncer":
                                // true only in portrait
                                param.setResult(c.orientation == Configuration.ORIENTATION_LANDSCAPE);
                                break;
                        }
                    } catch (Resources.NotFoundException ignored) {
                    }
                }
            }
        );
    }

    private void hookSystemIntegerResource() {
        XposedHelpers.findAndHookMethod(
            Resources.class,
            "getInteger",
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Resources res = (Resources) param.thisObject;
                    int id = (int) param.args[0];
                    try {
                        String entryName = res.getResourceEntryName(id);
                        Configuration c = res.getConfiguration();
                        switch (entryName) {
                            case "quick_settings_num_columns":
                                param.setResult(2);
                                break;
                            case "quick_qs_panel_max_rows":
                                param.setResult(2);
                                break;
                            case "quick_qs_panel_max_tiles":
                                param.setResult(2);
                                break;
                        }
                    } catch (Resources.NotFoundException ignored) {
                    }
                }
            }
        );
    }
}
