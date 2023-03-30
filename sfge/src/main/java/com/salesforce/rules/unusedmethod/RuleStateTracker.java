package com.salesforce.rules.unusedmethod;

import com.salesforce.apex.jorje.ASTConstants.NodeType;
import com.salesforce.collections.CollectionUtil;
import com.salesforce.exception.TodoException;
import com.salesforce.exception.UnexpectedException;
import com.salesforce.graph.Schema;
import com.salesforce.graph.build.CaseSafePropertyUtil.H;
import com.salesforce.graph.ops.ApexClassUtil;
import com.salesforce.graph.ops.ClassUtil;
import com.salesforce.graph.ops.MethodUtil;
import com.salesforce.graph.ops.TraversalUtil;
import com.salesforce.graph.vertex.*;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * A helper class for {@link com.salesforce.rules.UnusedMethodRule}, which tracks various elements
 * of state as the rule executes.
 */
public class RuleStateTracker {
    private final GraphTraversalSource g;
    /**
     * The set of methods on which analysis was performed. Helps us know whether a method returned
     * no violations because it was inspected and an invocation was found, or if it was simply never
     * inspected in the first place.
     */
    private final Set<MethodVertex> eligibleMethods;
    /**
     * The set of methods for which no invocation was found. At the end of execution, we'll generate
     * violations from each method in this set.
     */
    private final Set<MethodVertex> unusedMethods;
    /**
     * A Set used to track every DefiningType for which we've cached values. Minimizing redundant
     * queries is a very high priority for this rule.
     */
    private final Set<String> cachedDefiningTypes;
    /**
     * A map used to cache every {@link MethodCallExpressionVertex} in a given class. Minimizing
     * redundant queries is a very high priority for this rule. <br>
     * Note: Expressions of this type represent invocations of non-constructor methods.
     */
    private final Map<String, List<MethodCallExpressionVertex>> methodCallExpressionsByDefiningType;
    /**
     * A map used to cache every {@link ThisMethodCallExpressionVertex} in a given class. Minimizing
     * redundant queries is a very high priority for this rule. <br>
     * Note: Expressions of this type represent invocations of the {@code this()} constructor
     * pattern.
     */
    private final Map<String, List<ThisMethodCallExpressionVertex>>
            thisMethodCallExpressionsByDefiningType;
    /**
     * A map used to cache every {@link SuperMethodCallExpressionVertex} in a given class.
     * Minimizing redundant queries is a very high priority for this rule. <br>
     * Note: Expressions of this type represent invocations of the {@code super()} constructor
     * pattern.
     */
    private final Map<String, List<SuperMethodCallExpressionVertex>>
            superMethodCallExpressionsByDefiningType;
    // TODO: JDOC
    private final Map<String, InheritanceTree> inheritanceTreesByDefiningType;
    // TODO: JDOC
    private final Map<String, List<String>> superclassListsByDefiningType;
    /**
     * A map used to cache every subclass of a given class. Minimizing redundant queries is a very
     * high priority for this rule.
     */
    private final Map<String, List<String>> subclassesByDefiningType;
    /**
     * A map used to cache every inner class of a given class. Minimizing redundant queries is a
     * very high priority for this rule.
     */
    private final Map<String, List<UserClassVertex>> innerClassesByDefiningType;

    public RuleStateTracker(GraphTraversalSource g) {
        this.g = g;
        this.eligibleMethods = new HashSet<>();
        this.unusedMethods = new HashSet<>();
        this.cachedDefiningTypes = new HashSet<>();
        this.methodCallExpressionsByDefiningType = CollectionUtil.newTreeMap();
        this.thisMethodCallExpressionsByDefiningType = CollectionUtil.newTreeMap();
        this.superMethodCallExpressionsByDefiningType = CollectionUtil.newTreeMap();
        this.inheritanceTreesByDefiningType = CollectionUtil.newTreeMap();
        this.superclassListsByDefiningType = CollectionUtil.newTreeMap();
        this.subclassesByDefiningType = CollectionUtil.newTreeMap();
        this.innerClassesByDefiningType = CollectionUtil.newTreeMap();
    }

    /** Mark the provided method vertex as a candidate for rule analysis. */
    public void trackEligibleMethod(MethodVertex methodVertex) {
        eligibleMethods.add(methodVertex);
    }

