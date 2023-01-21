package com.salesforce.graph.ops.expander;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helps translate between lists of instances and their Ids while looking up {@link
 * PathExpansionRegistry}.
 */
public final class PathExpansionRegistryUtil {
    private PathExpansionRegistryUtil() {}

    public static List<ApexPathExpander> convertIdsToApexPathExpanders(
            List<Long> apexPathExpanderIds) {
        if (apexPathExpanderIds.isEmpty()) {
            return new ArrayList<>();
        }
        return apexPathExpanderIds.stream()
                .map(id -> PathExpansionRegistry.lookupApexPathExpander(id))
                .collect(Collectors.toList());
    }

    public static List<Long> convertApexPathExpandersToIds(
            List<ApexPathExpander> apexPathExpanders) {
        if (apexPathExpanders.isEmpty()) {
            return new ArrayList<>();
        }
        return apexPathExpanders.stream()
                .map(apexPathExpander -> apexPathExpander.getId())
                .collect(Collectors.toList());
    }

    public static List<ForkEvent> convertIdsToForkEvents(List<Long> forkEventIds) {
        if (forkEventIds.isEmpty()) {
            return new ArrayList<>();
        }
        return forkEventIds.stream()
                .map(id -> PathExpansionRegistry.lookupForkEvent(id))
                .collect(Collectors.toList());
    }

    public static List<Long> convertForkEventsToIds(List<ForkEvent> forkEvents) {
        if (forkEvents.isEmpty()) {
            return new ArrayList<>();
        }
        return forkEvents.stream().map(forkEvent -> forkEvent.getId()).collect(Collectors.toList());
    }
}