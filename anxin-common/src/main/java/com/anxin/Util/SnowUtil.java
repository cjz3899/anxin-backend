package com.anxin.Util;

/**
 * 雪花算法 ID 生成器。
 *
 * 单机默认 workerId=1、datacenterId=0；多实例部署时需改为从配置注入，避免 ID 冲突。
 * 生成的是 19 位 long，返回给前端时记得序列化为字符串（防 JS 精度丢失）。
 */
public class SnowUtil {

    /** 起始时间戳（2021-01-01 00:00:00），可自定义 */
    private static final long START_TIMESTAMP = 1609459200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long WORKER_ID = 1L;
    private static final long DATACENTER_ID = 0L;

    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    private SnowUtil() {
    }

    /**
     * 生成下一个雪花 ID。
     *
     * @return 雪花 long
     * @throws RuntimeException 时钟回拨时抛出
     */
    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成ID，timestamp=" + timestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 同一毫秒内序列号用尽，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (DATACENTER_ID << DATACENTER_ID_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
    }

    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