    /** Mark the provided method as unused. */
    public void trackUnusedMethod(MethodVertex methodVertex) {
        unusedMethods.add(methodVertex);
    }

    /** Get all of the methods that were found to be unused. */
    public Set<MethodVertex> getUnusedMethods() {
        return unusedMethods;
    }

    /** Get the total number of methods deemed eligible for analysis. */
    public int getEligibleMethodCount() {
        return eligibleMethods.size();
    }

    /**
     * Get every {@link MethodCallExpressionVertex} occurring in the classes represented by {@code
     * definingTypes}.
     */
    List<MethodCallExpressionVertex> getMethodCallExpressionsByDefiningType(
            String... definingTypes) {
        populateMethodCallCachesForDefiningType(definingTypes);
        List<MethodCallExpressionVertex> results = new ArrayList<>();
        for (String definingType : definingTypes) {
            results.addAll(this.methodCallExpressionsByDefiningType.get(definingType));
        }
        return results;
    }

    /**
     * Get every {@link ThisMethodCallExpressionVertex} occurring in the class represented by {@code
     * definingType}.
     *
     * @param definingType
     * @return
     */
    List<ThisMethodCallExpressionVertex> getThisMethodCallExpressionsByDefiningType(
            String definingType) {
        populateMethodCallCachesForDefiningType(definingType);
        return this.thisMethodCallExpressionsByDefiningType.get(definingType);
    }

    /**
     * Get every {@link SuperMethodCallExpressionVertex} occurring in the class represented by
     * {@code definingType}.
     *
     * @param definingType
     * @return
     */
    List<SuperMethodCallExpressionVertex> getSuperMethodCallExpressionsByDefiningType(
            String definingType) {
        populateMethodCallCachesForDefiningType(definingType);
        return this.superMethodCallExpressionsByDefiningType.get(definingType);
    }

    /**
     * Populate the various method call caches for the classes represented by {@code definingTypes}.
     * Do all of them in the same method because it's exceedingly likely that we'll need all of them
     * at one point or another.
     */
    private void populateMethodCallCachesForDefiningType(String... definingTypes) {
        // Determine which defining types don't already have something cached.
        List<String> uncachedTypes =
                Arrays.stream(definingTypes)
                        .filter(t -> !cachedDefiningTypes.contains(t))
                        .collect(Collectors.toList());
        // Populate the caches for those types with empty lists.
        for (String uncachedType : uncachedTypes) {
            methodCallExpressionsByDefiningType.put(uncachedType, new ArrayList<>());
            thisMethodCallExpressionsByDefiningType.put(uncachedType, new ArrayList<>());
            superMethodCallExpressionsByDefiningType.put(uncachedType, new ArrayList<>());
        }

        // If we've already populated the caches for this defining type, there's nothing to do.
        if (uncachedTypes.isEmpty()) {
            return;
        }
        // Otherwise, we need to do a query.
        List<InvocableWithParametersVertex> methodCalls =
                SFVertexFactory.loadVertices(
                        g,
                        g.V()
                                .where(
                                        __.or(
                                                H.hasWithin(
                                                        NodeType.METHOD_CALL_EXPRESSION,
                                                        Schema.DEFINING_TYPE,
                                                        definingTypes),
                                                H.hasWithin(
                                                        NodeType.THIS_METHOD_CALL_EXPRESSION,
                                                        Schema.DEFINING_TYPE,
                                                        definingTypes),
                                                H.hasWithin(
                                                        NodeType.SUPER_METHOD_CALL_EXPRESSION,
                                                        Schema.DEFINING_TYPE,
                                                        definingTypes))));

        // Sort the results by type and cache them appropriately.
        for (InvocableWithParametersVertex invocable : methodCalls) {
            String key = invocable.getDefiningType();
            if (invocable instanceof MethodCallExpressionVertex) {
                methodCallExpressionsByDefiningType
                        .get(key)
                        .add((MethodCallExpressionVertex) invocable);
            } else if (invocable instanceof ThisMethodCallExpressionVertex) {
                thisMethodCallExpressionsByDefiningType
                        .get(key)
                        .add((ThisMethodCallExpressionVertex) invocable);
            } else if (invocable instanceof SuperMethodCallExpressionVertex) {
                superMethodCallExpressionsByDefiningType
                        .get(key)
                        .add((SuperMethodCallExpressionVertex) invocable);
            } else {
                throw new TodoException(
                        "Unexpected InvocableWithParametersVertex implementation "
                                + invocable.getClass());
            }
        }
        cachedDefiningTypes.addAll(uncachedTypes);
    }

