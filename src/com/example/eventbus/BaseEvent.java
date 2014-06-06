package com.example.eventbus;

import java.io.Serializable;

public abstract class BaseEvent<T extends Serializable> implements Serializable {
    T mData;

    public BaseEvent(final T data) {
        mData = data;
    }

    public T getData() {
        return mData;
    }
}
