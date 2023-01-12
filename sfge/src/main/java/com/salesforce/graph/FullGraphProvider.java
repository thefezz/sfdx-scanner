package com.salesforce.graph;

import com.salesforce.graph.ops.GraphUtil;
import java.util.List;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

public final class FullGraphProvider {
    private static final ThreadLocal<GraphTraversalSource> FULL_GRAPH_INSTANCES =
            ThreadLocal.withInitial(() -> getFullGraphInstance());

    public static GraphTraversalSource get() {
        return FULL_GRAPH_INSTANCES.get();
    }

    public static void load(List<String> projectDirectories) throws GraphUtil.GraphLoadException {
        GraphUtil.loadSourceFolders(FULL_GRAPH_INSTANCES.get(), projectDirectories);
    }

    private FullGraphProvider() {}

    private static GraphTraversalSource getFullGraphInstance() {
        return LazyHolder.FULL_GRAPH_INSTANCE;
    }

    private static final class LazyHolder {
        private static final GraphTraversalSource FULL_GRAPH_INSTANCE = GraphUtil.getGraph();
    }
}
