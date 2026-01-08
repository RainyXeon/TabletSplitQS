package com.tablet.splitqs;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TabletLandscapeModule implements IXposedHookLoadPackage {

    private static final String SYSTEMUI = "com.android.systemui";
    private static final float NOTIF_WIDTH_RATIO = 0.6f;
    private static final int TAG_SPLITQS_MEASURED = 0x53485153;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;
        try {
            hookSplitShadeDecision(lpparam);
            hookSystemBooleanResource();
            hookSystemIntegerResource();
            hookWindowRootViewInsets(lpparam);
            hookNotificationWidth(lpparam);
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
                    Configuration c = Resources.getSystem().getConfiguration();
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
                            // Disable skinny notifications in landscape
                            case "config_skinnyNotifsInLandscape":
                                param.setResult(c.orientation != Configuration.ORIENTATION_LANDSCAPE);
                                break;
                            // One-handed bouncer should be PORTRAIT only
                            case "can_use_one_handed_bouncer":
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
                        switch (entryName) {
                            case "quick_settings_num_columns":
                            case "quick_qs_panel_max_rows":
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

    private void hookWindowRootViewInsets(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.systemui.scene.ui.view.WindowRootView",
            lpparam.classLoader,
            "onApplyWindowInsets",
            WindowInsets.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View root = (View) param.thisObject;
                    Resources res = root.getResources();
                    Configuration c = res.getConfiguration();
                    if (c.orientation != Configuration.ORIENTATION_LANDSCAPE) return;
                    if (!(root instanceof ViewGroup)) return;
                    ViewGroup vg = (ViewGroup) root;
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        View child = vg.getChildAt(i);
                        if (!(child.getLayoutParams()
                                instanceof ViewGroup.MarginLayoutParams)) {
                            continue;
                        }
                        ViewGroup.MarginLayoutParams lp =
                                (ViewGroup.MarginLayoutParams)
                                        child.getLayoutParams();
                        if (lp.leftMargin != 0) {
                            lp.setMargins(
                                    0,
                                    lp.topMargin,
                                    lp.rightMargin,
                                    lp.bottomMargin
                            );
                            child.setLayoutParams(lp);
                        }
                    }
                }
            }
        );
    }

    private void hookNotificationWidth(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
            lpparam.classLoader,
            "onMeasure",
            int.class,
            int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    Resources res = view.getResources();
                    Configuration c = res.getConfiguration();
                    // Landscape only
                    if (c.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                        view.setTag(TAG_SPLITQS_MEASURED, null);
                        return;
                    }
                    // Prevent infinite re-measure
                    if (view.getTag(TAG_SPLITQS_MEASURED) != null) {
                        return;
                    }
                    int widthSpec = (int) param.args[0];
                    int heightSpec = (int) param.args[1];
                    int fullWidth = View.MeasureSpec.getSize(widthSpec);
                    if (fullWidth <= 0) return;
                    int newWidth = (int) (fullWidth * NOTIF_WIDTH_RATIO);
                    int newWidthSpec = View.MeasureSpec.makeMeasureSpec(
                            newWidth,
                            View.MeasureSpec.EXACTLY
                    );
                    view.setTag(TAG_SPLITQS_MEASURED, Boolean.TRUE);
                    // Re-measure ONCE
                    view.measure(newWidthSpec, heightSpec);
                }
            }
        );
    }
}
