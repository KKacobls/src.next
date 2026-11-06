// ==UserScript==
// @name         KK Browser Tampermonkey verification
// @namespace    https://github.com/KKacobls/src.next
// @version      1.0.0
// @description  Verifies that the ORIGINAL Tampermonkey injected a userscript.
// @match        https://example.com/*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(function () {
    'use strict';
    if (document.getElementById('kk-tampermonkey-success')) return;
    const badge = document.createElement('div');
    badge.id = 'kk-tampermonkey-success';
    badge.textContent = '✅ Tampermonkey 已在 KK Gecko 瀏覽器中自動執行！';
    Object.assign(badge.style, {
        position: 'fixed',
        zIndex: '2147483647',
        top: '15px',
        left: '12px',
        right: '12px',
        padding: '14px',
        background: '#14532d',
        borderRadius: '10px',
        color: 'white',
        fontSize: '15px',
        fontFamily: 'sans-serif',
        boxShadow: '0 4px 12px rgba(0,0,0,.25)'
    });
    document.body.appendChild(badge);
    console.log('[KK] Original Tampermonkey userscript test succeeded.');
})();
