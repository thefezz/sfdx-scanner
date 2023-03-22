package com.salesforce.rules.unusedmethod;

import com.salesforce.apex.jorje.ASTConstants.NodeType;
import com.salesforce.collections.CollectionUtil;
import com.salesforce.graph.Schema;
import com.salesforce.graph.build.CaseSafePropertyUtil.H;
import com.salesforce.graph.vertex.*;
import java.util.List;
import java.util.Map;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

public class RuleQueryExecutor {
    private final GraphTraversalSource g;
    /**
     * A map of {@link MethodCallExpressionVertex} lists by the DefiningType in which they occur.
     */
    private final Map<String, List<MethodCallExpressionVertex>> methodCallsByContainingDefType;
    /**
     * A map of {@link ThisMethodCallExpressionVertex} lists by the DefiningType in which they
     * occur.
     */
    private final Map<String, List<ThisMethodCallExpressionVertex>>
            thisMethodCallsByContainingDefType;
    /**
     * A map of {@link SuperMethodCallExpressionVertex} lists by the DefiningType in which they
     * occur.
     */
    private final Map<String, List<SuperMethodCallExpressionVertex>>
            superMethodCallsByContainingDefType;

    public RuleQueryExecutor(GraphTraversalSource g) {
        this.g = g;
        this.methodCallsByContainingDefType = CollectionUtil.newTreeMap();
        this.thisMethodCallsByContainingDefType = CollectionUtil.newTreeMap();
        this.superMethodCallsByContainingDefType = CollectionUtil.newTreeMap();
    }

    /**
     * Get every {@link MethodCallExpressionVertex} occurring in the class indicated by {@code
     * definingType}.<br>
     * Note: These vertices represent invocations of non-constructor methods.
     */
    List<MethodCallExpressionVertex> getMethodCallsOccurringIn(String definingType) {
        return getInvocableByDefiningType(
                methodCallsByContainingDefType, NodeType.METHOD_CALL_EXPRESSION, definingType);
    }

    /**
     * Get every {@link ThisMethodCallExpressionVertex} occurring in the class indicated by {@code
     * definingType}.<br>
     * Note: These vertices represent invocations of {@code this()}-style constructors.
     */
    List<ThisMethodCallExpressionVertex> getThisMethodCallsOccurringIn(String definingType) {
        return getInvocableByDefiningType(
                thisMethodCallsByContainingDefType,
                NodeType.THIS_METHOD_CALL_EXPRESSION,
                definingType);
    }

    /**
     * Get every {@link SuperMethodCallExpressionVertex} occurring in the class indicated by {@code
     * definingType}.<br>
     * Note: These vertices represent invocations of {@code super()}-style constructors.
     */
    List<SuperMethodCallExpressionVertex> getSuperMethodCallsOccurringIn(String definingType) {
        return getInvocableByDefiningType(
                superMethodCallsByContainingDefType,
                NodeType.SUPER_METHOD_CALL_EXPRESSION,
                definingType);
    }

    /**
     * Helper method for populating the {@link #methodCallsByContainingDefType}, {@link
     * #thisMethodCallsByContainingDefType}, and {@link #superMethodCallsByContainingDefType} maps.
     */
    private <T extends BaseSFVertex> List<T> getInvocableByDefiningType(
            Map<String, List<T>> map, String nodeType, String definingType) {
        if (!map.containsKey(definingType)) {
            map.put(
                    definingType,
                    SFVertexFactory.loadVertices(
                            g, g.V().where(H.has(nodeType, Schema.DEFINING_TYPE, nodeType))));
        }
        return map.get(definingType);
    }
}
