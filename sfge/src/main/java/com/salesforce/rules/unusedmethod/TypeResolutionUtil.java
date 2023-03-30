package com.salesforce.rules.unusedmethod;

import com.salesforce.exception.TodoException;
import com.salesforce.exception.UnexpectedException;
import com.salesforce.graph.ops.ApexClassUtil;
import com.salesforce.graph.ops.ClassUtil;
import com.salesforce.graph.ops.MethodUtil;
import com.salesforce.graph.vertex.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

// TODO: JDOC WHOLE CLASS
public final class TypeResolutionUtil {

    static Optional<BaseSFVertex> resolveToVertex(GraphTraversalSource g, MethodCallExpressionVertex methodCall) {
        // TODO: CHECK CACHE

        // TODO: CACHE VALUE
        return resolveIntermediateTypes(g, methodCall.getReferenceExpression()).getVertex();
    }

    // TODO: JDOC
    static Optional<Typeable> resolveToTypeable(GraphTraversalSource g, MethodCallExpressionVertex methodCall) {
        // TODO: CHECK THE CACHE

        // TODO: CACHE VALUE
        return resolveIntermediateTypes(g, methodCall.getReferenceExpression()).getTypeable();
    }

    // TODO: JDOC
    private static TypeResolutionIntermediary resolveIntermediateTypes(GraphTraversalSource g, MethodCallExpressionVertex methodCall) {
        // TODO: CACHE INTERACTION

        // First, get whatever type the method call's reference resolves to.
        TypeResolutionIntermediary intermediateType = resolveIntermediateTypes(g, methodCall.getReferenceExpression());
        // Get the list of methods matching that name (including inherited versions, for completeness).
        List<MethodVertex> potentialInvocations = MethodUtil.getMethodsWithName(g, intermediateType.getIntermediateType(), methodCall.getMethodName(), true);
        // We only care about the first method, since they should all have the same return type.
        if (potentialInvocations.isEmpty()) {
            throw new UnexpectedException("No implementations of " + methodCall.getMethodName() + " on " + intermediateType.getIntermediateType());
        }
        // TODO: Can inner-class shenanigans cause this to be incorrect?
        return new TypeResolutionIntermediary(potentialInvocations.get(0));
    }

