package com.salesforce.graph.ops;

import com.salesforce.exception.UnexpectedException;
import com.salesforce.graph.vertex.*;
import com.salesforce.graph.visitor.TypedVertexVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import java.util.List;

public final class ParameterUtil {
    private static final Logger LOGGER = LogManager.getLogger(ParameterUtil.class);

    public static boolean parameterTypesMatch(GraphTraversalSource g, MethodVertex method, InvocableWithParametersVertex invocable) {
        // For each pair of parameters...
        for (int i = 0; i< method.getParameters().size(); i++) {
            ParameterVertex expectedParameter = method.getParameters().get(i);
            ChainedVertex actualParameter = invocable.getParameters().get(i);
            // If this pair doesn't match, the match as a whole is invalid.
            if (!parameterTypesMatch(g, expectedParameter, actualParameter)) {
                return false;
            }
        }
        // If all parameter pairs matched, we can return true.
        return true;
    }

    private static boolean parameterTypesMatch(GraphTraversalSource g, ParameterVertex expectedParameter, ChainedVertex providedArgument) {
        ArgumentDeclarationFinder declarationFinder = new ArgumentDeclarationFinder();
        String argumentType = getType(providedArgument.accept(new ArgumentDeclarationFinder()));
        return false;
    }

    /**
     * Finds the vertex that represents the declaration of a value being provided
     * as the argument for a method call.
     * In the absence of a {@link com.salesforce.graph.symbols.SymbolProvider}, this
     * is an inexact science, but we'll do our best.
     * If there were an interface that all possible options are guaranteed to extend,
     * we'd return that. As is, our only option is to return {@link BaseSFVertex} objects.
     */
    private static class ArgumentDeclarationFinder extends TypedVertexVisitor.DefaultThrow<BaseSFVertex> {

        @Override
        public BaseSFVertex visit(CastExpressionVertex vertex) {
            // A CastExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(ClassRefExpressionVertex vertex) {
            // A ClassRefExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(FieldDeclarationVertex vertex) {
            // A FieldDeclarationVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(FieldVertex vertex) {
            // A FieldVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(LiteralExpressionVertex vertex) {
            // A LiteralExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(MethodCallExpressionVertex vertex) {
            // How we proceed depends on the nature of the enclosed
            // AbstractReferenceExpressionVertex.
            AbstractReferenceExpressionVertex arev = vertex.getReferenceExpression();
            return arev.accept(new MethodDeclarationFinder(vertex));
        }

        @Override
        public BaseSFVertex visit(NewListInitExpressionVertex vertex) {
            // A NewListInitExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(NewListLiteralExpressionVertex vertex) {
            // A NewListLiteralExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(NewMapInitExpressionVertex vertex) {
            // A NewMapInitExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(NewMapLiteralExpressionVertex vertex) {
            // A NewMapLiteralExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(NewSetInitExpressionVertex vertex) {
            // A NewSetInitExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(NewSetLiteralExpressionVertex vertex) {
            // A NewSetLiteralExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(NewKeyValueObjectExpressionVertex vertex) {
            // A NewKeyValueObjectExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(ParameterVertex vertex) {
            // A ParameterVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(PostfixExpressionVertex vertex) {
            // A PostfixExpressionVertex simply encloses another vertex.
            // Unwrap it and keep going.
            return vertex.getOnlyChild().accept(this);
        }

        @Override
        public BaseSFVertex visit(PrefixExpressionVertex vertex) {
            // A PrefixExpressionVertex simply encloses another vertex.
            // Unwrap it and keep going.
            return vertex.getOnlyChild().accept(this);
        }

        @Override
        public BaseSFVertex visit(SoqlExpressionVertex vertex) {
            // A SoqlExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(TernaryExpressionVertex vertex) {
            // A TernaryExpressionVertex has `true` and `false` arms.
            // At this time, it is believed that we only need to examine
            // one arm, and that which arm we pick is irrelevant.
            // So arbitrarily pick the `true` arm, and keep going.
            return vertex.getTrueValue().accept(this);
        }

        @Override
        public BaseSFVertex visit(ThisVariableExpressionVertex vertex) {
            // A ThisVariableExpressionVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(VariableDeclarationVertex vertex) {
            // A VariableDeclarationVertex is a Typeable, so we can just return it.
            return vertex;
        }

        @Override
        public BaseSFVertex visit(VariableExpressionVertex.Single vertex) {
            // How we proceed depends on the nature of the enclosed
            // AbstractReferenceExpressionVertex
            AbstractReferenceExpressionVertex arev = vertex.getReferenceExpression();
            return vertex;
        }

