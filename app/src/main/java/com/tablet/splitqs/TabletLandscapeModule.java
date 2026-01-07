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

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