    // TODO:JDOC
    List<MethodCallExpressionVertex> getMethodCallExpressionsByMethodName(String methodName) {
        return SFVertexFactory.loadVertices(
                g,
                g.V()
                        .where(
                                H.has(
                                        NodeType.METHOD_CALL_EXPRESSION,
                                        Schema.METHOD_NAME,
                                        methodName)));
    }

    // TODO: JDOC
    UserClassVertex getClassByDefiningType(String definingType) {
        return SFVertexFactory.loadSingleOrNull(
                g,
                g.V().where(H.hasWithin(NodeType.USER_CLASS, Schema.DEFINING_TYPE, definingType)));
    }

    // TODO: JDOC AND COMMENT
    List<String> getSuperclassList(String definingType) {
        // If we don't already have a cached chain, we'll need to create one.
        if (!superclassListsByDefiningType.containsKey(definingType)) {
            List<UserClassVertex> superclasses =
                    SFVertexFactory.loadVertices(
                            g,
                            g.V()
                                    .where(
                                            H.has(
                                                    NodeType.USER_CLASS,
                                                    Schema.DEFINING_TYPE,
                                                    definingType))
                                    .repeat(__.out(Schema.EXTENSION_OF))
                                    .emit());
            superclassListsByDefiningType.put(
                    definingType,
                    superclasses.stream()
                            .map(UserClassVertex::getDefiningType)
                            .collect(Collectors.toList()));
        }
        return superclassListsByDefiningType.get(definingType);
    }

    // TODO: JDOC
    InheritanceTree getInheritanceTree(String definingType) {
        // If we don't already have a cached tree, we'll need to create one.
        if (!inheritanceTreesByDefiningType.containsKey(definingType)) {
            // Starting with the desired type...
            List<Map<String, Object>> rawResults =
                    g.V()
                            .where(H.has(NodeType.USER_CLASS, Schema.DEFINING_TYPE, definingType))
                            .union(
                                    // ...for itself...
                                    __.identity(),
                                    // ...and anything that inherits from it...
                                    __.repeat(__.out(Schema.EXTENDED_BY)).emit())
                            // ...Create a map containing its defining type and those of its
                            // subclasses.
                            .project(Schema.DEFINING_TYPE, Schema.EXTENDED_BY)
                            .by(Schema.DEFINING_TYPE)
                            .by(__.out(Schema.EXTENDED_BY).values(Schema.DEFINING_TYPE).fold())
                            // And return the whole thing as a list.
                            .toList();

            // To properly assemble the tree, we'll want to map new nodes to their expected
            // children.
            Map<String, Set<String>> newExtensions = CollectionUtil.newTreeMap();
            for (Map<String, Object> rawResult : rawResults) {
                String type = (String) rawResult.get(Schema.DEFINING_TYPE);
                // If there's not already a mapped tree for this type, we'll need to create one.
                if (!inheritanceTreesByDefiningType.containsKey(type)) {
                    InheritanceTree newTree = new InheritanceTree(type);
                    inheritanceTreesByDefiningType.put(type, newTree);
                    Set<String> expectedChildren = CollectionUtil.newTreeSet();
                    assert rawResult.get(Schema.EXTENDED_BY) instanceof List;
                    List<?> rawChildren = (List<?>) rawResult.get(Schema.EXTENDED_BY);
                    for (Object o : rawChildren) {
                        assert o instanceof String;
                        expectedChildren.add((String) o);
                    }
                    newExtensions.put(type, expectedChildren);
                }
            }
            // Properly add all new nodes into the tree.
            for (String newNodeType : newExtensions.keySet()) {
                InheritanceTree newNode = inheritanceTreesByDefiningType.get(newNodeType);
                for (String expectedChild : newExtensions.get(newNodeType)) {
                    newNode.addSubclass(inheritanceTreesByDefiningType.get(expectedChild));
                }
            }
        }
        // Return the tree from the cache.
        return inheritanceTreesByDefiningType.get(definingType);
    }

