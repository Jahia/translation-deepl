package org.jahia.community.translation.deepl.service;

import javax.jcr.RepositoryException;
import org.jahia.services.content.JCRNodeWrapper;

public interface DeepLTranslatorService {

    DeepLTranslationResponse translate(JCRNodeWrapper node, boolean translateSubtree, String sourceLanguage, String targetLanguage, boolean allLanguages) throws RepositoryException;
}
