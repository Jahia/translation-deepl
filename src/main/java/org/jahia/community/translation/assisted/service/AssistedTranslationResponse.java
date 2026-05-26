package org.jahia.community.translation.assisted.service;

public interface AssistedTranslationResponse {

    boolean isSuccessful();

    void setSuccessful(boolean state);

    String getMessage();

    void addMessage(String text);

    AssistedTranslationResponse merge(AssistedTranslationResponse other);
}
