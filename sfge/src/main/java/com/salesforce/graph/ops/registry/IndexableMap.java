package com.salesforce.graph.ops.registry;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IndexableMap<K extends Indexable, V extends Indexable> {
    private final Class<K> keyClass;
    private final Class<V> valueClass;
    private final Registry registry;

    private final Map<Long, Long> internalMap;

    public IndexableMap(Class<K> keyClass, Class<V> valueClass, Registry registry) {
        super();
        this.keyClass = keyClass;
        this.valueClass = valueClass;
        this.registry = registry;
        this.internalMap = new HashMap<>();
    }

    public V get(K key) {
        Long valueId = internalMap.get(key.getId());
        return (V) registry.lookup(valueClass, valueId);
    }

    public Long put(K key, V value) {
        return internalMap.put(key.getId(), value.getId());
    }

    public boolean containsKey(K key) {
        return internalMap.containsKey(key.getId());
    }

    public Collection<Indexable> values() {
        final Collection<Long> valueIds = internalMap.values();
        if (valueIds.isEmpty()) {
            return new ArrayList<>();
        }
        return valueIds.stream()
            .map(id -> registry.lookup(valueClass, id))
            .collect(Collectors.toList());
    }

    public Set<Indexable> getKeys() {
        final Set<Long> keyIds = internalMap.keySet();
        if (((Collection<Long>) keyIds).isEmpty()) {
            return new HashSet<>();
        }
        return keyIds.stream()
            .map(id -> registry.lookup(keyClass, id))
            .collect(Collectors.toSet());
    }

}
