package com.salesforce.rules.unusedmethod;

import com.salesforce.collections.CollectionUtil;
import com.salesforce.graph.vertex.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for {@link com.salesforce.rules.UnusedMethodRule}. Used for determining whether an
 * instance method is ever invoked, e.g., {@code someMethod()}, {@code super.someMethod())}, etc.
 */
public class InstanceMethodCallValidator extends BaseMethodCallValidator {
    // TODO: JDOC
    private final TreeSet<String> thisableTypes;
    // TODO: JDOC
    private final TreeSet<String> superableTypes;
    // TODO: JDOC
    private final Map<String, Set<String>> limitedExternalExposureMap;
    // TODO: JDOC
    private final Set<String> fullyExposedTypes;
    // TODO: JDOC

    public InstanceMethodCallValidator(
            MethodVertex targetMethod, RuleStateTracker ruleStateTracker) {
        super(targetMethod, ruleStateTracker);
        this.thisableTypes = CollectionUtil.newTreeSet();
        this.superableTypes = CollectionUtil.newTreeSet();
        this.limitedExternalExposureMap = CollectionUtil.newTreeMap();
        this.fullyExposedTypes = CollectionUtil.newTreeSet();
        identifyScopes();
    }

    private void identifyScopes() {
        String ownType = targetMethod.getDefiningType();
        if (targetMethod.isPrivate()) {
            // Private methods are this-able only on their own type, super-able nowhere, and
            // externally
            // exposed only within their own type's inner class network.
            thisableTypes.add(ownType);
            limitedExternalExposureMap.put(ownType, getInnerTypeNetwork(ownType));
        } else {
            // Public/protected methods have more complex exposure rules.
            // We need to identify the class where the original variant of the target method is
            // declared,
            // and every class whose implementation the target method overrides.
            // By default, assume the method originates on its own type.
            String rootType = ownType;
            Set<String> overriddenSuperClasses = CollectionUtil.newTreeSet();
            if (targetMethod.isOverride()) {
                // If the method is an override, ascend the superclass chain until we find the
                // original method.
                List<String> superclassList = ruleStateTracker.getSuperclassList(ownType);
                for (String superclass : superclassList) {
                    overriddenSuperClasses.add(superclass);
                    Optional<MethodVertex> superMethodOptional =
                            ruleStateTracker.getMethodWithSignature(
                                    superclass, targetMethod.getSignature(), false);
                    // If this class declares a variant of the method that is not an override of
                    // something else, it's the original.
                    if (superMethodOptional.isPresent()
                            && !superMethodOptional.get().isOverride()) {
                        rootType = superclass;
                        break;
                    }
                }
            }
            // Starting at the root, recursively process the entire inheritance tree.
            RuleStateTracker.InheritanceTree rootTree =
                    ruleStateTracker.getInheritanceTree(rootType);
            recursivelyHandleExposure(rootTree, overriddenSuperClasses, false, false);
        }
    }

    /**
     * For instance methods, an "internal" usage is one that an instance of the class performs on
     * itself.<br>
     * {@code this.method()} and {@code method()} are internal calls when performed within:
     *
     * <ol>
     *   <li>The class where the method is defined.
     *   <li>Subclasses that inherit the method but do NOT override it.
     *   <li>Superclasses that define a method of which the target method is itself an override.
     * </ol>
     *
     * Furthermore, in any subclass that inherits the method, {@code super.method()} is an internal
     * call.
     *
     * @return - true if the method is invoked internally.
     */
    protected boolean internalUsageDetected2() {
        return false;
    }

    // TODO: JDOC
    private Set<String> getInnerTypeNetwork(String definingType) {
        // Convert the inner type to an outer type.
        String outerType = definingType.split("\\.")[0];
        // Get all inner types for that outer type.
        List<String> innerTypes =
                ruleStateTracker.getInnerClasses(outerType).stream()
                        .map(UserClassVertex::getDefiningType)
                        .collect(Collectors.toList());
        Set<String> resultSet = CollectionUtil.newTreeSetOf(outerType);
        resultSet.addAll(innerTypes);
        return resultSet;
    }

