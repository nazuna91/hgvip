package com.dsh.hgvip;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 黄果短剧 VIP 解锁。
 *
 * Hook 点（混淆名）：
 *   com.video.bean.UserJsonAdapter.a(JsonReader) -> User   （Moshi fromJson 被混淆成 a）
 *   com.video.bean.User 的构造
 *   com.video.bean.MemberBenefit 的构造
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "HGVIP";
    private static final String[] TARGETS = { "a4256.a752034a.ac7397272053", "c79cb.c96a14df.c64797012005" };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        boolean hit = false;
        for (String t : TARGETS) { if (t.equals(lp.packageName)) { hit = true; break; } }
        if (!hit) return;
        log("loaded in " + lp.packageName);

        hookUserAdapter(lp.classLoader);
        hookUserConstructor(lp.classLoader);
        hookBenefit(lp.classLoader);
        hookPlayInfo(lp.classLoader);
    }

    /** hook UserJsonAdapter 里所有返回 User 的方法（混淆名 a/e 之类） */
    private void hookUserAdapter(ClassLoader cl) {
        try {
            Class<?> adapter = XposedHelpers.findClass("com.video.bean.UserJsonAdapter", cl);
            Class<?> userCls = XposedHelpers.findClass("com.video.bean.User", cl);
            int n = 0;
            for (Method m : adapter.getDeclaredMethods()) {
                if (!m.getReturnType().equals(userCls)) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object user = param.getResult();
                            if (user != null) patchUser(user);
                        }
                    });
                    n++;
                    log("hooked adapter." + m.getName() + " -> User");
                } catch (Throwable ignored) {}
            }
            log("adapter methods hooked: " + n);
        } catch (Throwable t) {
            log("adapter fail: " + t.getMessage());
        }
    }

    private void hookUserConstructor(ClassLoader cl) {
        try {
            Class<?> userCls = XposedHelpers.findClass("com.video.bean.User", cl);
            for (java.lang.reflect.Constructor<?> c : userCls.getDeclaredConstructors()) {
                try {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject != null) patchUser(param.thisObject);
                        }
                    });
                } catch (Throwable ignored) {}
            }
            log("hooked User constructors");
        } catch (Throwable t) {
            log("user class fail: " + t.getMessage());
        }
    }

    private void hookBenefit(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClass("com.video.bean.MemberBenefit", cl);
            for (java.lang.reflect.Constructor<?> c : cls.getDeclaredConstructors()) {
                try {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object obj = param.thisObject;
                            if (obj == null) return;
                            for (Field f : obj.getClass().getDeclaredFields()) {
                                if (Modifier.isStatic(f.getModifiers())) continue;
                                if (f.getType() == boolean.class) {
                                    try { f.setAccessible(true); f.setBoolean(obj, true); } catch (Throwable ignored) {}
                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
            log("hooked MemberBenefit constructors");
        } catch (Throwable t) {
            log("MemberBenefit fail: " + t.getMessage());
        }
    }

    /** hook PlayInfo：把试看索引清零（解除试看限制） */
    private void hookPlayInfo(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClass("com.video.business.videoDetail.PlayInfo", cl);
            for (java.lang.reflect.Constructor<?> c : cls.getDeclaredConstructors()) {
                try {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object obj = param.thisObject;
                            if (obj == null) return;
                            try {
                                Field f = obj.getClass().getDeclaredField("currentTryIndex");
                                f.setAccessible(true);
                                f.setInt(obj, 0);
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }
            log("hooked PlayInfo constructors");
        } catch (Throwable t) {
            log("PlayInfo fail: " + t.getMessage());
        }
    }

    /** 数值字段拉满 */
    private void patchUser(Object user) {
        Class<?> cls = user.getClass();
        StringBuilder sb = new StringBuilder("patched:");
        int changed = 0;
        for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Class<?> t = f.getType();
                if (t == int.class) {
                    // vipId 是暗网卡的 id（6），其他 int 拉满
                    String ann = jsonName(f);
                    boolean isVipId = ann != null && ann.equalsIgnoreCase("vipId");
                    if (isVipId) {
                        f.setInt(user, 6);
                        sb.append(" ").append(f.getName()).append("=6(dark)");
                        changed++;
                    } else {
                        int cur = f.getInt(user);
                        if (cur <= 0) { f.setInt(user, 999); sb.append(" ").append(f.getName()).append("=999"); changed++; }
                    }
                } else if (t == long.class) {
                    long cur = f.getLong(user);
                    if (cur <= 0) { f.setLong(user, 4102444800000L); sb.append(" ").append(f.getName()).append("=2100"); changed++; }
                } else if (t == boolean.class) {
                    if (!f.getBoolean(user)) { f.setBoolean(user, true); sb.append(" ").append(f.getName()).append("=true"); changed++; }
                }
            } catch (Throwable ignored) {}
        }
        log(sb.toString() + " (changed=" + changed + ")");
    }

    /** 读 Moshi @Json(name=...) 注解 */
    private String jsonName(Field f) {
        try {
            for (java.lang.annotation.Annotation a : f.getAnnotations()) {
                String an = a.annotationType().getName();
                if (!an.endsWith("Json")) continue;
                java.lang.reflect.Method m = a.annotationType().getMethod("name");
                Object v = m.invoke(a);
                if (v instanceof String) return (String) v;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
        Log.i(TAG, msg);
    }
}
