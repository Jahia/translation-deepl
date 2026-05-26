import gql from 'graphql-tag';

const getMutationTranslateNode = gql`mutation TranslateNode($path:String!,$sourceLanguage:String!,$targetLanguage:String!) {
        jcr{
            mutateNode(pathOrId: $path) {
                translateNode(sourceLocale: $sourceLanguage, targetLocale: $targetLanguage){
                    message
                    successful
                }
            }
        }
    }`;

const suggestTranslationForLanguage = gql`query SuggestTranslationForLanguage($path:String!, $sourceLanguage:String!, $targetLanguage:String!) {
    jcr {
        nodeByPath(path: $path) {
            translationSuggestions(sourceLanguage: $sourceLanguage, targetLanguage: $targetLanguage) {
                fieldName
                translatedValue
            }
        }
    }
}`;
export {getMutationTranslateNode, suggestTranslationForLanguage};
