package org.jahia.community.translation.deepl.service;

public interface DeepLTranslationResponse {

    public boolean isSuccessful();

    public void setSuccessful(boolean state);

    public String getMessage();

    public void addMessage(String text);

    public DeepLTranslationResponse merge(DeepLTranslationResponse other);
}
