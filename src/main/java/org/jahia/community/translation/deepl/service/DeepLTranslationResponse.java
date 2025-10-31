package org.jahia.community.translation.deepl.service;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

public interface DeepLTranslationResponse {

    @GraphQLField
    @GraphQLDescription("Get the status of the response")
    public boolean isSuccessful();

    public void setSuccessful(boolean state);

    @GraphQLField
    @GraphQLDescription("Get the DeepL response message")
    public String getMessage();

    public void addMessage(String text);

    public DeepLTranslationResponse merge(DeepLTranslationResponse other);
}
