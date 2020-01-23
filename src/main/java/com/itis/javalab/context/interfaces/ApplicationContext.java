package com.itis.javalab.context.interfaces;

public interface ApplicationContext {
    <T> T getComponent(String name);
}
