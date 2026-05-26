package org.jahia.community.translation.assisted.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;

/**
 * GraphQL DTO adapter for {@link AssistedTranslationResponse}.
 * Keeps GraphQL infrastructure concerns out of the service layer.
 */
@GraphQLName("AssistedTranslationResponse")
@GraphQLDescription("Result returned by an assisted translation operation")
public class GqlAssistedTranslationResponse {

    private final AssistedTranslationResponse response;

    public GqlAssistedTranslationResponse(AssistedTranslationResponse response) {
        this.response = response;
    }

    @GraphQLField
    @GraphQLDescription("Whether the translation completed successfully")
    public boolean isSuccessful() {
        return response.isSuccessful();
    }

    @GraphQLField
    @GraphQLDescription("Human-readable message describing the translation result")
    public String getMessage() {
        return response.getMessage();
    }
}