        @Override
        public BaseSFVertex visit(VariableExpressionVertex.Standard vertex) {
            // A VariableExpressionVertex.Standard is a Typeable, so we can just return it.
            return vertex;
        }
    }

    private static class MethodDeclarationFinder extends TypedVertexVisitor.DefaultThrow<MethodVertex> {
        private final MethodCallExpressionVertex invokedMethod;

        private MethodDeclarationFinder(MethodCallExpressionVertex invokedMethod) {
            this.invokedMethod = invokedMethod;
        }

        @Override
        public MethodVertex visit(EmptyReferenceExpressionVertex vertex) {
            // An EmptyReferenceExpressionVertex means the method isn't being called as a
            // property of anything.
            // (i.e., it's called as `someMethod()` instead of `whatever.someMethod()`.)
            // As such, the method is defined in a scope that is accessible to the
            // current class, and outer class enclosure is a viable option.
            String hostClass = invokedMethod.getDefiningType();
            String methodName = invokedMethod.getMethodName();
            return getMethodDeclaration(hostClass, methodName, true);
        }

        @Override
        public MethodVertex visit(ReferenceExpressionVertex vertex) {
            // A ReferenceExpressionVertex means that the method is being invoked
            // as a property of something specific.
            // (i.e., `something.method()`.)
            // We need to figure out what that thing's type is, and then we can
            // try to find the declaration of the desired method.
            BaseSFVertex hostDeclaration = followReferenceChain(vertex);
            // If we didn't turn up a declaration for the thing being referenced,
            // we're stuck, so we should just return null.
            if (hostDeclaration == null) {
                return null;
            }
            // Otherwise, we should get the type information from the reference,
            // and look for the method declaration.
            // In this case, outer class enclosure is not a viable option.
            String hostClass = getType(hostDeclaration);
            String methodName = invokedMethod.getMethodName();
            return getMethodDeclaration(hostClass, methodName, false);
        }

        private MethodVertex getMethodDeclaration(String className, String methodName, boolean checkOuterClass) {
            // Once we have both a class name and a method name, there are at most three options.
            // Evaluate them in this order, to avoid issues with conflicting names.
            // 1. The method is declared on the class itself.
            MethodVertex declaration = null;
            // 2. The method is declared on a type from which the class inherits.
            if (declaration == null) {
                // TODO: HANDLE THIS CASE.
            }
            // 3. The method is a static method declared on an outer class that
            //    encloses the target class. This is not always a valid option,
            //    hence the additional check.
            if (declaration == null && checkOuterClass) {
                // TODO: HANDLE THIS CASE.
            }
            // At this point, we've done everything we can. Either we found
            // our target or we didn't.
            return declaration;
        }
    }

    private static class VariableDeclarationFinder extends TypedVertexVisitor.DefaultThrow<Typeable> {
        private final VariableExpressionVertex referencedVariable;

        private VariableDeclarationFinder(VariableExpressionVertex referencedVariable) {
            this.referencedVariable = referencedVariable;
        }


        @Override
        public Typeable visit(EmptyReferenceExpressionVertex vertex) {
            // An EmptyReferenceExpression means that this variable isn't being referenced as
            // a property of anything.
            // (i.e., it's referenced as `x` instead of `whatever.x`.)
            // This means that its declaration might be outside of our usual scope.
            String hostClass = vertex.getDefiningType();
            String propertyName = referencedVariable.getName();
            return getVariableDeclaration(hostClass, propertyName, true);
        }


        @Override
        public Typeable visit(ReferenceExpressionVertex vertex) {
            return null;
        }

        private Typeable getVariableDeclaration(String className, String propertyName, boolean useBroadedScope) {
            // Once we have both a class and a name, there are at most four options.
            // Evaluate them in this order, to avoid issues with conflicting names.
            Typeable typeable = null;
            // 1. The variable is local to a method. This is not always a valid option,
            //    hence the additional check.
            if (useBroadedScope) {
                // TODO: HANDLE THIS CASE.
            }
            // 2. The variable is a property of the specified class.
            if (typeable == null) {
                // TODO: HANDLE THIS CASE.
            }
            // 3. The variable is being inherited from a parent class.
            if (typeable == null) {
                // TODO: HANDLE THIS CASE
            }
            // 4. The variable is a static property of an outer class
            //    that encloses this one. This is not always a valid option,
            //    hence the additional check.
            if (useBroadedScope && typeable == null) {
                // TODO: HANDLE THIS CASE.
            }
            // At this point, we've done everything we can. Either we found our
            // target or we didn't.
            return typeable;
        }
    }

    private static BaseSFVertex followReferenceChain(ReferenceExpressionVertex vertex) {
        return null;
    }

    private static String getType(BaseSFVertex vertex) {
        return "";
    }


    private ParameterUtil() {}
}
