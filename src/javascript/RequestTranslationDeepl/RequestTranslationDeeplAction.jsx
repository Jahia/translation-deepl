import React from 'react';
import {useTranslation} from 'react-i18next';
import {useSiteInfo} from '@jahia/data-helper';
import {useSelector} from 'react-redux';

export const RequestTranslationDeeplAction = ({path, render: Render, ...otherProps}) => {
    const {t} = useTranslation('translation-deepl');
    const {language, site} = useSelector(state => ({language: state.language, site: state.site}));
    const {siteInfo, loading} = useSiteInfo({siteKey: site, displayLanguage: language});

    if (loading || !siteInfo) {
        return null;
    }
    return siteInfo.languages.filter(lang => lang.language !== language).map(lang => (
                <Render {...otherProps}
                    buttonLabel={t('label.requestTranslation', {languageDisplay: lang.displayName})}
                    onClick={async () => {
                            const formData = new FormData();
                            formData.append('destLanguage', lang.language);
                            const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationAction.do?`, {
                                method: 'POST',
                                body: formData
                            });
                        }}/>
                ))
};