    /** Get all immediate subclasses of the provided classes. */
    List<String> getSubclasses(String... definingTypes) {
        List<String> results = new ArrayList<>();
        // For each type we were given...
        for (String definingType : definingTypes) {
            // If we've already got results for that type, we can just add those results to the
            // overall list.
            if (this.subclassesByDefiningType.containsKey(definingType)) {
                results.addAll(this.subclassesByDefiningType.get(definingType));
                continue;
            }
            // Otherwise, we need to do some querying.
            List<UserClassVertex> subclassVertices =
                    SFVertexFactory.loadVertices(
                            g,
                            g.V()
                                    .where(
                                            H.has(
                                                    NodeType.USER_CLASS,
                                                    Schema.DEFINING_TYPE,
                                                    definingType))
                                    .out(Schema.EXTENDED_BY));
            List<String> subclassNames =
                    subclassVertices.stream()
                            .map(UserClassVertex::getDefiningType)
                            .collect(Collectors.toList());
            this.subclassesByDefiningType.put(definingType, subclassNames);
            results.addAll(subclassNames);
        }
        return results;
    }

    /** Get any inner classes that reside in {@code definingType}. */
    List<UserClassVertex> getInnerClasses(String definingType) {
        if (!this.innerClassesByDefiningType.containsKey(definingType)) {
            this.innerClassesByDefiningType.put(
                    definingType, ClassUtil.getInnerClassesOf(g, definingType));
        }
        return this.innerClassesByDefiningType.get(definingType);
    }

    // TODO: JDOC
    Optional<MethodVertex> getMethodWithSignature(
            String definingType, String signature, boolean includeInheritedMethods) {
        return MethodUtil.getMethodWithSignature(
                g, definingType, signature, includeInheritedMethods);
    }

    /**
     * Indicates whether the specified class has/inherits a method with the specified signature
     *
     * @param definingType - A class name
     * @param signature - The signature of a method
     * @param includeInheritedMethods - Allows check to cover inherited methods in addition to local
     *     ones
     * @return True if class has method, else false
     */
    boolean classHasMatchingMethod(
            String definingType, String signature, boolean includeInheritedMethods) {
        return getMethodWithSignature(definingType, signature, includeInheritedMethods).isPresent();
    }

    /**
     * Get all {@link NewObjectExpressionVertex} instances representing instantiations of an object
     * whose type is {@code constructedType}.
     */
    List<NewObjectExpressionVertex> getConstructionsOfType(String constructedType) {
        // Start off with all NewObjectExpressionVertex instances whose declared type references the
        // desired type.
        List<NewObjectExpressionVertex> results =
                SFVertexFactory.loadVertices(
                        g,
                        g.V()
                                .where(
                                        H.has(
                                                NodeType.NEW_OBJECT_EXPRESSION,
                                                Schema.TYPE,
                                                constructedType)));

        // Inner types can be referenced by outer/sibling types by just the inner name rather than
        // the full name.
        // This means we need to do more, but what that "more" is depends on whether we're looking
        // at an inner or outer type.
        boolean typeIsInner = constructedType.contains(".");
        if (typeIsInner) {
            // If the type is inner, then we need to add inner-name-only references occurring in
            // other classes.
            String[] portions = constructedType.split("\\.");
            String outerType = portions[0];
            String innerType = portions[1];
            GraphTraversal<Vertex, Vertex> traversal =
                    g.V().where(H.has(NodeType.NEW_OBJECT_EXPRESSION, Schema.TYPE, innerType));
            List<NewObjectExpressionVertex> additionalResults =
                    getAliasedInnerTypeUsages(outerType, NodeType.NEW_OBJECT_EXPRESSION, traversal);
            results.addAll(additionalResults);
        } else {
            // If the type isn't an inner type, then it's possible that some of the references we
            // found are actually
            // inner-name-only references to inner types. So we need to remove those.
            results = removeInnerTypeCollisions(results, constructedType);
        }
        return results;
    }

