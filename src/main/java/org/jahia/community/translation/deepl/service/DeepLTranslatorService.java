package org.jahia.community.translation.deepl.service;

import org.json.JSONArray;

public interface DeepLTranslatorService {

    JSONArray translate(String path, String srcLanguage, String destLanguage, boolean threeDotsMenu);
}
