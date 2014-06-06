package com.example.eventbus;

import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventBus {

    static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(3, 8, 5, TimeUnit.MINUTES, new SynchronousQueue<Runnable>()) {
        {
            prestartCoreThread();
        }

        @Override
        public ThreadFactory getThreadFactory() {
            return new ThreadFactory() {

                private final AtomicInteger mThreadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(final Runnable r) {
                    return new Thread(Thread.currentThread().getThreadGroup(),
                            r, String.format("EventBus-%d", mThreadNumber.getAndIncrement()));
                }
            };
        }
    };

    static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
    static final Map<Class, ClassContext> CACHE_MAP = Collections.synchronizedMap(new HashMap<Class, ClassContext>());

    static final Map<Class, Pair<MethodContext, ArrayList<Object>>> OBJ_MAP = Collections.synchronizedMap(new HashMap<Class, Pair<MethodContext, ArrayList<Object>>>());
    static final Map<Object, ArrayList<Object>> OBJ_LIST_MAP = Collections.synchronizedMap(new HashMap<Object, ArrayList<Object>>());

    /**
     * 判断type是否是superType的子类型
     *
     * @param type       子类型
     * @param superClass 父类型
     * @return 假如有父子关系，或者子类型实现了父类型的接口，返回true，否则返回false
     */
    public static boolean isSubclassOf(Class<?> type, Class<?> superClass) {
        if (type == null) {
            return false;
        }
        if (type.equals(superClass)) {
            return true;
        }
        Class[] interfaces = type.getInterfaces();
        for (Class i : interfaces) {
            if (isSubclassOf(i, superClass)) {
                return true;
            }
        }
        Class superType = type.getSuperclass();
        return superType != null && isSubclassOf(superType, superClass);
    }

    public final void register(final Object o) {
        if (o == null) {
            return;
        }
        final Class<?> typeClz = o.getClass();
        ClassContext cc = CACHE_MAP.get(typeClz);
        if (cc == null) {
            cc = new ClassContext(typeClz);
            CACHE_MAP.put(typeClz, cc);
        }

        for (Method method : cc.mMethodList) {
            final Class<?> eventType = method.getParameterTypes()[0];
            Pair<MethodContext, ArrayList<Object>> pair = OBJ_MAP.get(eventType);
            if (pair == null) {
                pair = Pair.create(new MethodContext(method), new ArrayList<Object>());
                OBJ_LIST_MAP.put(o, pair.second);
                OBJ_MAP.put(eventType, pair);
            }
            pair.second.add(o);
        }

    }

    public final void unregister(final Object o) {
        final ArrayList<Object> list = OBJ_LIST_MAP.get(o);
        if (list == null || list.isEmpty()) {
            return;
        }
        list.remove(o);
        OBJ_LIST_MAP.remove(o);
        for (Pair<MethodContext, ArrayList<Object>> pair : OBJ_MAP.values()) {
            if (pair.second != null) {
                if (pair.second.contains(o)) {
                    throw new Error("pair.second contains o");
                }
            }
        }
    }

    public final void post(final BaseEvent event) {
        if (event == null) {
            return;
        }
        final Class<?> clz = event.getClass();
        Pair<MethodContext, ArrayList<Object>> pair = OBJ_MAP.get(clz);
        if (pair == null || pair.second == null || pair.second.isEmpty()) {
            return;
        }
        for (Object o : pair.second) {
            pair.first.call(event, o);
        }
    }

    static class ClassContext {
        public ArrayList<Method> mMethodList = new ArrayList<Method>();

        public ClassContext(Class<?> clz) {
            while (clz != null) {
                for (Method method : clz.getDeclaredMethods()) {
                    final Event event = method.getAnnotation(Event.class);
                    if (event == null) {
                        continue;
                    }
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes == null || paramTypes.length != 1 || isSubclassOf(paramTypes[0], BaseEvent.class)) {
                        continue;
                    }
                    mMethodList.add(method);
                }
                clz = clz.getSuperclass();
            }
        }
    }

    static class MethodContext {
        private final Method mMethod;
        private final Event mEvent;

        MethodContext(final Method method) {
            mMethod = method;
            mEvent = method.getAnnotation(Event.class);
            mMethod.setAccessible(true);
        }

        public void call(final BaseEvent event, final Object obj) {
            switch (mEvent.runOn()) {
                case SOURCE:
                    $call(event, obj);
                    break;
                case MAIN:
                    UI_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            $call(event, obj);
                        }
                    });
                    break;
                case BACKGROUND:
                    THREAD_POOL_EXECUTOR.submit(new Runnable() {
                        @Override
                        public void run() {
                            $call(event, obj);
                        }
                    });
                    break;
            }
        }

        private void $call(final BaseEvent event, final Object obj) {
            if (obj == null) {
                return;
            }
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(os);
                oos.writeObject(event);
                oos.flush();
                InputStream is = new ByteArrayInputStream(os.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(is);
                Object o = ois.readObject();

                mMethod.invoke(obj, o);

                os.close();
                oos.close();

                ois.close();
                is.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
