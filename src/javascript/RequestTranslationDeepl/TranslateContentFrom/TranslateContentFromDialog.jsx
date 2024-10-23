import React, { useState } from 'react';
import { Dialog, DialogActions, DialogContent, DialogTitle } from '@material-ui/core';
import { Button, Dropdown, Typography, Warning } from '@jahia/moonstone';
import * as PropTypes from 'prop-types';
import { useTranslation } from 'react-i18next';
import styles from './TranslateContentFromDialog.scss';
import { LoaderOverlay } from '../../DesignSystem/LoaderOverlay';
import {useApolloClient} from '@apollo/client';
import {EditFormQuery} from './edit.gql-queries';
import {getI18nFieldAndValues} from './TranslateContentFrom.utils';
import {Constants} from './ContentEditor.constants';

export const TranslateContentFromDialog = ({
    language,
    availableLanguages,
    isOpen,
    onCloseDialog,
    path,
    uuid,
    langLocale,
    formik
}) => {
    const client = useApolloClient();

    const getDataFromSelectedLanguage = async _language => {
        const variables = {
            uilang: _language,
            formik,
            language: _language,
            uuid: uuid,
            writePermission: `jcr:modifyProperties_default_${_language}`,
            childrenFilterTypes: Constants.childrenFilterTypes
        };

        const formAndData = await client.query({query: EditFormQuery, variables: variables});

        return getI18nFieldAndValues(formAndData);
    };

    const { t } = useTranslation('translation-deepl');
    const handleCancel = () => {
        onCloseDialog();
    };

    const defaultOption = {
        label: t('translation-deepl:label.dialog.translationFrom.defaultValue'),
        value: 'void'
    };

    const [currentOption, setCurrentOption] = useState(defaultOption);

    const handleOnChange = (e, item) => {
        setCurrentOption(item);
        return true;
    };

    const [loadingQuery, setLoadingQuery] = useState(false);
    const [error, setError] = useState(null);
    const [results, setResults] = useState(null);


    const handleClick = async () => {
        setLoadingQuery(true);
        setError(null);
        try {
            const formData = new FormData();
            formData.append('subTree', false);
            formData.append('allLanguages',false);
            formData.append('srcLanguage', currentOption.value);
            formData.append('destLanguage', langLocale);
            formData.append('3dotsMenu', true);


            const response = await fetch(`${contextJsParameters.contextPath}/cms/editframe/default/${langLocale}${path}.requestTranslationAction.do`, {
                method: 'POST',
                headers: { Accept: 'application/json' },
                body: formData
            });
            

            if (!response.ok) {
                const errorMessage = `HTTP error! status: ${response.status}`;
                const errorBody = await response.text();
                console.error(errorMessage, errorBody);
                throw new Error(errorMessage);
            }

            let results;
            try {
                results = await response.json();
            } catch (error) {
                console.error('Error parsing JSON:', error);
                throw new Error('Failed to parse JSON response');
            }

            if (results.resultCode === 200) {
                setResults(results);
                results.properties.forEach(property => {
                    formik.setFieldValue(property.name, property.value);
                    console.log(`Name: ${property.name}, Value: ${property.value}`);
                });

            } else {
                setError(`Error: ${results.resultCode}`);
            }
        } catch (error) {
            console.error('Error DeepL Tranlating Content:', error);

            let errorMessage = 'An error occurred while trying to DeepL translate content. Please check the console for more details.';
            if (error instanceof Error) {
                errorMessage = error.message;
            }

            setError(errorMessage);
        } finally {
            setLoadingQuery(false);
        }
        onCloseDialog();
    };

    let isApplyDisabled = defaultOption.value === currentOption.value;

    return (
        <Dialog fullWidth
            aria-labelledby="alert-dialog-slide-title"
            open={isOpen}
            maxWidth="sm"
            classes={{ paper: styles.dialog_overflowYVisible }}
            onClose={onCloseDialog}
        >
            <DialogTitle id="dialog-language-title" className={styles.dialogTitleContainer}>
                <img
                    src="https://static.deepl.com/img/logo/deepl-logo-blue.svg"
                    alt="DeepL Logo"
                    className={styles.dialogLogo}
                />
                <img
                    src="https://static.deepl.com/img/logo/deepl-logo-text-blue.svg"
                    alt="DeepL Text"
                    className={styles.dialogLogoText}
                />
                <Typography isUpperCase variant="heading" weight="bold" className={styles.dialogTitle}>
                    {t('translation-deepl:label.dialog.translationFrom.dialogTitle')}
                </Typography>
                <div className={styles.dialogTitleTextContainer}>
                    <Typography variant="subheading" className={styles.dialogSubTitle}>
                        {t('translation-deepl:label.dialog.translationFrom.dialogSubTitle')}
                    </Typography>
                </div>
            </DialogTitle>
            <DialogContent className={styles.dialogContent} classes={{ root: styles.dialogContent_overflowYVisible }}>
                <div className={styles.loaderOverlayWrapper}>
                    <LoaderOverlay status={loadingQuery} />
                </div>
                <Typography className={styles.copyFromLabel}>
                    {t('translation-deepl:label.dialog.translationFrom.listLabel')}
                </Typography>
                <Dropdown
                    className={styles.language}
                    label={currentOption.label}
                    value={currentOption.value}
                    size="medium"
                    isDisabled={false}
                    maxWidth="120px"
                    data={[defaultOption].concat(availableLanguages.filter(element => element.displayName !== language).map(element => {
                        return {
                            value: element.language,
                            label: element.displayName
                        };
                    }))}
                    onChange={handleOnChange}
                />
                <Typography className={styles.label}>
                    {t('translation-deepl:label.dialog.translationFrom.currentLanguage')}
                </Typography>
                <Typography>{language}</Typography>
            </DialogContent>
            <DialogActions>
                <Typography className={styles.warningText}>
                    <Warning
                        className={styles.warningIcon} /> {t('translation-deepl:label.dialog.translationFrom.bottomText')}
                </Typography>
                <Button
                    size="big"
                    color="default"
                    label={t('translation-deepl:label.dialog.translationFrom.btnCancel')}
                    onClick={handleCancel}
                />
                <Button
                    size="big"
                    color="accent"
                    label={t('translation-deepl:label.dialog.translationFrom.btnApply')}
                    disabled={isApplyDisabled}
                    onClick={handleClick}
                />
            </DialogActions>
        </Dialog>
    );
};

TranslateContentFromDialog.propTypes = {
    formik: PropTypes.object.isRequired,
    language: PropTypes.string.isRequired,
    availableLanguages: PropTypes.array.isRequired,
    isOpen: PropTypes.bool.isRequired,
    uuid: PropTypes.string.isRequired,
    onCloseDialog: PropTypes.func.isRequired
};