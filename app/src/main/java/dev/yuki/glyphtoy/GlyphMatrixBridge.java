package dev.yuki.glyphtoy;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class GlyphMatrixBridge {
    interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String message, Throwable throwable);
    }

    private static final String TAG = "YukiGlyphBridge";
    private static final String DEVICE_PHONE_4A_PRO_FALLBACK = "25111p";
    private static final String[] MANAGER_CLASS_NAMES = {
            "com.nothing.ketchum.GlyphMatrixManager",
            "com.nothing.glyphmatrix.GlyphMatrixManager"
    };
    private static final String[] GLYPH_CLASS_NAMES = {
            "com.nothing.ketchum.Glyph",
            "com.nothing.glyphmatrix.Glyph"
    };

    private final Context context;
    private Object manager;
    private Object callback;
    private Class<?> managerClass;

    GlyphMatrixBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    void init(Listener listener) {
        try {
            managerClass = findClass(MANAGER_CLASS_NAMES);
            Method getInstance = managerClass.getMethod("getInstance", Context.class);
            manager = getInstance.invoke(null, context);
            Class<?> callbackClass = findNestedClass(managerClass, "Callback");
            callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    new CallbackHandler(listener)
            );
            managerClass.getMethod("init", callbackClass).invoke(manager, callback);
        } catch (Throwable throwable) {
            listener.onError("Glyph Matrix SDK を初期化できません。app/libs に公式 AAR を入れてください。", throwable);
        }
    }

    void registerPhone4aPro() {
        invoke("register", new Class<?>[]{String.class}, devicePhone4aPro());
    }

    void setToyFrame(int[] frame) {
        invoke("setMatrixFrame", new Class<?>[]{int[].class}, frame);
    }

    void setAppFrame(int[] frame) {
        if (!invoke("setAppMatrixFrame", new Class<?>[]{int[].class}, frame)) {
            setToyFrame(frame);
        }
    }

    void closeAppMatrix() {
        invoke("closeAppMatrix", new Class<?>[0]);
    }

    void turnOff() {
        invoke("turnOff", new Class<?>[0]);
    }

    void unInit() {
        invoke("unInit", new Class<?>[0]);
        manager = null;
        callback = null;
        managerClass = null;
    }

    private boolean invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (manager == null || managerClass == null) {
            return false;
        }
        try {
            Method method = managerClass.getMethod(methodName, parameterTypes);
            method.invoke(manager, args);
            return true;
        } catch (NoSuchMethodException missing) {
            Log.w(TAG, "SDK method is unavailable: " + methodName, missing);
            return false;
        } catch (Throwable throwable) {
            Log.e(TAG, "SDK method failed: " + methodName, throwable);
            return false;
        }
    }

    private String devicePhone4aPro() {
        for (String className : GLYPH_CLASS_NAMES) {
            try {
                Class<?> glyph = Class.forName(className);
                return String.valueOf(glyph.getField("DEVICE_25111p").get(null));
            } catch (Throwable ignored) {
                // Fall through to the next known package name or fallback literal.
            }
        }
        return DEVICE_PHONE_4A_PRO_FALLBACK;
    }

    private static Class<?> findClass(String[] names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException exception) {
                last = exception;
            }
        }
        throw last;
    }

    private static Class<?> findNestedClass(Class<?> parent, String simpleName) throws ClassNotFoundException {
        for (Class<?> candidate : parent.getClasses()) {
            if (simpleName.equals(candidate.getSimpleName())) {
                return candidate;
            }
        }
        throw new ClassNotFoundException(parent.getName() + "$" + simpleName);
    }

    private static final class CallbackHandler implements InvocationHandler {
        private final Listener listener;

        private CallbackHandler(Listener listener) {
            this.listener = listener;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) {
                    return "Yuki Glyph Matrix callback";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return proxy == args[0];
                }
            }
            if ("onServiceConnected".equals(name)) {
                if (args != null && args.length > 0 && args[0] instanceof ComponentName) {
                    Log.i(TAG, "Connected to " + args[0]);
                }
                listener.onConnected();
            } else if ("onServiceDisconnected".equals(name)) {
                listener.onDisconnected();
            }
            return null;
        }
    }
}
