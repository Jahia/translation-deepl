import React from 'react';
import {useTranslation} from 'react-i18next';
import {useSiteInfo, useNodeInfo, useNodeChecks} from '@jahia/data-helper';
import {useSelector} from 'react-redux';
import {toIconComponentFunction} from '@jahia/moonstone';

export const RequestTranslationDeeplAction = ({path, render: Render, ...otherProps}) => {
    const {t} = useTranslation('translation-deepl');
    const {language, site} = useSelector(state => ({language: state.language, site: state.site}));
    const {siteInfo, loading} = useSiteInfo({siteKey: site, displayLanguage: language});
    const {node, nodeLoading: nodeLoading} = useNodeInfo({path: path, language: language}, {getDisplayName: true});
    const {checksResult} = useNodeChecks({path, language}, {requiredPermission: 'deeplTranslate'});
    const sharedProps = {"enabled": checksResult, ...otherProps}

    if (loading || !siteInfo || nodeLoading || !node) {
        return null;
    }

    if (otherProps.subTree && otherProps.allLanguages) {
        return <Render {...sharedProps}
            buttonLabel={t('label.requestTranslationTreeForAllLanguages', {displayName: node.displayName})}
            onClick={async () => {
                            const formData = new FormData();
                            formData.append('subTree', otherProps.subTree);
                            formData.append('allLanguages', otherProps.allLanguages);
                            const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
                                method: 'POST',
                                body: formData
                            });
                            ;
                        }}/>
    } else if (otherProps.subTree && !otherProps.allLanguages) {
        return siteInfo.languages.filter(lang => lang.language !== language).map(lang => (
                    <Render {...sharedProps}
                        buttonLabel={t('label.requestTranslationTree', {languageDisplay: lang.displayName, displayName: node.displayName})}
                        onClick={async () => {
                                const formData = new FormData();
                                formData.append('subTree', otherProps.subTree);
                                formData.append('allLanguages', otherProps.allLanguages);
                                formData.append('destLanguage', lang.language);
                                const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
                                    method: 'POST',
                                    body: formData
                                });
                            }}/>
                    ))
    } else if (!otherProps.subTree && otherProps.allLanguages) {
        return <Render {...sharedProps}
            buttonLabel={t('label.requestTranslationForAllLanguages', {displayName: node.displayName})}
            onClick={async () => {
                            const formData = new FormData();
                            formData.append('subTree', otherProps.subTree);
                            formData.append('allLanguages', otherProps.allLanguages);
                            const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
                                method: 'POST',
                                body: formData
                            });
                            ;
                        }}/>
    } else {
        return siteInfo.languages.filter(lang => lang.language !== language).map(lang => (
                    <Render {...sharedProps}
                        buttonLabel={t('label.requestTranslation', {languageDisplay: lang.displayName, displayName: node.displayName})}
                        onClick={async () => {
                                const formData = new FormData();
                                formData.append('subTree', otherProps.subTree);
                                formData.append('allLanguages', otherProps.allLanguages);
                                formData.append('destLanguage', lang.language);
                                const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
                                    method: 'POST',
                                    body: formData
                                });
                            }}/>
                    ))
}

};


//    if (allLanguages) {
//        return <Render {...otherProps}
//            buttonLabel={t('label.requestTranslationForAllLanguages', {displayName: node.displayName})}
//            onClick={async () => {
//                            const formData = new FormData();
//                            formData.append('subTree', subTree);
//                            formData.append('allLanguages', allLanguages);
//                            const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
//                                method: 'POST',
//                                body: formData
//                            });;
//                        }}/>;
//
//    } else {
//        return siteInfo.languages.filter(lang => lang.language !== language).map(lang => (
//                    <Render {...otherProps}
//                        buttonLabel={t('label.requestTranslation', {languageDisplay: lang.displayName, displayName: node.displayName})}
//                        onClick={async () => {
//                                const formData = new FormData();
//                                formData.append('subTree', subTree);
//                                formData.append('allLanguages', allLanguages);
//                                formData.append('destLanguage', lang.language);
//                                const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
//                                    method: 'POST',
//                                    body: formData
//                                });
//                            }}/>
//                    ));
//}

//};
