package com.anxin.threadlocal;

public class BaseContext {

    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    private BaseContext() {
    }

    /** 设置当前登录用户 id（在拦截器验证 token 后调用） */
    public static void setCurrentId(Long id) {
        THREAD_LOCAL.set(id);
    }

    /** 获取当前登录用户 id */
    public static Long getCurrentId() {
        return THREAD_LOCAL.get();
    }

    /** 清除当前线程的用户信息（请求结束时必须在 finally/afterCompletion 调用） */
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
