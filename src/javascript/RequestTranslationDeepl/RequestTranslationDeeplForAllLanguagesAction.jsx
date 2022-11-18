import React from 'react';
import {useTranslation} from 'react-i18next';
import {useSelector} from 'react-redux';
import {useNodeInfo} from '@jahia/data-helper';

export const RequestTranslationDeeplActionForAllLanguagesAction = ({path, render: Render, subTree, allLanguages, ...otherProps}) => {
    const {t} = useTranslation('translation-deepl');
    const {language} = useSelector(state => ({language: state.language, site: state.site, uilang: state.uilang}));
    const {node, nodeLoading: nodeLoading} = useNodeInfo({path: path, language: language}, {getDisplayName: true});

    if (nodeLoading || !node) {
        return null;
    }
    return <Render {...otherProps}
        buttonLabel={t('label.requestTranslationForAllLanguages', {displayName: node.displayName})}
        onClick={async () => {
                    const formData = new FormData();
                    formData.append('subTree', subTree);
                    formData.append('allLanguages', allLanguages);
                    const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do`, {
                        method: 'POST',
                        body: formData
                    });
                }}/>

};

