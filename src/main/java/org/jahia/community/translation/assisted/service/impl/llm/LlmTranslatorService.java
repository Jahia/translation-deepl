package org.jahia.community.translation.assisted.service.impl.llm;

import org.apache.commons.lang3.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslationServicesManager;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.glossary.GlossaryService;
import org.jahia.community.translation.assisted.service.glossary.ResolvedGlossary;
import org.jahia.community.translation.assisted.service.impl.AssistedTranslationResponseImpl;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.modules.genai.api.CompletionRequest;
import org.jahia.modules.genai.api.CompletionResult;
import org.jahia.modules.genai.api.GenAiInfo;
import org.jahia.modules.genai.api.GenAiService;
import org.jahia.modules.genai.api.ResponseFormat;
import org.jahia.modules.genai.api.StopReason;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

/**
 * LLM-backed {@link TranslatorService}, calling the platform
 * <a href="https://github.com/Jahia/genai-connector">genai-connector</a> module through its
 * exported {@link GenAiService} facade.
 *
 * <p>This module owns the <em>prompt</em> (instructions template + glossary injection), the
 * batching and the JCR write-back; the connector owns the provider, the credentials and the
 * transport. Any provider the connector supports — Anthropic, OpenAI and OpenAI-compatible
 * endpoints, Mistral, Azure OpenAI — therefore works here, where this class used to speak the
 * OpenAI Responses API through an embedded SDK (see jahia-private#5366).</p>
 *
 * <p>The component registers with {@code service.ranking=10.0}, above DeepL's {@code 5.0}, but
 * ranking alone does not decide: consumers resolve translators through
 * {@link org.jahia.community.translation.assisted.service.TranslatorServiceResolver}, which
 * skips any translator whose {@link #isAvailable()} is {@code false}. So when the connector is
 * installed but not yet configured with a provider, translation falls back to DeepL.</p>
 */
@Component(service = TranslatorService.class,
        property = {"service.translation.provider=genai", "service.ranking=10.0"},
        configurationPid = SERVICE_CONFIG_FILE_NAME,
        immediate = true)
public class LlmTranslatorService implements TranslatorService {
    private static final Logger logger = LoggerFactory.getLogger(LlmTranslatorService.class);
    private static final String PROVIDER_KEY = "genai";
    private static final int TRANSLATION_BATCH_SIZE = 200;
    private static final int MAX_GLOSSARY_TERMS_IN_PROMPT = 200;
    private static final String JSON_RESPONSE_INSTRUCTION = "Return only a valid JSON object with exactly the same keys as the input and translated string values. Do not add markdown or explanations.";
    private static final String JSON_REPAIR_INSTRUCTION = "Your previous answer was not a valid JSON object. Answer again with a single valid JSON object and nothing else: no markdown code fences, no preamble, no trailing text.";
    private static final String JSON_INPUT_PREFIX = "json payload to translate:\n";

    /**
     * Used when neither {@code translation.llm.prompt} nor the legacy
     * {@code translation.openai.prompt} is configured. It matches the template shipped in
     * {@code org.jahia.community.translation.assisted.cfg}, so the service stays usable with a
     * configuration that only carries provider-independent keys.
     */
    private static final String DEFAULT_PROMPT_PATTERN = "You are a translation engine. Return only strict JSON object key->translated text. Preserve HTML tags/entities exactly. Preserve the JSON structure as received, translate only the values and keep the keys exactly as they are. The source language is {{sourceLanguage}} and the target language is {{targetLanguage}}. Those languages are expressed as ISO code with or without regional variations, if the variation is specified please ensure the translation match the regional specification. For example, if the target language is PT-BR, please ensure that the translation is in Brazilian Portuguese and not European Portuguese.";

    private String promptPattern;
    private String model;

    @Reference
    private GenAiService genAiService;

    @Reference
    private TranslationServicesManager translationServicesManager;

    @Reference
    private GlossaryService glossaryService;

    private static String ensureJsonKeywordInInput(String input) {
        String safeInput = input == null ? "" : input;
        if (StringUtils.containsIgnoreCase(safeInput, "json")) {
            return safeInput;
        }
        return JSON_INPUT_PREFIX + safeInput;
    }

