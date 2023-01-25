package com.salesforce.graph.ops.expander;

import com.google.common.collect.ImmutableMap;
import com.salesforce.graph.ops.registry.AbstractRegistryData;
import com.salesforce.graph.ops.registry.Registry;

import java.util.Map;
import java.util.function.Supplier;

public class PathExpansionRegistry extends Registry {

    private static final Map<Class, Supplier> REGISTRY_SUPPLIER = ImmutableMap.of(
        ApexPathCollapser.class, () -> new PathCollapserRegistryData(),
        ForkEvent.class, () -> new ForkEventRegistryData(),
        ApexPathExpander.class, () -> new ApexPathExpanderRegistryData()
    );

    @Override
    protected Map<Class, Supplier> getRegistrySupplier() {
        return REGISTRY_SUPPLIER;
    }

//    static class ApexPathCollapser {
//        public static void register(PathExpansionRegistry registry, Indexable indexable) {
//            registry
//                .registryHolderMap.get(ApexPathCollapser.class)
//                .validateAndPut(indexable);
//        }
//
//        public static Indexable lookup(PathExpansionRegistry registry, Long id) {
//            return registry
//                .registryHolderMap.get(ApexPathCollapser.class).get(indexableClass).get(id);
//        }
//
//        public Indexable deregister(PathExpansionRegistry registry, Long id) {
//            return registry
//                .registryHolderMap.get(ApexPathCollapser.class).remove(id);
//        }
//
//        public void validate(PathExpansionRegistry registry, Indexable indexableInstance) {
//            registry
//                .registryHolderMap.get(ApexPathCollapser.class).validate(indexableInstance);
//        }
//    }


    private static class PathCollapserRegistryData extends AbstractRegistryData<ApexPathCollapser> {
        // Nothing new to add
    }

    private static class ForkEventRegistryData extends AbstractRegistryData<ForkEvent> {
        // Nothing new to add
    }

    private static class ApexPathExpanderRegistryData extends AbstractRegistryData<ApexPathExpander> {
        // Nothing new to add
    }
}
