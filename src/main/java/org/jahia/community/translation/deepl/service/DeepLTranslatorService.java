package org.jahia.community.translation.deepl.service;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

public interface DeepLTranslatorService {

    DeepLTranslationResponse translate(JCRNodeWrapper node, boolean translateSubtree, String sourceLanguage, String targetLanguage, boolean allLanguages) throws RepositoryException;
}
