package com.itis.javalab.context;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.itis.javalab.context.annotations.Autowired;
import com.itis.javalab.context.annotations.Component;
import com.itis.javalab.context.annotations.Controller;
import com.itis.javalab.context.interfaces.ApplicationContext;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public class ApplicationContextReflectionBasedSecondImpl implements ApplicationContext {
    private Map<String, Object> objects = new HashMap();
    private Reflections reflections;
    private Map<String, Class<?>> exsComponents = new HashMap<>();
    private Map<Class<?>, Class<?>> dictionary = new HashMap<>();

    public ApplicationContextReflectionBasedSecondImpl(String[] properties) {
        initBaseConnection(properties);
        reflections = new Reflections(properties[4]);
        objects.put("reflections",reflections);
        Set<Class<?>> components = reflections.getTypesAnnotatedWith(Component.class);
        for (Class current : components) {
            String[] names = current.getName().split("\\.");
            exsComponents.put(names[names.length - 1], current);
        }
        List<Class<?>> classes;
        Class mainDao = null;
        Class controller = null;
        try {
            mainDao = Class.forName(properties[5]);
            controller = Class.forName(properties[6]);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        for (Class current : exsComponents.values()) {
            classes = new ArrayList<>();
            Set<Class<?>> currentClasses = reflections.getSubTypesOf(current);
            if (currentClasses.size() != 0) {
                if (currentClasses.size() >= 2) {
                    if (!current.equals(mainDao)) {
                        if (current.equals(controller)) {
                            continue;
                        }
                        throw new IllegalStateException("Найдено несколько релизаций для компонента");
                    }
                }
                classes.addAll(currentClasses);
                dictionary.put(current, classes.get(0));
            }
        }
    }

    private void loadControllers(Set<Class<?>> currentClasses) {
    }

    private void initBaseConnection(String[] properties) {
        try {
            Connection connection = DriverManager.getConnection(properties[0], properties[1], properties[2]);
            objects.put("connection", connection);
            objects.put("context",this);
            objects.put("mapper",new ObjectMapper());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <T> T getComponent(String name) {
        if (objects.get(name) == null) {
            try {
                loadComponent(name);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InstantiationException e) {
                throw new IllegalStateException(e);
            }
        }
        return (T) objects.get(name);
    }


    private void loadComponent(String name) throws IllegalAccessException, InstantiationException {
        Class component = exsComponents.get(name);
        if (component != null) {
            Class<?> current = null;
            if (component.isAnnotationPresent(Controller.class)) {
                current = component;
            } else {
                current = dictionary.get(component);
            }
            Object object = current.newInstance();
            object = inject(object);
            objects.put(name, object);
            return;
        }
        throw new IllegalStateException("Компонент не найден в доступном списке компонентов");
    }

    private Object inject(Object component) throws IllegalAccessException, InstantiationException {
        for (Field field : component.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                if (field.getType().equals(Connection.class)) {
                    boolean isAccessible = field.isAccessible();
                    field.setAccessible(true);
                    field.set(component, objects.get("connection"));
                    field.setAccessible(isAccessible);
                    continue;
                }
                if(field.getType().equals(ObjectMapper.class)){
                    boolean isAccessible = field.isAccessible();
                    field.setAccessible(true);
                    field.set(component, objects.get("mapper"));
                    field.setAccessible(isAccessible);
                    continue;
                }
                if(field.getType().equals(ApplicationContext.class)){
                    boolean isAccessible = field.isAccessible();
                    field.setAccessible(true);
                    field.set(component, objects.get("context"));
                    field.setAccessible(isAccessible);
                    continue;
                }
                if(field.getType().equals(Reflections.class)){
                    boolean isAccessible = field.isAccessible();
                    field.setAccessible(true);
                    field.set(component, objects.get("reflections"));
                    field.setAccessible(isAccessible);
                    continue;
                }
                for (Object dependency : objects.values()) {
                    Class<?> check = dictionary.get(field.getType());
                    if (dependency.getClass().equals(check)) {
                        boolean isAccessible = field.isAccessible();
                        field.setAccessible(true);
                        field.set(component, dependency);
                        field.setAccessible(isAccessible);
                        continue;
                    }
                }
                String name = getComponentName(field.getType().getName());
                loadComponent(name);
                Object object = objects.get(name);
                boolean isAccessible = field.isAccessible();
                field.setAccessible(true);
                field.set(component, object);
                field.setAccessible(isAccessible);
            }
        }
        return component;
    }

    private String getComponentName(String name) {
        String[] names = name.split("\\.");
        return names[names.length - 1];
    }
}
