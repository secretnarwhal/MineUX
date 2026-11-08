package com.mineux.core.gpu;

import java.util.function.Supplier;

/** A fixed debug label for something made on the GPU. */
final class Label implements Supplier<String> {

    private final String text;

    Label(String text) {
        this.text = text;
    }

    @Override
    public String get() {
        return text;
    }
}