    @Override
    public String getProviderKey() {
        // Report the provider actually serving the calls, so logs name Anthropic/OpenAI/… rather
        // than this module's generic key.
        return genAiService.info().map(GenAiInfo::provider).orElse(PROVIDER_KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the connector: {@code true} only when a provider is configured there with
     * a usable key. This is what makes the DeepL fallback work — the component itself is always
     * registered once the connector bundle is present.</p>
     */
    @Override
    public boolean isAvailable() {
        return genAiService.isAvailable();
    }

    @Activate
    @Modified
    public void activate(Map<String, String> properties) {
        Map<String, String> safeProperties = properties == null ? Collections.emptyMap() : properties;
        if (properties == null) {
            logger.warn("Missing configurations: {} — falling back to the built-in prompt", SERVICE_CONFIG_FILE_FULLNAME);
        }
        logger.info("Activating the LLM Translator Service (through the genai-connector module)");

        String pattern = firstNonBlank(safeProperties, TRANSLATION_LLM_PROMPT, LEGACY_TRANSLATION_OPENAI_PROMPT);
        if (pattern == null) {
            pattern = DEFAULT_PROMPT_PATTERN;
        } else if (!safeProperties.containsKey(TRANSLATION_LLM_PROMPT)) {
            logger.warn("Configuration key {} is deprecated, rename it to {} in {}",
                    LEGACY_TRANSLATION_OPENAI_PROMPT, TRANSLATION_LLM_PROMPT, SERVICE_CONFIG_FILE_FULLNAME);
        }
        promptPattern = pattern.replace("{{sourceLanguage}}", "{0}").replace("{{targetLanguage}}", "{1}");

        model = firstNonBlank(safeProperties, TRANSLATION_LLM_MODEL, LEGACY_TRANSLATION_OPENAI_MODEL);
        if (model != null && !safeProperties.containsKey(TRANSLATION_LLM_MODEL)) {
            logger.warn("Configuration key {} is deprecated, rename it to {} in {}",
                    LEGACY_TRANSLATION_OPENAI_MODEL, TRANSLATION_LLM_MODEL, SERVICE_CONFIG_FILE_FULLNAME);
        }
        logger.info("LLM translations configured with model {}", model == null ? "<genai-connector default>" : model);
    }

    private static String firstNonBlank(Map<String, String> properties, String... keys) {
        for (String key : keys) {
            String value = StringUtils.trimToNull(properties.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public AssistedTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(node.getSession().getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final JCRSessionWrapper sessionTarget = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
        final JCRNodeWrapper localizedNode = session.getNodeByIdentifier(node.getIdentifier());
        final TranslationData data = new TranslationData();

        translationServicesManager.buildDataToTranslate(localizedNode, data, true, true);
        ResolvedGlossary resolvedGlossary = glossaryService.resolve(localizedNode, sourceLanguage, targetLanguage);
        List<TranslatedField> translatedFields = getTranslatedFieldList(sourceLanguage, targetLanguage, data, true, resolvedGlossary.getTerms());
        // We need to transform this list to handle multivalued fields have their fieldname ending with ___index___
        // Each of those field values need to be stored in a List at the same original index
        Map<String, TranslatedField> translatedFieldMap = translationServicesManager.getTranslatedFieldMap(translatedFields);
        try {
            translatedFieldMap.values().forEach(field -> translationServicesManager.copyFieldValue(field, sessionTarget));
            sessionTarget.save();
            // Load the translated node in target language to render the new title
            String displayableName = sessionTarget.getNodeByIdentifier(node.getIdentifier()).getDisplayableName();
            Locale targetLocale = LanguageCodeConverters.getLocaleFromCode(targetLanguage);
            Locale sourceLocale = LanguageCodeConverters.getLocaleFromCode(sourceLanguage);
            MessageFormat languageInfo = new MessageFormat("{0} ({1})");
            return new AssistedTranslationResponseImpl(true, MessageFormat.format("Page/resource {0} translated successfully from {1} to {2}",
                    displayableName,
                    StringUtils.capitalize(languageInfo.format(new Object[]{sourceLocale.getDisplayLanguage(targetLocale), sourceLocale.getDisplayLanguage(sourceLocale)})),
                    StringUtils.capitalize(languageInfo.format(new Object[]{targetLocale.getDisplayLanguage(targetLocale), targetLocale.getDisplayLanguage(sourceLocale)}))));
        } catch (RepositoryException e) {
            logger.error("Error when getting session for target language {}", targetLanguage, e);
        }
        return null;
    }

    @Override
    public AssistedTranslationResponse translateProperty(JCRNodeWrapper node, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        throw new UnsupportedOperationException("translateProperty is not supported in LlmTranslatorService, please use translateNode instead");
    }

    @Override
    public List<TranslatedField> suggestTranslationForNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        // Ensure we get the nod ein the source language to be able to suggest translations
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(node.getSession().getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final JCRNodeWrapper localizedNode = session.getNodeByIdentifier(node.getIdentifier());
        final TranslationData data = new TranslationData();

        translationServicesManager.buildDataToTranslate(localizedNode, data, true, false);
        // Build the JSON to translate from data.getTexts(), a map of key->text where key is the JCR path
        // to the property (e.g. /a/title) and value is the text to translate (e.g. "Hello <b>world</b>")
        ResolvedGlossary resolvedGlossary = glossaryService.resolve(localizedNode, sourceLanguage, targetLanguage);
        return getTranslatedFieldList(sourceLanguage, targetLanguage, data, false, resolvedGlossary.getTerms());
    }

    private List<TranslatedField> getTranslatedFieldList(String sourceLanguage, String targetLanguage, TranslationData data, boolean subtree, Map<String, String> glossaryTerms) {
        String sourceLanguageMapped = translationServicesManager.getTargetLanguages().getOrDefault(sourceLanguage, sourceLanguage);
        String targetLanguageMapped = translationServicesManager.getTargetLanguages().getOrDefault(targetLanguage, targetLanguage);
        String baseSystemPrompt = MessageFormat.format(promptPattern, sourceLanguageMapped, targetLanguageMapped)
                + "\n" + JSON_RESPONSE_INSTRUCTION;
        List<Map.Entry<String, String>> textEntries = new ArrayList<>(data.getTexts().entrySet());
        if (textEntries.isEmpty()) {
            return List.of();
        }

        // Every batch is self-contained: the OpenAI-only `previous_response_id` chaining was dropped
        // with the SDK (jahia-private#5366). The full system prompt is re-sent per batch, which the
        // former chaining also did — it only saved re-sending the previous *payloads*.
        Map<String, String> translatedValues = new HashMap<>();

        for (int startIdx = 0; startIdx < textEntries.size(); startIdx += TRANSLATION_BATCH_SIZE) {
            int endIdx = Math.min(startIdx + TRANSLATION_BATCH_SIZE, textEntries.size());
            Map<String, String> batchTexts = new LinkedHashMap<>();
            textEntries.subList(startIdx, endIdx).forEach(entry -> batchTexts.put(entry.getKey(), entry.getValue()));

            processGlossaryTerms(glossaryTerms, batchTexts, translatedValues);

            Map<String, String> textsToTranslate = new LinkedHashMap<>();
            processTextToTranslates(batchTexts, translatedValues, textsToTranslate);

            if (textsToTranslate.isEmpty()) {
                continue;
            }

            // org.json escapes "</" as "<\/" (a safety measure for embedding JSON in HTML <script> tags).
            // Sending that to the model makes it preserve the literal backslash and re-escape it, so closing
            // tags come back as "<\/p>". Undo the over-escaping so the model sees clean HTML.
            String requestJson = new JSONObject(textsToTranslate).toString().replace("<\\/", "</");
            if (logger.isDebugEnabled()) {
                logger.debug("Calling the genai-connector with requested translation batch [{}, {})", startIdx, endIdx);
            }

            String systemPrompt = baseSystemPrompt + buildGlossaryInstruction(glossaryTerms, textsToTranslate.values());
            Optional<JSONObject> responseJson = complete(systemPrompt, requestJson).flatMap(this::parseJsonObject);

            if (responseJson.isEmpty()) {
                if (logger.isWarnEnabled()) {
                    logger.warn("The response for batch [{}, {}) was not valid JSON, retrying once", startIdx, endIdx);
                }
                // The repair call cannot refer to "the previous answer" the way the chained Responses API
                // did, so it re-sends the same payload with a hardened instruction instead.
                responseJson = complete(systemPrompt + "\n" + JSON_REPAIR_INSTRUCTION, requestJson).flatMap(this::parseJsonObject);
            }

            if (responseJson.isEmpty()) {
                logger.error("The model did not return valid JSON for translation batch [{}, {})", startIdx, endIdx);
                return List.of();
            }

            processJsonResponse(responseJson, textsToTranslate, translatedValues);
        }

        return translationServicesManager.getTranslatedFieldList(data, subtree, translatedValues);
    }

    /**
     * Runs one completion through the connector in native JSON-object mode.
     *
     * @return the generated text, or empty when the model produced nothing usable
     */
    private Optional<String> complete(String instructions, String input) {
        CompletionRequest.Builder request = CompletionRequest.builder()
                .instructions(instructions)
                // Native JSON mode of some providers (OpenAI's in particular) requires the word "json"
                // to appear in the prompt; the payload alone does not always contain it.
                .input(ensureJsonKeywordInInput(input))
                .responseFormat(ResponseFormat.JSON);
        if (model != null) {
            request.model(model);
        }
        CompletionResult result = genAiService.complete(request.build());
        if (result.stopReason() == StopReason.MAX_TOKENS) {
            logger.warn("The model stopped on its output-token limit: the JSON is truncated and will not parse. "
                    + "Raise the max output tokens in the genai-connector configuration, or lower the translation batch size.");
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Translation batch served by {}/{} ({} input tokens, {} output tokens)",
                    result.provider(), result.model(), result.usage().inputTokens(), result.usage().outputTokens());
        }
        return Optional.of(result.text()).filter(StringUtils::isNotBlank);
    }

    private static void processJsonResponse(Optional<JSONObject> responseJson, Map<String, String> textsToTranslate, Map<String, String> translatedValues) {
        if (responseJson.isPresent()) {
            Map<String, Object> responseJsonMap = responseJson.get().toMap();
            textsToTranslate.keySet().forEach(key -> {
                if (responseJsonMap.containsKey(key) && responseJsonMap.get(key) != null) {
                    translatedValues.put(key, responseJsonMap.get(key).toString());
                }
            });
        } else {
            logger.error("No valid JSON response to process");
        }
    }

    private static void processTextToTranslates(Map<String, String> batchTexts, Map<String, String> translatedValues, Map<String, String> textsToTranslate) {
        batchTexts.forEach((key, value) -> {
            if (!translatedValues.containsKey(key)) {
                textsToTranslate.put(key, value);
            }
        });
    }

    private static void processGlossaryTerms(Map<String, String> glossaryTerms, Map<String, String> batchTexts, Map<String, String> translatedValues) {
        batchTexts.forEach((key, sourceText) -> {
            String glossaryMatch = glossaryTerms.get(sourceText);
            if (StringUtils.isNotBlank(glossaryMatch)) {
                translatedValues.put(key, glossaryMatch);
            }
        });
    }

    private String buildGlossaryInstruction(Map<String, String> glossaryTerms, Collection<String> batchTexts) {
        if (glossaryTerms == null || glossaryTerms.isEmpty()) {
            return "";
        }

        List<String> nonEmptyBatchTexts = batchTexts.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (nonEmptyBatchTexts.isEmpty()) {
            return "";
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(glossaryTerms.entrySet());
        // Prefer longer terms first so specific phrases are not dropped when capped.
        entries.sort((a, b) -> Integer.compare(StringUtils.length(b.getKey()), StringUtils.length(a.getKey())));

        Map<String, String> limitedTerms = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.stream().filter(e -> StringUtils.isNotBlank(StringUtils.trimToEmpty(e.getKey()))).collect(Collectors.toList())) {
            String sourceTerm = StringUtils.trimToEmpty(entry.getKey());
            boolean relevant = isRelevantGlossaryTerm(sourceTerm, nonEmptyBatchTexts);
            if (relevant) {
                limitedTerms.put(sourceTerm, entry.getValue());
            }
            if (limitedTerms.size() >= MAX_GLOSSARY_TERMS_IN_PROMPT) {
                break;
            }
        }

        if (limitedTerms.isEmpty()) {
            return "";
        }

        return "\nApply this json glossary (source->target) when terms appear: " + new JSONObject(limitedTerms);
    }

    private boolean isRelevantGlossaryTerm(String sourceTerm, Collection<String> batchTexts) {
        String[] tokens = StringUtils.trimToEmpty(sourceTerm).split("\\s+");
        if (tokens.length == 0) {
            return false;
        }

        String joinedTokens = Arrays.stream(tokens)
                .filter(StringUtils::isNotBlank)
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
        if (StringUtils.isBlank(joinedTokens)) {
            return false;
        }

        Pattern termPattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + joinedTokens + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return batchTexts.stream().anyMatch(text -> termPattern.matcher(text).find());
    }

    private Optional<JSONObject> parseJsonObject(String content) {
        try {
            return Optional.of(new JSONObject(content));
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to parse the model output as JSON: {}", content, e);
            }
            return Optional.empty();
        }
    }
}
