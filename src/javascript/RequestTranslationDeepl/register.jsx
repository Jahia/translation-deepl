import React from "react";
import {
    registry
} from "@jahia/ui-extender";

import {
    RequestTranslationDeeplForAllLanguagesActionComponent
} from "./RequestTranslationDeeplForAllLanguagesActionComponent";
import {RequestTranslationDeeplActionComponent} from "./RequestTranslationDeeplActionComponent";
import DeeplIcon from "./DeeplIcon";

export default () => {
    registry.add('action', 'requestTranslationDeepl', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'translation-deepl:label.action',
        targets: ['content-editor/field/3dots:5.5'],
        component: RequestTranslationDeeplActionComponent
    });

    registry.add('action', 'requestTranslationDeeplForAllLanguages', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'translation-deepl:label.actionAllProperties',
        targets: ['content-editor/header/3dots:5.5'],
        component: RequestTranslationDeeplForAllLanguagesActionComponent
    });
};