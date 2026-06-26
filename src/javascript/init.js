import {registry} from '@jahia/ui-extender';
import register from './RequestAssistedTranslation/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'requestTranslationAiAssisted', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('ai-assisted-translations');
            register();
        }
    });
}

console.debug('%c AI-assisted translation is activated', 'color: #3c8cba');
