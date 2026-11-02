package com.dsh.hgvip;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 黄果短剧 VIP 解锁模块。
 *
 * 目标：a4256.a752034a.ac7397272053
 * 原理：它的会员状态由服务端下发到 com.video.bean.User，
 *       但视频 m3u8 是完整给的（服务端不校验），
 *       所以只要把 User 的 vip 字段改成 true，前端就会解锁 UI。
 *
 * Hook 点：com.video.bean.UserJsonAdapter.fromJson(JsonReader)
 *        解析完成后反射改 vip 字段。
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "HGVIP";
    private static final String TARGET = "a4256.a752034a.ac7397272053";
    private static final String USER_ADAPTER = "com.video.bean.UserJsonAdapter";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!lp.packageName.equals(TARGET)) return;
        log("loaded in " + lp.packageName);

        Class<?> adapter;
        try {
            adapter = XposedHelpers.findClass(USER_ADAPTER, lp.classLoader);
        } catch (Throwable t) {
            log("adapter not found: " + t.getMessage());
            return;
        }

        // Moshi 的 JsonAdapter.fromJson 有两个重载：JsonReader / String
        for (String name : new String[]{"fromJson"}) {
            for (java.lang.reflect.Method m : adapter.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object user = param.getResult();
                            if (user != null) patchUser(user);
                        }
                    });
                    log("hooked " + m.getName() + "(" + m.getParameterTypes().length + " args)");
                } catch (Throwable t) {
                    log("hook fail: " + t.getMessage());
                }
            }
        }

        // 兜底：hook User 的构造，任何方式创建都改
        try {
            Class<?> userCls = XposedHelpers.findClass("com.video.bean.User", lp.classLoader);
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

    /** 反射遍历字段，把名字里含 vip 的改成"已开通" */
    private void patchUser(Object user) {
        Class<?> cls = user.getClass();
        int changed = 0;
        for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName().toLowerCase();
            String ann = jsonName(f);
            String key = (ann != null ? ann : name).toLowerCase();
            if (!key.contains("vip")) continue;
            try {
                f.setAccessible(true);
                Class<?> t = f.getType();
                if (t == boolean.class) {
                    f.setBoolean(user, true);
                    changed++;
                } else if (t == int.class) {
                    int cur = f.getInt(user);
                    if (cur <= 0) { f.setInt(user, 999); changed++; }
                } else if (t == long.class) {
                    long cur = f.getLong(user);
                    if (cur <= 0) { f.setLong(user, 4102444800000L); changed++; }
                } else if (t == String.class) {
                    Object cur = f.get(user);
                    if (cur == null || ((String) cur).isEmpty()) {
                        f.set(user, "SVIP");
                        changed++;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (changed > 0) log("patched user: " + changed + " vip fields");
        else {
            StringBuilder sb = new StringBuilder("user fields: ");
            for (Field f2 : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f2.getModifiers())) continue;
                sb.append(f2.getName()).append("(").append(f2.getType().getSimpleName()).append(") ");
            }
            log(sb.toString());
        }
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