    // TODO: JDOC
    private static TypeResolutionIntermediary resolveIntermediateTypes(GraphTraversalSource g, AbstractReferenceExpressionVertex abstractReferenceExpression) {
       // TODO: CHECK THE CACHE

        if (abstractReferenceExpression instanceof EmptyReferenceExpressionVertex) {
            // TODO: CACHE
            return new TypeResolutionIntermediary(abstractReferenceExpression.getDefiningType());
        }
        ReferenceExpressionVertex referenceExpression = (ReferenceExpressionVertex) abstractReferenceExpression;

        // If there's no cache, we need to get a value.
        // Identify the type we're starting at.
        TypeResolutionIntermediary currentType = null;
        if (referenceExpression.getChildren().size() == 1) {
            BaseSFVertex child = referenceExpression.getOnlyChild();
            if (child instanceof Typeable) {
                // If the reference's child is a typeable, it can tell us our starting type.
                currentType = new TypeResolutionIntermediary((Typeable) child);
            } else if (child instanceof MethodCallExpressionVertex) {
                // If the reference's type is another method call, we need to drill into that.
                currentType = resolveIntermediateTypes(g, (MethodCallExpressionVertex) child);
            }
        }

        // Get the chain of referenced names we need to resolve.
        List<String> referenceNameList = referenceExpression.getNames();
        // We also need to identify whether this reference exists in a static or instance context.
        boolean isContextStatic = referenceContextIsStatic(referenceExpression);
        // Go through the chain of names.
        for (String referenceName : referenceNameList) {
            // TODO: CHECK THE CACHE TO SEE IF WE CAN SHORT-CIRCUIT

            // TODO: COMMENT RE THIS IF-BRANCH
            if (currentType == null) {
                Optional<MethodVertex> parentMethodOptional = referenceExpression.getParentMethod();
                if (parentMethodOptional.isPresent()) {
                    MethodVertex parentMethod = parentMethodOptional.get();
                    // TODO: COMMENT RE CHECKING VARIABLES
                    List<VariableDeclarationVertex> declaredVariables = MethodUtil.getVariableDeclarations(g, parentMethod);
                    for (VariableDeclarationVertex declaredVariable : declaredVariables) {
                        // TODO: FORMAT THIS COMMENT.
                        // NOTE: A known bug exists here. This check doesn't verify that the
                        //       variable declaration occurs before the reference. So calls like `SomeClass someClass = SomeClass.getInstance()` will incorrectly pass this if-check.
                        if (declaredVariable.getName().equalsIgnoreCase(referenceName)) {
                            currentType = new TypeResolutionIntermediary(declaredVariable);
                            // If we were in a static context, we're not anymore.
                            isContextStatic = false;
                            // TODO: CACHE
                            break;
                        }
                    }
                    if (currentType != null) {
                        continue;
                    }
                    // TODO: COMMENT RE CHECKING PARAMS
                    List<ParameterVertex> params = parentMethod.getParameters();
                    for (ParameterVertex param : params) {
                        if (param.getName().equalsIgnoreCase(referenceName)) {
                            currentType = new TypeResolutionIntermediary(param);
                            // If we were in a static context, we're not anymore.
                            isContextStatic = false;
                            // TODO: CACHE
                            break;
                        }
                    }
                }
                if (currentType != null) {
                    continue;
                }
            }

            // TODO: COMMENT ON PURPOSE OF THIS VARIABLE
            String speculativeCurrentType = currentType != null ? currentType.getIntermediateType() : referenceExpression.getDefiningType();

            // TODO: COMMENT RE PROP CHECKING
            // TODO: ADD SUPER CHECKING
            Optional<FieldVertex> fieldOptional = ApexClassUtil.getField(g, speculativeCurrentType, referenceName);

            // If there's a property, we need to make sure it's visible to our current context.
            if (fieldOptional.isPresent()) {
                // Instance context has access to both static and instance properties, while static
                // context only has access to static properties.
                if (!isContextStatic || fieldOptional.get().isStatic()) {
                    currentType = new TypeResolutionIntermediary(fieldOptional.get());
                    // If we were in a static context, we're not anymore.
                    isContextStatic = false;
                    // TODO: CACHE
                    continue;
                }
            }

            // TODO: CHECK FOR INNER CLASS OF EFFECTIVE CURRENT
            String speculativeOuterType = speculativeCurrentType.split("\\.")[0];
            Optional<UserClassVertex> innerClassOptional = ClassUtil.getUserClass(g, speculativeOuterType + "." + referenceName);
            if (innerClassOptional.isPresent()) {
                currentType = new TypeResolutionIntermediary(innerClassOptional.get().getDefiningType());
                // If we were in a static context, we're not anymore.
                isContextStatic = false;
                // TODO: CACHE
                continue;
            }
            Optional<UserClassVertex> outerClassOptional = ClassUtil.getUserClass(g, referenceName);
            if (outerClassOptional.isPresent()) {
                currentType = new TypeResolutionIntermediary(outerClassOptional.get().getDefiningType());
                // If we were in a static context, we're not anymore.
                isContextStatic = false;
                // TODO: CACHE
                continue;
            }

            // If we're here, we found nothing. That's a problem.
            throw new UnexpectedException("Failed to resolve reference to " + referenceName);
        }
        // If we've reached the end of the chain, then we can return whatever we have.
        // TODO: CACHE BEFORE RETURNING.
        return currentType;
    }

    // TODO: JDOC
    private static boolean referenceContextIsStatic(ReferenceExpressionVertex referenceExpressionVertex) {
        Optional<MethodVertex> parentMethodOptional = referenceExpressionVertex.getParentMethod();
        Optional<FieldDeclarationVertex> parentFieldDeclarationOptional = referenceExpressionVertex.getParentFieldDeclaration();
        if (!parentMethodOptional.isPresent() && !parentFieldDeclarationOptional.isPresent()) {
            throw new UnexpectedException("Cannot determine context for reference expression " + referenceExpressionVertex.toMinimalString());
        }
        return parentMethodOptional.map(FieldWithModifierVertex::isStatic).orElseGet(() -> parentFieldDeclarationOptional.get().isStatic());
    }

    // TODO: JDOC WHOLE CLASS
    private static class TypeResolutionIntermediary {
        private final Typeable typeable;
        private final MethodVertex methodVertex;
        private final String className;

        TypeResolutionIntermediary(Typeable typeable) {
            this.typeable = typeable;
            this.methodVertex = null;
            this.className = null;
        }


        TypeResolutionIntermediary(MethodVertex methodVertex) {
            this.typeable = null;
            this.methodVertex = methodVertex;
            this.className = null;
        }

        TypeResolutionIntermediary(String className) {
            this.typeable = null;
            this.methodVertex = null;
            this.className = className;
        }

        String getIntermediateType() {
            return typeable != null
                ? typeable.getCanonicalType()
                : methodVertex != null ? methodVertex.getReturnType() : className;
        }

        Optional<Typeable> getTypeable() {
            return typeable != null ? Optional.of(typeable) : Optional.empty();
        }

        Optional<BaseSFVertex> getVertex() {
            return typeable != null
                ? Optional.of((BaseSFVertex) typeable)
                : methodVertex != null ? Optional.of(methodVertex) : Optional.empty();
        }
    }

    private TypeResolutionUtil() {}
}
