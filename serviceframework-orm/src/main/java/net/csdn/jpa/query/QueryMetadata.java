package net.csdn.jpa.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable model-level contract: the declared methods for one entity.
 * Field eligibility, types, operators and sort tokens are already validated.
 */
public final class QueryMetadata {

    private final String modelName;
    private final List<QueryDescriptor> methods;

    public QueryMetadata(String modelName, List<QueryDescriptor> methods) {
        this.modelName = modelName;
        this.methods = Collections.unmodifiableList(new ArrayList<QueryDescriptor>(methods));
    }

    public String getModelName() {
        return modelName;
    }

    public List<QueryDescriptor> getMethods() {
        return methods;
    }

    public QueryDescriptor require(String methodName) {
        for (int i = 0; i < methods.size(); i++) {
            QueryDescriptor method = methods.get(i);
            if (method.getMethodName().equals(methodName)) {
                return method;
            }
        }
        throw new QueryDeclarationException(modelName, methodName, "-", "query method is not declared");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof QueryMetadata)) {
            return false;
        }
        QueryMetadata that = (QueryMetadata) other;
        return Objects.equals(modelName, that.modelName) && methods.equals(that.methods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, methods);
    }

    @Override
    public String toString() {
        return modelName + " " + methods;
    }
}
