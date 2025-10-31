package org.jahia.community.translation.deepl.service;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

public interface DeepLTranslatorService {

    DeepLTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;
    DeepLTranslationResponse translateProperty(JCRNodeWrapper node, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;
}