    // TODO: JDOC
    private Set<String> recursivelyHandleExposure(
            RuleStateTracker.InheritanceTree inheritanceTree,
            Set<String> superclassesOfTarget,
            boolean subclassOfTarget,
            boolean parentIsThisable) {
        // We need to know whether the type is this-able, and whether it's super-able.
        String definingType = inheritanceTree.getDefiningType();
        boolean isOwnType = definingType.equalsIgnoreCase(targetMethod.getDefiningType());
        boolean isSuperclass = superclassesOfTarget.contains(definingType);
        boolean isNonOverridingSubclass =
                subclassOfTarget
                        && parentIsThisable
                        && !ruleStateTracker.classHasMatchingMethod(
                                definingType, targetMethod.getSignature(), false);
        // The type is this-able if it's the method's own type, an overridden superclass, or a
        // non-overriding subclass.
        boolean isThisable = isOwnType || isSuperclass || isNonOverridingSubclass;
        // The type is super-able if its immediate parent is a this-able subclass of the target
        // method's class.
        boolean isSuperable = subclassOfTarget && parentIsThisable;

        // We need to recursively process this class's subclasses.
        Set<String> subclasses = CollectionUtil.newTreeSet();
        for (RuleStateTracker.InheritanceTree subclassTree : inheritanceTree.getSubclasses()) {
            subclasses.addAll(
                    recursivelyHandleExposure(
                            subclassTree,
                            superclassesOfTarget,
                            subclassOfTarget || isOwnType,
                            isThisable));
        }

        // Mark the ways in which this class is exposed.
        if (isSuperable) {
            superableTypes.add(definingType);
        }

        if (isThisable) {
            thisableTypes.add(definingType);

            // This-able types also have varying degrees of external exposure based on their
            // visibility and that of the target method.
            Set<String> limitedExternalitySet = CollectionUtil.newTreeSet();
            // This-ables are always externally exposed to themselves and (usually, see note) their
            // inner class network.
            // NOTE: This is an oversimplification. Technically, private classes that inherit a
            // protected method only expose
            //       that method to classes in the inner class network that also inherit the method.
            // However, this omission
            //       will only slightly impact performance and should not impact correctness at all.
            limitedExternalitySet.addAll(getInnerTypeNetwork(definingType));
            UserClassVertex userClassVertex = ruleStateTracker.getClassByDefiningType(definingType);
            // Public/global classes are also exposed to all their subclasses.
            if (userClassVertex.isPublic() || userClassVertex.isGlobal()) {
                limitedExternalitySet.addAll(subclasses);
                // If the target method and the this-able type are both public, then the type has
                // full exposure everywhere.
                if (targetMethod.isPublic()) {
                    fullyExposedTypes.add(definingType);
                }
            }
            limitedExternalExposureMap.put(definingType, limitedExternalitySet);
        }
        // Return a new set containing all the subclasses and this type itself, for use by the
        // recursive callers.
        subclasses.add(definingType);
        return subclasses;
    }

    // TODO: JDOC
    @Override
    protected boolean internalUsageDetected() {
        Set<String> allInternallyExposedTypes =
                CollectionUtil.newTreeSetOf(thisableTypes, superableTypes);
        List<MethodCallExpressionVertex> potentialCalls =
                ruleStateTracker.getMethodCallExpressionsByDefiningType(
                        allInternallyExposedTypes.toArray(new String[0]));

        for (MethodCallExpressionVertex potentialCall : potentialCalls) {
            String definingType = potentialCall.getDefiningType();
            // For it to be a match, it must be this-able or super-able in a place where such a
            // thing is allowed.
            boolean isAllowedThis =
                    thisableTypes.contains(definingType)
                            && (potentialCall.isThisReference()
                                    || potentialCall.isEmptyReference());
            boolean isAllowedSuper =
                    superableTypes.contains(definingType) && potentialCall.isSuperReference();
            if (!isAllowedThis && !isAllowedSuper) {
                continue;
            }

            // For it to be a match, the names have to match.
            if (!potentialCall.getFullMethodName().equalsIgnoreCase(targetMethod.getName())) {
                continue;
            }

            // For it to be a match, the parameters have to match.
            if (!parametersAreValid(potentialCall)) {
                continue;
            }

            // If all of that worked, it's a match.
            return true;
        }
        return false;
    }

    /**
     * For instance methods, an "external" usage is one that is performed on an instance of the
     * class by something else.<br>
     * E.g., {@code instanceOfSomeClass.method()} or {@code new SomeClass().method()}.
     *
     * @return - True if the method is invoked externally.
     */
    @Override
    protected boolean externalUsageDetected() {
        // Start with the limited externality checks, since those are likely to be faster by virtue
        // of their tighter scopes.
        for (String limitedExternalType : limitedExternalExposureMap.keySet()) {
            String[] typesWithExposure =
                    limitedExternalExposureMap.get(limitedExternalType).toArray(new String[0]);
            // Get every method call expression occurring in any of the types where the external
            // type is exposed.
            List<MethodCallExpressionVertex> potentialCalls =
                    ruleStateTracker.getMethodCallExpressionsByDefiningType(typesWithExposure);
            if (isExternalCall(potentialCalls, limitedExternalType)) {
                return true;
            }
        }
        // Get every single MethodCallExpression that invokes a method with the desired name.
        List<MethodCallExpressionVertex> allPotentialCalls =
                ruleStateTracker.getMethodCallExpressionsByMethodName(targetMethod.getName());
        // Use a null second parameter for `isExternalCall()` so it only evaluates the fully
        // external types.
        return isExternalCall(allPotentialCalls, null);
    }

    // TODO: JDOC
    private boolean isExternalCall(
            List<MethodCallExpressionVertex> potentialCalls, String limitedlyExternalType) {
        for (MethodCallExpressionVertex potentialCall : potentialCalls) {
            // Empty
            // TODO: PROBABLY OUGHT TO DO SOME KIND OF FILTERING OF RESULTS WE KNOW AREN'T GOOD?

            // If the method's name is wrong, it's obviously not a match.
            if (!potentialCall.getMethodName().equalsIgnoreCase(targetMethod.getName())) {
                continue;
            }

            // If the parameters are wrong, it's not a match.
            if (!parametersAreValid(potentialCall)) {
                continue;
            }

            Optional<BaseSFVertex> declarationOptional =
                    ruleStateTracker.doTheThing(potentialCall);
            if (declarationOptional.isPresent()) {
                BaseSFVertex declaration = declarationOptional.get();
                String declaredType = "";
                if (declaration instanceof Typeable) {
                    declaredType = ((Typeable) declaration).getCanonicalType();
                } else if (declaration instanceof MethodVertex) {
                    declaredType = ((MethodVertex) declaration).getReturnType();
                }
                if (declaredType.equalsIgnoreCase(limitedlyExternalType) || fullyExposedTypes.contains(declaredType)) {
                    return true;
                }
            }
        }
        return false;
    }
}
