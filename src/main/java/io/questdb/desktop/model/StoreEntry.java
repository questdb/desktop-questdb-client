package io.questdb.desktop.model;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;


public class StoreEntry implements UniqueId<String>, Comparable<StoreEntry> {

    private static final Comparator<String> COMPARING = (k1, k2) -> {
        String[] k1Parts = k1.split("\\.");
        String[] k2Parts = k2.split("\\.");
        if (k1Parts.length != k2Parts.length) {
            return Integer.compare(k1Parts.length, k2Parts.length);
        } else if (2 == k1Parts.length) {
            if (Objects.equals(k1Parts[0], k2Parts[0])) {
                return k1Parts[1].compareTo(k2Parts[1]);
            }
        }
        return k1.compareTo(k2);
    };

    private final Map<String, String> attrs;
    private volatile String name;

    public StoreEntry(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        this.name = name;
        attrs = new TreeMap<>();
    }

    /**
     * Shallow copy constructor, attributes are a reference to the attributes of 'other'.
     * <p>
     * The {@link Store} uses this constructor to recycle the objects instantiated by the
     * JSON decoder, which produces instances of StoreItem that already contain an attribute
     * map. We do not need to instantiate yet another attribute map when we can recycle the
     * instance provided by the decoder.
     *
     * @param other store entry
     */
    public StoreEntry(StoreEntry other) {
        name = other.name;
        attrs = other.attrs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Attribute getter.
     *
     * @param attrName name of the attribute
     * @return the value associated with the attribute, or null if it does not exist
     */
    public String getAttr(String attrName) {
        return attrs.get(attrName);
    }

    /**
     * Attribute getter.
     *
     * @param attr an implementor of HasKey
     * @return the value associated with the attribute, or null if it does not exist
     */
    public String getAttr(UniqueId<String> attr) {
        return attrs.get(attr.getUniqueId());
    }

    /**
     * Attribute setter.
     * <p>
     * null values are stored as an empty string.
     *
     * @param attr  an implementor of HasKey
     * @param value value for the attribute
     */
    public void setAttr(UniqueId<String> attr, String value) {
        setAttr(attr, value, "");
    }

    public void setAttr(UniqueId<String> attr, String value, String defaultValue) {
        attrs.put(attr.getUniqueId(), null == value || value.isEmpty() ? defaultValue : value);
    }

    public void setAttr(String attrName, String value, String defaultValue) {
        attrs.put(attrName, value == null || value.isEmpty() ? defaultValue : value);
    }

    public void setAttr(String attrName, String value) {
        attrs.put(attrName, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof StoreEntry that) {
            return name.equals(that.name) && attrs.equals(that.attrs);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attrs);
    }

    @Override
    public int compareTo(@NotNull StoreEntry that) {
        return COMPARING.compare(getUniqueId(), that.getUniqueId());
    }

    @Override
    public String getUniqueId() {
        return String.format("%s.%s", name, attrs);
    }

    @Override
    public String toString() {
        return getUniqueId();
    }
}
