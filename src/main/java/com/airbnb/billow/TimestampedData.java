package com.airbnb.billow;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Data
@Slf4j
public class TimestampedData<T> {
    private final T data;
    private final long timestamp;

    public static <T> TimestampedData<T> withTimestamp(Supplier<T> supplier) {
        long timestamp = System.currentTimeMillis();
        return new TimestampedData<T>(supplier.get(), timestamp);
    }
}