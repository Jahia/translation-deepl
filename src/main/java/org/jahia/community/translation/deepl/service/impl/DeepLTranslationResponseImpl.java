package org.jahia.community.translation.deepl.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jahia.community.translation.deepl.service.DeepLTranslationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeepLTranslationResponseImpl implements DeepLTranslationResponse {

    private static final Logger logger = LoggerFactory.getLogger(DeepLTranslationResponseImpl.class);

    private final List<String> messages = new ArrayList<>();
    private boolean state;

    public DeepLTranslationResponseImpl(boolean state, String reason) {
        this.state = state;
        addMessage(reason);
    }

    @Override
    public boolean isSuccessful() {
        return state;
    }

    @Override
    public void setSuccessful(boolean state) {
        this.state = state;
    }

    @Override
    public String getMessage() {
        return String.join("\n", messages);
    }

    @Override
    public void addMessage(String text) {
        if (StringUtils.isNotBlank(text)) {
            messages.add(text);
        }
    }

    @Override
    public DeepLTranslationResponse merge(DeepLTranslationResponse other) {
        state = state || other.isSuccessful();
        messages.addAll(((DeepLTranslationResponseImpl) other).messages);
        return this;
    }
}
