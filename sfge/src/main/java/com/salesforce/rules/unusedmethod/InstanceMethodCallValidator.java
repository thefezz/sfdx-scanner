package com.salesforce.rules.unusedmethod;

import com.salesforce.graph.vertex.MethodCallExpressionVertex;
import com.salesforce.graph.vertex.MethodVertex;
import com.salesforce.graph.vertex.UserClassVertex;
import java.util.List;
import java.util.Optional;

/**
 * Helper class for {@link com.salesforce.rules.UnusedMethodRule}. Used for determining whether an
 * instance method is ever invoked, e.g., {@code someMethod()}, {@code super.someMethod())}, etc.
 */
public class InstanceMethodCallValidator extends BaseMethodCallValidator {
    public InstanceMethodCallValidator(
            MethodVertex targetMethod, RuleStateTracker ruleStateTracker) {
        super(targetMethod, ruleStateTracker);
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
        return usageDetected(new InternalUsageDetector());
    }

    /**
     * For instance methods, an "external" usage is one that is performed on an instance of the
     * class by something else.<br>
     * This could be {@code someObj.method()} or {@code new SomeClass().method()} where the object
     * is:
     *
     * <ol>
     *   <li>The class where the method is defined.
     *   <li>A subclass that inherits the method from the class without overriding it.
     *   <li>A superclass whose implementation of the method is overridden by the class.
     * </ol>
     *
     * @return - true if the method is invoked externally.
     */
    @Override
    protected boolean externalUsageDetected() {
        return usageDetected(new ExternalUsageDetector());
    }

    private boolean usageDetected(UsageDetector detector) {
        // First, use the detector to check for usage via the host class.
        if (detector.detectsUsage(targetMethod.getDefiningType(), true, false)) {
            return true;
        }

        // If the method is heritable, then we also need to check for usage in subclasses.
        if (methodIsHeritable()) {
            boolean methodIsVirtual = targetMethod.isVirtual();
            // Start with a list of the immediate subclasses of the host class.
            // Note: We use a list to keep the search breadth-first. If it turns out that
            // depth-first is generally faster, we can switch to a stack instead.
            List<String> subclassNames =
                    ruleStateTracker.getSubclasses(targetMethod.getDefiningType());
            int i = 0;
            while (i < subclassNames.size()) {
                String subclassName = subclassNames.get(i);
                // We need to know whether this subclass merely inherits the method, or if it also
                // overrides it.
                boolean subclassOverridesMethod =
                        methodIsVirtual
                                && ruleStateTracker.classHasMatchingMethod(
                                        subclassName, targetMethod.getSignature(), false);
                // If we find a usage in this subclass, we're done.
                if (detector.detectsUsage(subclassName, !subclassOverridesMethod, true)) {
                    return true;
                }

                // Otherwise, if the subclass is itself extensible and didn't override the target
                // method, then its own subclasses must be added for consideration.
                if (!subclassOverridesMethod) {
                    subclassNames.addAll(ruleStateTracker.getSubclasses(subclassName));
                }
                // Finally, increment the index to the next subclass.
                i += 1;
            }
        }

        // If the method overrides an inherited method, then we also need to check the superclasses.
        if (targetMethod.isOverride()) {
            // Start with the first superclass. We know it exists because otherwise the code
            // wouldn't compile.
            Optional<UserClassVertex> superClassOptional =
                    ruleStateTracker.getSuperClass(targetMethod.getParentClass().get());
            String targetSignature = targetMethod.getSignature();
            while (superClassOptional.isPresent()) {
                UserClassVertex superClass = superClassOptional.get();
                // If the superclass uses the method, we're done.
                if (detector.detectsUsage(superClass.getName(), true, false)) {
                    return true;
                }
                // Otherwise, if this superclass isn't the original declaration of the target method
                // (i.e. it either doesn't have an implementation for that method or its
                // implementation is itself an override), we need to go up to the next superclass.
                Optional<MethodVertex> superMethodOptional =
                        ruleStateTracker.getMethodWithSignature(
                                superClass.getDefiningType(), targetSignature, false);
                if (!superMethodOptional.isPresent() || superMethodOptional.get().isOverride()) {
                    superClassOptional = ruleStateTracker.getSuperClass(superClass);
                } else {
                    // If this is the original source of the method and we found no usage, we're
                    // done.
                    break;
                }
            }
        }
        // If we're here, then we've exhausted all our options for this reference type, and can
        // return false.
        return false;
    }

    private abstract static class UsageDetector {
        abstract boolean detectsUsage(String definingType, boolean seekThis, boolean seekSuper);
    }

    private class InternalUsageDetector extends UsageDetector {
        @Override
        protected boolean detectsUsage(String definingType, boolean seekThis, boolean seekSuper) {
            // Get all method call expressions occurring in the target class.
            List<MethodCallExpressionVertex> potentialCalls =
                    ruleStateTracker.getMethodCallExpressionsByDefiningType(definingType);
            for (MethodCallExpressionVertex potentialCall : potentialCalls) {
                // An empty reference is an implicit `this` reference.
                boolean isThis =
                        potentialCall.isThisReference() || potentialCall.isEmptyReference();
                // Unless this reference is one of the types we're looking for, we skip it.
                // Note: I could have simplified this with De Morgan's Law, but prioritized
                // readability instead.
                if (!(seekThis && isThis) && !(seekSuper && potentialCall.isSuperReference())) {
                    continue;
                }
                // If the name is wrong, it's not a match.
                if (!potentialCall.getFullMethodName().equalsIgnoreCase(targetMethod.getName())) {
                    continue;
                }
                // If the parameters are wrong, it's not a match.
                if (!parametersAreValid(potentialCall)) {
                    continue;
                }
                // If all our other checks passed, it's a valid call.
                return true;
            }
            return false;
        }
    }

    private class ExternalUsageDetector extends UsageDetector {
        @Override
        protected boolean detectsUsage(String definingType, boolean seekThis, boolean seekSuper) {
            // TODO: IMPLEMENT THIS METHOD
            return false;
        }
    }
}
