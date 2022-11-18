import React from 'react';
import {menuAction,
        MenuItemRenderer,
        MenuRenderer,
        registry
        } from '@jahia/ui-extender';
import {RequestTranslationDeeplAction} from './RequestTranslationDeeplAction';
//import {RequestTranslationDeeplActionForAllLanguagesAction} from './RequestTranslationDeeplForAllLanguagesAction';

export default function () {

    const menuActionWithRenderer = registry.add('action', 'menuActionDeepl', menuAction, {
        buttonIcon: window.jahia.moonstone.toIconComponent('<svg data-name="Layer 1" id="Layer_1" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg"><defs><style>.cls-1{fill:#ba63c6;}</style></defs><title/><path class="cls-1" d="M31.79,28.11l-7-14a2,2,0,0,0-3.58,0L18,20.56,15.3,18.73A17.13,17.13,0,0,0,19.91,9H22a2,2,0,0,0,0-4H14V3a2,2,0,0,0-4,0V5H2A2,2,0,0,0,2,9H15.86a13.09,13.09,0,0,1-3.79,7.28,13.09,13.09,0,0,1-3.19-4.95,2,2,0,1,0-3.77,1.34A17.1,17.1,0,0,0,8.9,18.75L3.84,22.37a2,2,0,0,0,2.33,3.25l5.93-4.24,4.08,2.79-2,3.93a2,2,0,0,0,3.58,1.79l.45-.89H23a2,2,0,0,0,0-4H20.24L23,19.47l5.21,10.42a2,2,0,0,0,3.58-1.79Z"/></svg>'),
        menuRenderer: MenuRenderer,
        menuItemRenderer: MenuItemRenderer
    });

    registry.add('action', 'translationMenu', menuActionWithRenderer, {
        buttonLabel: 'translation-deepl:label.menu',
        targets: ['contentActions:999'],
        menuTarget: 'translationMenu',
        isMenuPreload: true
    });

    registry.add('action', 'translation-deepl-requestTranslation ', {
        targets: ['translationMenu:1'],
        showOnNodeTypes: ['jnt:page', 'jnt:content'],
        subTree: false,
        allLanguages: false,
        component: RequestTranslationDeeplAction
    });

    registry.add('action', 'translation-deepl-requestTranslationForAllLanguages', {
        buttonIcon: window.jahia.moonstone.toIconComponent('<svg data-name="Layer 1" id="Layer_1" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg"><defs><style>.cls-1{fill:#ba63c6;}</style></defs><title/><path class="cls-1" d="M31.79,28.11l-7-14a2,2,0,0,0-3.58,0L18,20.56,15.3,18.73A17.13,17.13,0,0,0,19.91,9H22a2,2,0,0,0,0-4H14V3a2,2,0,0,0-4,0V5H2A2,2,0,0,0,2,9H15.86a13.09,13.09,0,0,1-3.79,7.28,13.09,13.09,0,0,1-3.19-4.95,2,2,0,1,0-3.77,1.34A17.1,17.1,0,0,0,8.9,18.75L3.84,22.37a2,2,0,0,0,2.33,3.25l5.93-4.24,4.08,2.79-2,3.93a2,2,0,0,0,3.58,1.79l.45-.89H23a2,2,0,0,0,0-4H20.24L23,19.47l5.21,10.42a2,2,0,0,0,3.58-1.79Z"/></svg>'),
        targets: ['translationMenu:2'],
        showOnNodeTypes: ['jnt:page', 'jnt:content'],
        subTree: false,
        allLanguages: true,
        component: RequestTranslationDeeplAction
    });

    registry.add('action', 'translation-deepl-requestTranslationTree ', {
        targets: ['translationMenu:3'],
        showOnNodeTypes: ['jnt:page', 'jnt:content'],
        subTree: true,
        allLanguages: false,
        component: RequestTranslationDeeplAction
    });

    registry.add('action', 'translation-deepl-requestTranslationTreeForAllLanguages', {
        targets: ['translationMenu:4'],
        showOnNodeTypes: ['jnt:page', 'jnt:content'],
        subTree: true,
        allLanguages: true,
        component: RequestTranslationDeeplAction
    });




//    registry.add('action', 'translation-deepl-requestTranslationForAllLanguages', {
//        targets: ['translationMenu:1'],
//        showOnNodeTypes: ['jnt:page', 'jnt:content'],
//        component: <RequestTranslationDeeplAction subTree='false' allLanguages='true' />
//    });
//
//    registry.add('action', 'translation-deepl-requestTranslation', {
//        targets: ['translationMenu:2'],
//        showOnNodeTypes: ['jnt:page', 'jnt:content'],
//        component: <RequestTranslationDeeplAction subTree="false" allLanguages="false" />
//    });
}
