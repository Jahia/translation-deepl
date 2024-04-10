package org.jahia.community.translation.deepl.service;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

public interface DeepLTranslatorService {

    void translate(JCRNodeWrapper node, boolean translateSubtree, String targetLanguage, boolean allLanguages) throws RepositoryException;
}
