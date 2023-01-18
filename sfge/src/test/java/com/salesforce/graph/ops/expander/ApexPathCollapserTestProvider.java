package com.salesforce.graph.ops.expander;

public class ApexPathCollapserTestProvider {
    public static void initializeForTest() {
        if (ApexPathCollapserProvider.PATH_COLLAPSERS != null) {
            ApexPathCollapserProvider.PATH_COLLAPSERS.remove();
        }
    }

    public static void cleanup() {
        ApexPathCollapserProvider.PATH_COLLAPSERS.remove();
    }
}
