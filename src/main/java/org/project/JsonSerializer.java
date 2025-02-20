package org.project;

import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Retention(RetentionPolicy.RUNTIME)
@interface JsonField {
    String name() default "";
}

public class JsonSerializer {
    public static JSONObject serializeObject(Object obj, Map<Object, Integer> objectMap, AtomicInteger idGenerator) throws Exception {
        if (obj == null) {
            return null;
        }
        if (objectMap.containsKey(obj)) {
            JSONObject refObject = new JSONObject();
            refObject.put("$ref", objectMap.get(obj));
            return refObject;
        }
        JSONObject jsonObject = new JSONObject();
        int id = idGenerator.getAndIncrement();
        objectMap.put(obj, id);
        jsonObject.put("$id", id);
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(JsonField.class)) {
                JsonField annotation = field.getAnnotation(JsonField.class);
                String key = annotation.name().isEmpty() ? field.getName() : annotation.name();
                Object value = field.get(obj);
                if (value == null) {
                    jsonObject.put(key, JSONObject.NULL);
                } else if (field.getType().isArray()) {
                    JSONArray jsonArray = new JSONArray();
                    int length = Array.getLength(value);
                    for (int i = 0; i < length; i++) {
                        Object element = Array.get(value, i);
                        jsonArray.put(serializeObject(element, objectMap, idGenerator));
                    }
                    jsonObject.put(key, jsonArray);
                } else if (field.getType().isPrimitive() || value instanceof String) {
                    jsonObject.put(key, value);
                } else {
                    jsonObject.put(key, serializeObject(value, objectMap, idGenerator));
                }
            }
        }
        return jsonObject;
    }
    public static <T> T deserializeObject(JSONObject jsonObject, Class<T> clazz, Map<Integer, Object> objectMap) throws Exception {
        if (jsonObject.has("$ref")) {
            int refId = jsonObject.getInt("$ref");
            return (T) objectMap.get(refId);
        }
        T instance = clazz.getDeclaredConstructor().newInstance();
        if (jsonObject.has("$id")) {
            int id = jsonObject.getInt("$id");
            objectMap.put(id, instance);
        }
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(JsonField.class)) {
                JsonField annotation = field.getAnnotation(JsonField.class);
                String key = annotation.name().isEmpty() ? field.getName() : annotation.name();
                if (jsonObject.has(key) && !jsonObject.isNull(key)) {
                    if (field.getType().isArray()) {
                        JSONArray jsonArray = jsonObject.getJSONArray(key);
                        Object array = Array.newInstance(field.getType().getComponentType(), jsonArray.length());
                        for (int i = 0; i < jsonArray.length(); i++) {
                            if (jsonArray.get(i) instanceof JSONObject) {
                                JSONObject elementJson = jsonArray.getJSONObject(i);
                                Object element = deserializeObject(elementJson, field.getType().getComponentType(), objectMap);
                                Array.set(array, i, element);
                            }
                        }
                        field.set(instance, array);
                    } else if (field.getType().isPrimitive() || field.getType().equals(String.class)) {
                        field.set(instance, jsonObject.get(key));
                    } else {
                        JSONObject nestedObject = jsonObject.getJSONObject(key);
                        Object nestedValue = deserializeObject(nestedObject, field.getType(), objectMap);
                        field.set(instance, nestedValue);
                    }
                } else {
                    field.set(instance, null);
                }
            }
        }

        return instance;
    }
    public static class Person {
        @JsonField
        private String name;
        @JsonField
        private int age;
        @JsonField
        private Person[] friends;
        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
        public Person(String name, int age, Person[] friends) {
            this.name = name;
            this.age = age;
            this.friends = friends;
        }
        public Person() {}
        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + '}';
        }
    }

    public static void main(String[] args) throws Exception {
        Person friend1 = new Person("Alice", 25);
        Person friend2 = new Person("Bob", 30);
        Person person = new Person("John", 20, new Person[]{friend1, friend2, friend1});
        Map<Object, Integer> objectMap = new HashMap<>();
        AtomicInteger idGenerator = new AtomicInteger(1);
        JSONObject serializedPerson = serializeObject(person, objectMap, idGenerator);
        System.out.println("Сериализованный JSON:");
        System.out.println(serializedPerson.toString(4));
        Map<Integer, Object> objectMapDeserialization = new HashMap<>();
        Person deserializedPerson = deserializeObject(serializedPerson, Person.class, objectMapDeserialization);
        System.out.println("\nДесериализованный объект:");
        System.out.println(deserializedPerson);
    }
}
