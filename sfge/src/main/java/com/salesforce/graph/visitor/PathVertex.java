package com.salesforce.graph.visitor;

import com.salesforce.graph.ApexPath;
import com.salesforce.graph.DeepCloneable;
import com.salesforce.graph.ops.expander.PathExpansionRegistry;
import com.salesforce.graph.vertex.BaseSFVertex;
import java.util.Objects;

/**
 * Represents a {@link ApexPath}/{@link BaseSFVertex} pair that uniquely identifies the
 * relationship. A given vertex may be visited more than once, but it will always correspond to a
 * unique ApexPath.
 */
public final class PathVertex implements DeepCloneable<PathVertex> {

    public static PathVertex getInstance(ApexPath path, BaseSFVertex vertex, PathExpansionRegistry registry) {

        final int pathVertexId = PathVertex.getPathVertexId(path, vertex);
        PathVertex pathVertex;

        // First check if it's already available in the registry
        pathVertex = registry.lookupPathVertex(Long.valueOf(pathVertexId));
        if (pathVertex == null) {
            pathVertex = new PathVertex(path, vertex, registry);
        }

        return pathVertex;
    }

    private static int getPathVertexId(ApexPath path, BaseSFVertex vertex) {
        return Objects.hash(path.getStableId(), vertex);
    }

    /** Represents Id of ApexPath **/
    private final Long stableId;
    private final BaseSFVertex vertex;
    private final int hash;

    private PathVertex(ApexPath path, BaseSFVertex vertex, PathExpansionRegistry registry) {
        this.stableId = path.getStableId();
        this.vertex = vertex;
        this.hash = getPathVertexId(path, vertex);

        // Add instance to registry
        registry.registerPathVertex(this);
    }

    public Long getId() {
        return Long.valueOf(hash);
    }

    public BaseSFVertex getVertex() {
        return vertex;
    }

    @Override
    public PathVertex deepClone() {
        // It's immutable reuse
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathVertex that = (PathVertex) o;
        return stableId.equals(that.stableId) && vertex.equals(that.vertex);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "PathVertex{" +
            "id=" + getId() +
            '}';
    }
}
