package com.comagents.commons.communication;

public record ComagentsError(int code, String errorMessage, String errorClassName) {

    public ComagentsError(Throwable throwable) {
        this(0, throwable.getMessage(), throwable.getClass().getName());
    }
}
