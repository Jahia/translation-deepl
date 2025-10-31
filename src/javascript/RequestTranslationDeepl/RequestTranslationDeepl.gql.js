import gql from 'graphql-tag';

function getQueryTranslationLocksAndPermissions(allLanguages, path) {
    const locks = allLanguages.map(l => `lock_${l.language}:nodeByPath(path: "${path}/j:translation_${l.language}") {lockInfo {details {type}}}`);
    const perms = allLanguages.map(l => `perm_${l.language}:hasPermission(permissionName: "jcr:modifyProperties_default_${l.language}")`);

    return gql`query GetTranslationLocksAndPermissions($path:String!) {
        jcr {
            nodeByPath(path: $path) {
                uuid
                workspace
                path
                lockInfo {
                    details {
                        language
                        owner
                        type
                    }
                }
                ${perms}
            }
            ${locks}
        }
    }`;
}

function getMutationTranslateNode() {
    return gql`mutation translateNode($path:String!,$sourceLocale:String!,$targetLocale:String!) {
        jcr{
            mutateNode(pathOrId: $path) {
                translateNode(sourceLocale: $sourceLocale, targetLocale: $targetLocale){
                    message
                    successful
                }
            }
        }
    }`;
}

function getMutationTranslateProperty() {
    return gql`mutation translateProperty($path:String!,$propertyName:String!,$sourceLocale:String!,$targetLocale:String!) {
        jcr{
            mutateNode(pathOrId: $path) {
                translateProperty(propertyName: $propertyName, sourceLocale: $sourceLocale, targetLocale: $targetLocale){
                    message
                    successful
                }
            }
        }
    }`;
}

export {getQueryTranslationLocksAndPermissions, getMutationTranslateNode, getMutationTranslateProperty};