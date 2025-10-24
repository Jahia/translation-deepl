package org.jahia.community.translation.deepl.spring;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.uicomponents.bean.Visibility;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeepLVisibility extends Visibility {

    private static final Logger logger = LoggerFactory.getLogger(DeepLVisibility.class);

    private final List<String> languages;

    public DeepLVisibility() {
        languages = null;
    }

    public DeepLVisibility(String... languages) {
        this.languages = Arrays.asList(languages);
    }

    @Override
    public boolean getRealValue(JCRNodeWrapper contextNode, JahiaUser jahiaUser, Locale locale, HttpServletRequest request) {
        try {
            final Set<String> siteLanguages = contextNode.getResolveSite().getLanguages();
            return siteLanguages.size() > 1
                    && Optional.ofNullable(languages).map(siteLanguages::containsAll).orElse(Boolean.TRUE)
                    && super.getRealValue(contextNode, jahiaUser, locale, request);
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }
}
