package org.jahia.community.translation.deepl.actions;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.ajax.gwt.client.widget.Linker;
import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.community.translation.deepl.DeeplConstants;
import org.jahia.community.translation.deepl.service.DeepLTranslationResponse;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.utils.LanguageCodeConverters;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component(service = Action.class)
public class RequestTranslationAction extends Action {

    private static final Logger logger = LoggerFactory.getLogger(RequestTranslationAction.class);

    @Reference
    private DeepLTranslatorService deepLTranslatorService;

    @Activate
    public void activate() {
        setName("requestTranslationAction");
        setRequireAuthenticatedUser(true);
        setRequiredPermission("jcr:write_default+" + DeeplConstants.TRANSLATE_PERMISSION);
        setRequiredWorkspace(Constants.EDIT_WORKSPACE);
        setRequiredMethods("GET,POST");
    }

    @Override
    public ActionResult doExecute(final HttpServletRequest request, final RenderContext renderContext, final Resource resource, final JCRSessionWrapper session, Map<String, List<String>> parameters, final URLResolver urlResolver) throws Exception {
        final String currentBrowsedLanguage = LanguageCodeConverters.localeToLanguageTag(session.getLocale());
        final boolean allLanguages = getBooleanParameter(DeeplConstants.PROP_ALL_LANGUAGES, parameters);
        final boolean subTree = getBooleanParameter(DeeplConstants.PROP_SUB_TREE, parameters);
        final String sourceLanguage = getStringParameter(DeeplConstants.PROP_SRC_LANGUAGE, parameters, currentBrowsedLanguage);
        final String targetLanguage = getStringParameter(DeeplConstants.PROP_DEST_LANGUAGE, parameters);

        if (!allLanguages && StringUtils.isBlank(targetLanguage)) {
            logger.error("No target language specified");
            return ActionResult.BAD_REQUEST;
        }

        final DeepLTranslationResponse response = deepLTranslatorService.translate(resource.getNode(), subTree, sourceLanguage, targetLanguage, allLanguages);
        final JSONObject jsonObject = new JSONObject();
        final Map<String, String> message = new HashMap<>();
        message.put("title", "TITLE (DeepL action)");
        message.put("text", String.format("TEXT (DeepL action) , successful=%s , %s", response.isSuccessful(), response.getMessage()));
        message.put("messageBoxType", response.isSuccessful() ? "info" : "alert");
        jsonObject.put("messageDisplay", message);
        if (response.isSuccessful() && StringUtils.equals(targetLanguage, currentBrowsedLanguage))
            jsonObject.put("refreshData", Collections.singletonMap(Linker.REFRESH_MAIN, true));
        return new ActionResult(HttpServletResponse.SC_OK, null, jsonObject);
    }

    private <R> R getParameter(String key, Map<String, List<String>> parameters, Function<String, R> parser, R defaultValue) {
        final List<String> values = parameters.get(key);
        if (CollectionUtils.isEmpty(values)) return defaultValue;
        return parser.apply(values.get(0));
    }

    private boolean getBooleanParameter(String key, Map<String, List<String>> parameters) {
        return getParameter(key, parameters, Boolean::parseBoolean, Boolean.FALSE);
    }

    private String getStringParameter(String key, Map<String, List<String>> parameters) {
        return getStringParameter(key, parameters, StringUtils.EMPTY);
    }

    private String getStringParameter(String key, Map<String, List<String>> parameters, String defaultValue) {
        return getParameter(key, parameters, s -> s, defaultValue);
    }
}
