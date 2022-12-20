package com.salesforce.graph.symbols.apex;

import com.salesforce.exception.ProgrammingException;
import com.salesforce.graph.ops.CloneUtil;
import com.salesforce.graph.symbols.ScopeUtil;
import com.salesforce.graph.symbols.SymbolProvider;
import com.salesforce.graph.vertex.*;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents the information that's getting iterated in a loop.
 * Built as a replacement to ApexForLoopValue - we are bringing the aspect of any ApexValue
 * to be an iterated value of another ApexValue.
 */
public final class IterationInfo implements com.salesforce.graph.DeepCloneable<IterationInfo> {
    private final ArrayList<ApexValue<?>> iteratedItems;
    private final ApexValue<?> iteratedOn;
    private final Typeable type;

    public IterationInfo(ChainedVertex iteratedValueVertex, ApexValueBuilder builder) {
        this(getApexValue(iteratedValueVertex, builder.getSymbolProvider()));
    }

    public IterationInfo(@Nullable ApexValue<?> apexValue) {
        this.iteratedOn = apexValue;
        this.iteratedItems = getIteratedItems(this.iteratedOn);
        this.type = getType(this.iteratedOn).orElse(null);
    }

    private IterationInfo(IterationInfo other) {
        this.iteratedOn = CloneUtil.cloneApexValue(other.iteratedOn);
        this.iteratedItems = CloneUtil.cloneArrayList(other.iteratedItems);
        this.type = other.type;
    }

    @Override
    public IterationInfo deepClone() {
        // TODO: should this be using DeepCloneContextProvider instead?
        return new IterationInfo(this);
    }

    public List<ApexValue<?>> getIteratedItems() {
        return Collections.unmodifiableList(iteratedItems);
    }

    public boolean isItemListIndeterminant() {
        if (iteratedOn == null) {
            return true;
        } else {
            return iteratedOn.isIndeterminant();
        }
    }

    public Optional<Typeable> getType() {
        return Optional.ofNullable(this.type);
    }

    private static ApexValue<?> getApexValue(@Nullable ChainedVertex iteratedValueVertex, SymbolProvider symbolProvider) {
        ApexValue<?> apexValue;
        if (iteratedValueVertex instanceof VariableExpressionVertex.ForLoop) {
            ChainedVertex forLoopValues =
                ((VariableExpressionVertex.ForLoop) iteratedValueVertex).getForLoopValues();
            apexValue =
                ScopeUtil.resolveToApexValue(symbolProvider, forLoopValues).orElse(null);
        } else if (iteratedValueVertex instanceof NewListLiteralExpressionVertex) {
            apexValue =
                ApexValueBuilder.get(symbolProvider).valueVertex(iteratedValueVertex).buildList();
        } else if (iteratedValueVertex instanceof NewSetLiteralExpressionVertex) {
            apexValue =
                ApexValueBuilder.get(symbolProvider).valueVertex(iteratedValueVertex).buildSet();
        } else {
            throw new ProgrammingException("Unhandled example of iterated vertex type: " + iteratedValueVertex);
        }

        return apexValue;
    }

    private Optional<Typeable> getType(@Nullable ApexValue<?> apexValue) {
        if (apexValue instanceof ApexIterableCollectionValue) {
            return ((ApexIterableCollectionValue) apexValue).getSubType();
        } else if (apexValue instanceof ApexSoqlValue) {
            final Optional<String> optObjectName = apexValue.getDefiningType();
            if (optObjectName.isPresent()) {
                return Optional.of(SyntheticTypedVertex.get(optObjectName.get()));
            }
        }
        return Optional.empty();
    }

    private ArrayList<ApexValue<?>> getIteratedItems(@Nullable ApexValue<?> apexValue) {
        final ArrayList<ApexValue<?>> iteratedItems = new ArrayList<>();
        if (apexValue instanceof ApexIterableCollectionValue) {
            // FIXME: Pass on sanitization information
//        AbstractSanitizableValue.copySanitization(collectionValue, this);
            for (ApexValue<?> item : ((ApexIterableCollectionValue)apexValue).getValues()) {
                iteratedItems.add(item);
            }
        }
        return iteratedItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IterationInfo that = (IterationInfo) o;
        return iteratedItems.equals(that.iteratedItems) && Objects.equals(iteratedOn, that.iteratedOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iteratedItems, iteratedOn);
    }

    @Override
    public String toString() {
        return "IterationInfo{" +
            "iteratedItems=" + iteratedItems +
            ", iteratedOn=" + iteratedOn +
            '}';
    }
}
