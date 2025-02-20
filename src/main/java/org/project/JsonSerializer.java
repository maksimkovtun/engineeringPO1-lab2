package org.project;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Retention(RetentionPolicy.RUNTIME)
@interface JsonField {
    String name() default "";
}

public class JsonSerializer {
    public static String serialize(Object obj) throws IllegalAccessException, JSONException {
        if (obj == null) {
            return "null";
        }
        Class<?> clazz = obj.getClass();
        JSONObject jsonObject = new JSONObject();
        Map<Object, Integer> objectMap = new HashMap<>(); 
        serializeObject(obj, jsonObject, objectMap);
        return jsonObject.toString();
    }
    private static void serializeObject(Object obj, JSONObject jsonObject, Map<Object, Integer> objectMap) throws IllegalAccessException, JSONException {
        if (obj == null) {
            return;
        }
        Class<?> clazz = obj.getClass();
        if (objectMap.containsKey(obj)) {
            jsonObject.put("$ref", objectMap.get(obj));
            return;
        } else {
            objectMap.put(obj, objectMap.size());
            jsonObject.put("$id", objectMap.size() - 1);
        }
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(JsonField.class)) {
                JsonField annotation = field.getAnnotation(JsonField.class);
                String key = annotation.name().isEmpty() ? field.getName() : annotation.name();
                Object value = field.get(obj);

                if (value == null) {
                    jsonObject.put(key, JSONObject.NULL);
                } else if (value.getClass().isArray()) {
                    JSONArray jsonArray = new JSONArray();
                    for (int i = 0; i < Array.getLength(value); i++) {
                        Object element = Array.get(value, i);
                        JSONObject elementJson = new JSONObject();
                        serializeObject(element, elementJson, objectMap);
                        jsonArray.put(elementJson);
                    }
                    jsonObject.put(key, jsonArray);
                } else if (value.getClass().isPrimitive() || value instanceof String || value instanceof Number || value instanceof Boolean) {
                    jsonObject.put(key, value.toString());
                } else {
                    JSONObject nestedObject = new JSONObject();
                    serializeObject(value, nestedObject, objectMap);
                    jsonObject.put(key, nestedObject);
                }
            }
        }
    }
    public static <T> T deserialize(String json, Class<T> clazz) throws Exception {
        JSONObject jsonObject = new JSONObject(json);
        Map<Integer, Object> objectMap = new HashMap<>();
        return deserializeObject(jsonObject, clazz, objectMap);
    }
    private static <T> T deserializeObject(JSONObject jsonObject, Class<T> clazz, Map<Integer, Object> objectMap) throws Exception {
        if (jsonObject == null || jsonObject.has("$ref")) {
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
                            JSONObject elementJson = jsonArray.optJSONObject(i);
                            if (elementJson != null) {
                                Object element = deserializeObject(elementJson, field.getType().getComponentType(), objectMap);
                                Array.set(array, i, element);
                            }
                        }
                        field.set(instance, array);
                    } else if (field.getType().isPrimitive()) {
                        if (field.getType().equals(int.class)) {
                            field.set(instance, jsonObject.getInt(key));
                        } else if (field.getType().equals(double.class)) {
                            field.set(instance, jsonObject.getDouble(key));
                        } else if (field.getType().equals(boolean.class)) {
                            field.set(instance, jsonObject.getBoolean(key));
                        } else if (field.getType().equals(long.class)) {
                            field.set(instance, jsonObject.getLong(key));
                        } else if (field.getType().equals(float.class)) {
                            field.set(instance, (float) jsonObject.getDouble(key));
                        } else if (field.getType().equals(short.class)) {
                            field.set(instance, (short) jsonObject.getInt(key));
                        } else if (field.getType().equals(byte.class)) {
                            field.set(instance, (byte) jsonObject.getInt(key));
                        } else {
                            throw new IllegalArgumentException("Unsupported primitive type: " + field.getType());
                        }
                    } else if (field.getType().equals(String.class)) {
                        field.set(instance, jsonObject.getString(key));
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
    public static class TestClass {
        @JsonField
        private String name;
        @JsonField(name = "age")
        private int age;
        @JsonField
        private TestClass[] friends;
        public TestClass(String name, int age, TestClass[] friends) {
            this.name = name;
            this.age = age;
            this.friends = friends;
        }
        public TestClass() {}
        @Override
        public String toString() {
            return "TestClass{name='" + name + '\'' + ", age=" + age + '}';
        }
    }
    public static void main(String[] args) throws Exception {
        TestClass friend1 = new TestClass("Alice", 25, null);
        TestClass friend2 = new TestClass("Bob", 30, null);
        TestClass person = new TestClass("Charlie", 35, new TestClass[]{friend1, friend2});
        String json = serialize(person);
        System.out.println("Serialized JSON: " + json);
        TestClass deserializedPerson = deserialize(json, TestClass.class);
        System.out.println("Deserialized Object: " + deserializedPerson);
    }
}
