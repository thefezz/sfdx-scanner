package com.salesforce.graph.vertex;

import com.salesforce.graph.symbols.SymbolProvider;
import com.salesforce.graph.symbols.SymbolProviderVertexVisitor;
import com.salesforce.graph.visitor.PathVertexVisitor;
import java.util.Map;

/**
 * This vertex represents a {@code SuperVariableExpression} in the AST. It typically occurs inside a
 * {@link ReferenceExpressionVertex} when {@code super.someMethod()} is invoked.
 */
public class SuperVariableExpressionVertex extends TODO_FIX_HIERARCHY_ChainedVertex
        implements Typeable {
    SuperVariableExpressionVertex(Map<Object, Object> properties) {
        this(properties, null);
    }

    SuperVariableExpressionVertex(Map<Object, Object> properties, Object supplementalParam) {
        super(properties, supplementalParam);
    }

    @Override
    public boolean visit(PathVertexVisitor visitor, SymbolProvider symbols) {
        return visitor.visit(this, symbols);
    }

    @Override
    public boolean visit(SymbolProviderVertexVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void afterVisit(PathVertexVisitor visitor, SymbolProvider symbols) {
        visitor.afterVisit(this, symbols);
    }

    @Override
    public void afterVisit(SymbolProviderVertexVisitor visitor) {
        visitor.afterVisit(this);
    }

    @Override
    public String getCanonicalType() {
        return getDefiningType();
    }
}
