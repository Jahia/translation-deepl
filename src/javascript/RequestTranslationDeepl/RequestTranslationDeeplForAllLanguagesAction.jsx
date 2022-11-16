import React from 'react';
import {useTranslation} from 'react-i18next';
import {useSelector} from 'react-redux';

export const RequestTranslationDeeplActionForAllLanguagesAction = ({path, render: Render, ...otherProps}) => {
    const {language} = useSelector(state => ({language: state.language, site: state.site, uilang: state.uilang}));

    const formData = new FormData();
    return <Render {...otherProps}
        onClick={async () => {
                    const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${language}${path}.requestTranslationForAllLanguagesAction.do`, {
                        method: 'POST',
                        body: formData
                    });
                }}/>

};

