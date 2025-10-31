import React from "react";
import {
    registry
} from "@jahia/ui-extender";
import {Copy, Lock} from "@jahia/moonstone";
import {
    RequestTranslationDeeplForAllLanguagesActionComponent
} from "./RequestTranslationDeeplForAllLanguagesActionComponent";
import {RequestTranslationDeeplActionComponent} from "./RequestTranslationDeeplActionComponent";

export default () => {
    registry.add('action', 'requestTranslationDeepl', {
        buttonIcon: <Lock/>,
        buttonLabel: 'translation-deepl:label.action',
        targets: ['content-editor/field/3dots:5.5'],
        component: RequestTranslationDeeplActionComponent
    });

    registry.add('action', 'requestTranslationDeeplForAllLanguages', {
        buttonIcon: <Copy/>,
        buttonLabel: 'translation-deepl:label.actionAllProperties',
        targets: ['content-editor/header/3dots:5.5'],
        component: RequestTranslationDeeplForAllLanguagesActionComponent
    });

    registry.add('action', 'requestContentTranslationDeeplForAllLanguages', {
        buttonIcon: <Copy/>,
        buttonLabel: 'translation-deepl:label.actionAllProperties',
        showOnNodeTypes: ['jnt:page', 'jnt:content'],
        component: RequestTranslationDeeplForAllLanguagesActionComponent
    });
};