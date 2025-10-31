import React, {useContext, useMemo} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {ComponentRendererContext} from '@jahia/ui-extender';
import {RequestTranslationDeepl} from './RequestTranslationDeepl';
import {useContentEditorContext, useContentEditorSectionContext} from '@jahia/jcontent';
import {useNodeChecks} from '@jahia/data-helper';
import {useFormikContext} from 'formik';

export const RequestTranslationDeeplForAllLanguagesActionComponent = ({
    render: Render,
    loading: Loading,
    ...others
}) => {
    const editorContext = useContentEditorContext();
    const editorSectionContext = useContentEditorSectionContext();
    const formikContext = useFormikContext();
    const componentRenderer = useContext(ComponentRendererContext);

    // Load namespace
    useTranslation('translation-deepl');

    const res = useNodeChecks(
        {path: editorContext.nodeData.path},
        {
            requireModuleInstalledOnSite: ['translation-deepl']
        }
    );

    const fields = useMemo(() => {
        const fieldNames = editorSectionContext.sections.flatMap(section =>
            section.fieldSets.flatMap(fieldset =>
                fieldset.fields
                    .filter(field =>
                        field?.i18n === true &&
                        field?.readOnly === false &&
                        field?.name !== undefined
                    )
                    .map(field => field.name)
            )
        );
        return Object.fromEntries(
            fieldNames.map(name => [name, formikContext.values[name] ?? ''])
        );
    }, [editorSectionContext.sections, formikContext.values]);

    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    if (!res.checksResult || editorContext.siteInfo.languages.length <= 1) {
        return false;
    }

    const enabled = !editorContext.nodeData?.lockedAndCannotBeEdited;

    return (
        <Render
            {...others}
            isVisible
            enabled={enabled}
            onClick={() => {
                componentRenderer.render('requestTranslationDeeplForAllLanguages', RequestTranslationDeepl, {
                    path: editorContext.nodeData.path,
                    language: editorContext.lang,
                    siteLanguages: editorContext.siteInfo.languages,
                    isOpen: true,
                    isNew: editorContext?.nodeData?.newName !== undefined,
                    setI18nContext: editorContext.setI18nContext,
                    fields,
                    onClose: () => {
                        componentRenderer.setProperties('requestTranslationDeeplForAllLanguages', {isOpen: false});
                    },
                    onExited: () => {
                        componentRenderer.destroy('requestTranslationDeeplForAllLanguages');
                    }
                });
            }}
        />
    );
};

RequestTranslationDeeplForAllLanguagesActionComponent.propTypes = {
    formik: PropTypes.object.isRequired,
    editorContext: PropTypes.object.isRequired,
    render: PropTypes.func.isRequired,
    loading: PropTypes.func
};