    /**
     * Get all {@link MethodCallExpressionVertex} instances representing invocations of a method
     * named {@code methodName} on a thing called {@code referencedType}.
     */
    List<MethodCallExpressionVertex> getInvocationsOnType(
            String referencedType, String methodName) {
        // Start off with all MethodCallExpressionVertex instances whose contained
        // ReferenceExpression matches the referenced type.
        List<MethodCallExpressionVertex> results =
                SFVertexFactory.loadVertices(
                        g,
                        TraversalUtil.traverseInvocationsOf(
                                g, new ArrayList<>(), referencedType, methodName));

        // Inner types can be referenced by their outer/sibling types by just their inner name
        // rather than the full name. This means we need to do more, but what that "more" is
        // depends on whether we're looking an inner or outer type.
        boolean typeIsInner = referencedType.contains(".");
        if (typeIsInner) {
            // If the type is inner, then we need to add inner-name-only references occurring in
            // other classes.
            String[] portions = referencedType.split("\\.");
            String outerType = portions[0];
            String innerType = portions[1];
            GraphTraversal<Vertex, Vertex> traversal =
                    TraversalUtil.traverseInvocationsOf(
                            g, new ArrayList<>(), innerType, methodName);
            List<MethodCallExpressionVertex> additionalResults =
                    getAliasedInnerTypeUsages(
                            outerType, NodeType.METHOD_CALL_EXPRESSION, traversal);
            results.addAll(additionalResults);
        } else {
            // If the type isn't an inner type, then it's possible some of the references we found
            // are actually inner-name-only references to inner types. So we need to remove those.
            results = removeInnerTypeCollisions(results, referencedType);
        }
        return results;
    }

    // TODO: JDOC
    Optional<BaseSFVertex> doTheThing(MethodCallExpressionVertex methodCall) {
        return TypeResolutionUtil.resolveToVertex(g, methodCall);
    }

    /**
     * Returns the {@link BaseSFVertex} where the value referenced in the specified method call is
     * declared. <br>
     * E.g., for {@code someObject.someMethod()}, returns the declaration of {@code someObject}.
     */
    Optional<BaseSFVertex> getDeclarationOfReferencedValue(MethodCallExpressionVertex methodCall) {
        // Step 1: Get the name of the thing being referenced.
        List<String> referenceNameList = methodCall.getReferenceExpression().getNames();
        // Step 2: The method call must happen in the context of another method, a field
        // declaration, or both. Determine which.
        Optional<MethodVertex> parentMethodOptional = methodCall.getParentMethod();
        Optional<FieldDeclarationVertex> parentFieldDeclarationOptional =
                methodCall.getParentFieldDeclaration();
        // Theoretically one or both of those optionals should be present. If not, throw an
        // exception.
        if (!parentMethodOptional.isPresent() && !parentFieldDeclarationOptional.isPresent()) {
            throw new UnexpectedException(
                    "Cannot determine context for method call " + methodCall.toMinimalString());
        }
        if (referenceNameList.isEmpty()) {
            System.out.println("Actual result: empty b/c no refs, own def type " + methodCall.getDefiningType());
            return Optional.empty();
        }
        // Step 3: If we're in a method, check for variables and parameters.
        if (parentMethodOptional.isPresent()) {
            MethodVertex parentMethod = parentMethodOptional.get();
            // Step 3A: Check for variables.
            List<VariableDeclarationVertex> declaredVariables =
                    MethodUtil.getVariableDeclarations(g, parentMethod);
            for (VariableDeclarationVertex declaredVariable : declaredVariables) {
                // NOTE: A known bug exists here. We merely check that the variable exists,
                //       without verifying that its declaration occurs before the method call.
                //       So `SomeClass someClass = SomeClass.getInstance();` will incorrectly
                //       pass this if-check.
                if (declaredVariable.getName().equalsIgnoreCase(referenceNameList.get(0))) {
                    System.out.println("Actual result: variable " + declaredVariable.getName() + " of type " + declaredVariable.getCanonicalType());
                    return Optional.of(declaredVariable);
                }
            }

            // Step 3B: Check whether any of the method's parameters match the first referenced
            //          name.
            List<ParameterVertex> params = parentMethod.getParameters();
            for (ParameterVertex param : params) {
                if (param.getName().equalsIgnoreCase(referenceNameList.get(0))) {
                    System.out.println("Actual result: parameter " + param.getName() + " of type " + param.getCanonicalType());
                    return Optional.of(param);
                }
            }
        }

        // Step 4: Check whether any properties on the class match the first referenced name.
        Optional<FieldVertex> fieldOptional =
                ApexClassUtil.getField(g, methodCall.getDefiningType(), referenceNameList.get(0));
        // If there's a property, we need to make sure it's visible to our current context.
        if (fieldOptional.isPresent()) {
            // Determine whether we're in a static context or an instance context.
            boolean isContextStatic =
                    parentMethodOptional
                            .map(FieldWithModifierVertex::isStatic)
                            .orElseGet(() -> parentFieldDeclarationOptional.get().isStatic());
            // Instance context has access to both static and instance properties, while static
            // context only has access to static properties.
            if (!isContextStatic || fieldOptional.get().isStatic()) {
                System.out.println("Actual result: prop " + fieldOptional.get().getName() + " of type " + fieldOptional.get().getCanonicalType());
                return Optional.of(fieldOptional.get());
            }
        }

        // Step 5: If, after all of that, we still haven't found anything, just return an empty
        // Optional and let the caller figure out what to do with it.
        System.out.println("Actual result: Empty, own def type " + methodCall.getDefiningType());
        return Optional.empty();
    }

