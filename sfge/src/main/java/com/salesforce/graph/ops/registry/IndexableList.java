package com.salesforce.graph.ops.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IndexableList<T extends Indexable> {
    private final Registry registry;
    private final Class<T> itemClass;
    private final List<Long> internalList;

    public IndexableList(Registry registry, Class itemClass) {
        this.registry = registry;
        this.itemClass = itemClass;
        internalList = new ArrayList<>();
    }

    public boolean add(T item) {
        return internalList.add(item.getId());
    }

    public Indexable get(int i) {
        Long itemId = internalList.get(i);
        return registry.lookup(itemClass, itemId);
    }

    public List<T> getValues() {
        if (internalList.isEmpty()) {
            return new ArrayList<>();
        }

        return (List<T>) internalList.stream().map(id -> registry.lookup(itemClass, id)).collect(Collectors.toList());
    }

    public void clear() {
        internalList.clear();
    }

    public boolean contains(T item) {
        return internalList.contains(item.getId());
    }
}
