package com.salesforce.rules.unusedmethod;

import com.salesforce.collections.CollectionUtil;
import com.salesforce.graph.vertex.MethodCallExpressionVertex;
import com.salesforce.graph.vertex.MethodVertex;
import com.salesforce.graph.vertex.UserClassVertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class for {@link com.salesforce.rules.UnusedMethodRule}. Used for determining whether an
 * instance method is ever invoked, e.g., {@code someMethod()}, {@code super.someMethod())}, etc.
 */
public class InstanceMethodCallValidator extends BaseMethodCallValidator {
    private final Set<String> potentialInternalCallers;
    private final Set<String> thisableTypes;
    private final Set<String> superableTypes;
    private final Map<String, Set<String>> limitedExternalityMap;
    private final Set<String> fullyExternalTypes;



    public InstanceMethodCallValidator(
            MethodVertex targetMethod, RuleStateTracker ruleStateTracker) {
        super(targetMethod, ruleStateTracker);
        potentialInternalCallers = CollectionUtil.newTreeSet();
        thisableTypes = CollectionUtil.newTreeSet();
        superableTypes = CollectionUtil.newTreeSet();
        limitedExternalityMap = CollectionUtil.newTreeMap();
        fullyExternalTypes = CollectionUtil.newTreeSet();
    }

    private void identifyScopes() {
        /*
        - Private:
            - This-ability:
                - Self
            - Super-ability:
                - Nowhere
            - Limited externality:
                - Self => self, inner, outer, and siblings.
        - Protected:
            - This-ability:
                - Per private
                - Non-overriding subclasses
            - Super-ability:
                - Non-overriding subclasses
                - Directly overriding subclasses
            - Limited externality:
                - Per private
                - Any private this-able => self.
                - Any public this-able => All subclasses.
        - Public:
            - This-ability:
                - Per protected
            - Super-ability:
                - Per protected
            - Limited externality:
                - Per protected
                - Any private this-able => self, inner, outer, siblings.
            - Full externality:
                - Any public this-able
        - Override:
            - This-ability
                - Per applicable scope
                - Overridden superclasses
         */

        // The target method is always this-able on its own defining type.
        String ownDefiningType = targetMethod.getDefiningType();
        thisableTypes.add(ownDefiningType);

        // The method's defining type always grants limited externality to itself and its inner/outer/sibling classes.
        Set<String> ownExternalitySet = CollectionUtil.newTreeSetOf(ownDefiningType);
        List<UserClassVertex> typesInOwnFile = ruleStateTracker.getInnerClasses(ownDefiningType.split("\\.")[0]);
        for (UserClassVertex typeInOwnFile : typesInOwnFile) {
            ownExternalitySet.add(typeInOwnFile.getDefiningType());
        }
    }

    private boolean internal() {
        // Get every method call expression occurring in any potential internal caller.
        List<MethodCallExpressionVertex> potentialCallers = ruleStateTracker.getMethodCallExpressionsByDefiningType()

        return false;
    }

    private boolean external() {
        return false;
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
    @Override
    protected boolean internalUsageDetected() {
        // First, check for usage in the class where the target method is defined.
        List<MethodCallExpressionVertex> ownClassPotentialCalls =
                ruleStateTracker.getMethodCallExpressionsByDefiningType(
                        targetMethod.getDefiningType());
        for (MethodCallExpressionVertex potentialCall : ownClassPotentialCalls) {
            // If the call is neither `this.method()` or `method()`, it's not a match.
            if (!potentialCall.isThisReference() && !potentialCall.isEmptyReference()) {
                continue;
            }
            // If the method name is wrong, it's not a match.
            if (!potentialCall.getMethodName().equalsIgnoreCase(targetMethod.getName())) {
                continue;
            }
            // If the method's parameters don't match, it's not a match.
            if (!parametersAreValid(potentialCall)) {
                continue;
            }
            // If all of that worked, then it's a valid match.
            return true;
        }
        // TODO: If class is abstract/virtual and the method is non-private, check subclasses.
        //       - Get all subclasses
        //       - Get all MethodCallExpressionVertex instances in each subclass
        //       - If the subclass overrides the method, only look for super. Otherwise look for
        // implied/explicit `this` also.
        //       - Unless the subclass overrides the method, drill into the subclass's subclasses.
        // TODO: If class is an extension of a superclass and the method is an override, check
        // superclasses.
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
        // TODO: IMPLEMENT THIS METHOD.
        return false;
    }

    private List<UserClassVertex> getSuperclasses() {
        return new ArrayList<>();
    }

    private class SubclassSummary {
        private List<UserClassVertex> inheritingSubclasses;
        private List<UserClassVertex> overridingSubclasses;
        private List<UserClassVertex> miscellaneousSubclasses;


        public SubclassSummary(String definingType) {
            this.inheritingSubclasses = new ArrayList<>();
            this.overridingSubclasses = new ArrayList<>();
            this.miscellaneousSubclasses = new ArrayList<>();

            // Only heritable methods require a meaningful summary of subclasses.
            if (methodIsHeritable()) {
            }
        }

        public List<UserClassVertex> getInheritingSubclasses() {
            return inheritingSubclasses;
        }

        public List<UserClassVertex> getOverridingSubclasses() {
            return overridingSubclasses;
        }

        public List<UserClassVertex> getMiscellaneousSubclasses() {
            return miscellaneousSubclasses;
        }
    }
}
