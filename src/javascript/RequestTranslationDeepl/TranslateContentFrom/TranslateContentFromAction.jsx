import React, {useContext} from 'react';
import PropTypes from 'prop-types';
import {TranslateContentFromDialog} from './TranslateContentFromDialog';
import {getFullLanguageName} from './TranslateContentFrom.utils';
import {ComponentRendererContext} from '@jahia/ui-extender';
import {useFormikContext} from 'formik';
//import {useContentEditorContext} from '@jahia/jcontent';
import {useContentEditorContext} from '@jahia/content-editor';

export const TranslateContentFromActionComponent = ({render: Render, ...otherProps}) => {
    const {render, destroy} = useContext(ComponentRendererContext);
    const formik = useFormikContext();
    const {nodeData, lang, siteInfo} = useContentEditorContext();

    return (
        <Render {...otherProps}
                enabled={siteInfo.languages.length > 1 && nodeData.hasWritePermission}
                onClick={() => {
                    render('TranslateContentFromDialog', TranslateContentFromDialog, {
                        isOpen: true,
                        path: nodeData.path,
                        uuid: nodeData.uuid,
                        formik,
                        language: getFullLanguageName(siteInfo.languages, lang),
                        langLocale: lang,
                        availableLanguages: siteInfo.languages,
                        onCloseDialog: () => destroy('TranslateContentFromDialog')
                    });
                }}/>
    );
};

TranslateContentFromActionComponent.propTypes = {
    render: PropTypes.func.isRequired
}; 

export const TranslateContentFromAction = {
    component: TranslateContentFromActionComponent
};