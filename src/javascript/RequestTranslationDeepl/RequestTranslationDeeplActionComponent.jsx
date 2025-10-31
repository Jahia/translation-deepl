import React, {useContext} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {ComponentRendererContext} from '@jahia/ui-extender';
import {RequestTranslationDeepl} from './RequestTranslationDeepl';
import {useFormikContext} from 'formik';
import {useContentEditorContext} from '@jahia/jcontent';
import {useNodeChecks} from '@jahia/data-helper';

export const RequestTranslationDeeplActionComponent = ({
    field,
    render: Render,
    loading: Loading,
    ...others
}) => {
    const formik = useFormikContext();
    const editorContext = useContentEditorContext();
    const componentRenderer = useContext(ComponentRendererContext);

    // Load namespace
    useTranslation('translation-deepl');

    const path= editorContext.nodeData? editorContext.nodeData.path: field;
    const res = useNodeChecks(
        {path: editorContext.nodeData.path},
        {
            requireModuleInstalledOnSite: ['translation-deepl']
        }
    );

    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    if (!res.checksResult || !field.i18n || editorContext.siteInfo.languages.length <= 1) {
        return false;
    }

    const enabled = !editorContext.nodeData?.lockedAndCannotBeEdited;

    const fieldValue = formik.values[field.name] ?? '';

    return (
        <Render
            {...others}
            isVisible
            enabled={enabled}
            onClick={() => {
                componentRenderer.render('requestTranslationDeepl', RequestTranslationDeepl, {
                    path: path,
                    field: field,
                    fieldValue: fieldValue,
                    language: editorContext.lang,
                    siteLanguages: editorContext.siteInfo.languages,
                    isOpen: true,
                    setI18nContext: editorContext.setI18nContext,
                    onClose: () => {
                        componentRenderer.setProperties('requestTranslationDeepl', {isOpen: false});
                    },
                    onExited: () => {
                        componentRenderer.destroy('requestTranslationDeepl');
                    }
                });
            }}
        />
    );
};

RequestTranslationDeeplActionComponent.propTypes = {
    formik: PropTypes.object.isRequired,

    editorContext: PropTypes.object.isRequired,

    field: PropTypes.object.isRequired,

    render: PropTypes.func.isRequired,

    loading: PropTypes.func
};