    /**
     * An inner class's sibling/outer classes can reference it by its inner type alone. This method
     * accepts a traversal containing all potential references to the inner name, and filters for
     * those that occur in the sibling/outer classes and therefore refer to the inner type.
     *
     * @param outerType - The name of the outer class.
     * @param nodeType - The node type being targeted.
     * @param initialTraversal - A traversal containing all nodes of {@code nodeType} that
     *     potentially reference the inner type.
     * @return - A list of {@link BaseSFVertex} instances representing the subset of {@code
     *     initialTraversal} that are aliased inner type references.
     * @param <T> - An extension of {@link BaseSFVertex}.
     */
    private <T extends BaseSFVertex> List<T> getAliasedInnerTypeUsages(
            String outerType, String nodeType, GraphTraversal<Vertex, Vertex> initialTraversal) {
        return SFVertexFactory.loadVertices(
                g,
                initialTraversal.where(
                        __.or(
                                H.has(nodeType, Schema.DEFINING_TYPE, outerType),
                                H.hasStartingWith(
                                        nodeType, Schema.DEFINING_TYPE, outerType + "."))));
    }

    /**
     * If an inner class shares the name of an outer class, then references to the inner class that
     * occur in its outer/sibling classes can resemble references to the outer class. This class
     * filters such false positives from the results of a query.
     *
     * @param unfilteredResults - The results, pre-filtering
     * @param referencedType - The outer type with which inner types may collide
     * @return - {@code unfilteredResults}, with all colliding references removed
     */
    private <T extends BaseSFVertex> List<T> removeInnerTypeCollisions(
            List<T> unfilteredResults, String referencedType) {
        return unfilteredResults.stream()
                .filter(
                        v -> {
                            // Convert the result's definingType into an outer type by getting
                            // everything before the first period.
                            String outerType = v.getDefiningType().split("\\.")[0];
                            // Get any inner classes belonging to this outer type in a
                            // case-insensitive set.
                            Set<String> innerClassNames =
                                    CollectionUtil.newTreeSetOf(
                                            getInnerClasses(outerType).stream()
                                                    .map(UserClassVertex::getName)
                                                    .collect(Collectors.toList()));
                            // If the set lacks an entry for our referenced type, then there's no
                            // conflicting inner class, and we can keep this result.
                            return !innerClassNames.contains(referencedType);
                        })
                .collect(Collectors.toList());
    }

    // TODO: JDOC WHOLE CLASS
    static class InheritanceTree {
        private final String definingType;
        private final List<InheritanceTree> subclasses;

        private InheritanceTree(String definingType) {
            this.definingType = definingType;
            this.subclasses = new ArrayList<>();
        }

        private void addSubclass(InheritanceTree inheritanceTree) {
            subclasses.add(inheritanceTree);
        }

        String getDefiningType() {
            return definingType;
        }

        List<InheritanceTree> getSubclasses() {
            return subclasses;
        }
    }
}
