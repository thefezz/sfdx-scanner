package com.salesforce.graph.ops.expander;

import com.salesforce.graph.ops.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helps translate between lists of instances and their Ids while looking up {@link
 * Registry}.
 */
public final class PathExpansionRegistryUtil {
    private PathExpansionRegistryUtil() {}

    public static void registerPathCollapser(PathExpansionRegistry registry, ApexPathCollapser pathCollapser) {
        registry.register(ApexPathCollapser.class, pathCollapser);
    }

    public static List<ApexPathExpander> convertIdsToApexPathExpanders(
        Registry registry, List<Long> apexPathExpanderIds) {
        if (apexPathExpanderIds.isEmpty()) {
            return new ArrayList<>();
        }
        return apexPathExpanderIds.stream()
                .map(id -> registry.lookupApexPathExpander(id))
                .collect(Collectors.toList());
    }

    public static List<Long> convertApexPathExpandersToIds(
        Registry registry, List<ApexPathExpander> apexPathExpanders) {
        if (apexPathExpanders.isEmpty()) {
            return new ArrayList<>();
        }
        return apexPathExpanders.stream()
                .map(
                        apexPathExpander -> {
                            registry.validateApexPathExpander(apexPathExpander);
                            return apexPathExpander.getId();
                        })
                .collect(Collectors.toList());
    }

    public static List<ForkEvent> convertIdsToForkEvents(
        Registry registry, List<Long> forkEventIds) {
        if (forkEventIds.isEmpty()) {
            return new ArrayList<>();
        }
        return forkEventIds.stream()
                .map(id -> registry.lookupForkEvent(id))
                .collect(Collectors.toList());
    }

    public static List<Long> convertForkEventsToIds(
        Registry registry, List<ForkEvent> forkEvents) {
        if (forkEvents.isEmpty()) {
            return new ArrayList<>();
        }
        return forkEvents.stream()
                .map(
                        forkEvent -> {
                            registry.validateForkEvent(forkEvent);
                            return forkEvent.getId();
                        })
                .collect(Collectors.toList());
    }
}
