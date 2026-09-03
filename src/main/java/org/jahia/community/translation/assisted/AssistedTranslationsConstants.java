package org.jahia.community.translation.assisted;

public class AssistedTranslationsConstants {

    public static final String SERVICE_CONFIG_FILE_NAME = "org.jahia.community.translation.assisted";
    public static final String SERVICE_CONFIG_FILE_NAME_DEEPL = SERVICE_CONFIG_FILE_NAME + ".deepl";
    public static final String SERVICE_CONFIG_FILE_FULLNAME = SERVICE_CONFIG_FILE_NAME + ".cfg";
    public static final String DEEPL_API_KEY = "translation.deepl.api.key";
    public static final String TRANSLATION_LLM_PROMPT = "translation.llm.prompt";
    public static final String TRANSLATION_LLM_MODEL = "translation.llm.model";
    public static final String TRANSLATION_GLOSSARY_RELATIVE_PATH = "translation.glossary.relative.path";
    public static final String TRANSLATION_GLOSSARY_FILE_PATTERN = "translation.glossary.file.pattern";
    public static final String PROP_PREFIX_TARGET_LANGUAGES = "targetLanguages.";

    // Legacy keys, kept for the migration to the genai-connector module (see jahia-private#5366).
    // The API key is ignored — provider credentials now live in the connector's own configuration.
    // The prompt and model keys are still read, as a fallback for the renamed provider-neutral ones.
    public static final String LEGACY_OPENAI_CONFIG_FILE_NAME = SERVICE_CONFIG_FILE_NAME + ".openai";
    public static final String LEGACY_OPENAI_API_KEY = "translation.openai.api.key";
    public static final String LEGACY_TRANSLATION_OPENAI_PROMPT = "translation.openai.prompt";
    public static final String LEGACY_TRANSLATION_OPENAI_MODEL = "translation.openai.model";

    public static final String MSG_NOTHING_TO_TRANSLATE = "Nothing to translate in %s";
    public static final String SLASH = "/";

    private AssistedTranslationsConstants() {

    }

}
