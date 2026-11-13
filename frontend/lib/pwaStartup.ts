/** Native startup images require the exact portrait device size and pixel ratio. */
const portraitIphones = [
    [375, 667, 2],
    [375, 812, 3],
    [390, 844, 3],
    [393, 852, 3],
    [402, 874, 3],
    [414, 896, 2],
    [414, 896, 3],
    [428, 926, 3],
    [430, 932, 3],
    [440, 956, 3],
] as const;

export const appleStartupImages = portraitIphones.map(([width, height, ratio]) => ({
    url: `/startup/launch-${width * ratio}x${height * ratio}.png`,
    media: `(device-width: ${width}px) and (device-height: ${height}px) and (-webkit-device-pixel-ratio: ${ratio}) and (orientation: portrait)`,
}));

// Inline the existing app monogram (public/icons/app-icon.svg), also used for the native PNGs.
const launchMark = encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><title>Gokul app monogram</title><rect width=\"512\" height=\"512\" fill=\"#980c31\"/><g transform=\"translate(236 88) scale(1)\"><g stroke=\"white\" stroke-width=\"2\" stroke-linejoin=\"round\" fill=\"none\"><path d=\"M20 40C12 33 5 28 5 19c7-1 12 3 15 9 3-6 8-10 15-9 0 9-7 14-15 21Z\"/><path d=\"M20 40V28M20 28c-5-6-7-12-4-20 5 3 8 10 4 20Z\" fill=\"white\"/><path d=\"M14 20C7 17 6 11 8 6c5 1 9 6 8 12M25 20c7-3 8-9 6-14-5 1-9 6-8 12\" fill=\"white\"/><path d=\"m20 3-3 4 3 3 3-3-3-4Z\" fill=\"white\"/></g></g><path fill=\"white\" transform=\"translate(121 385) scale(0.160619 -0.160619)\" d=\"M1530 111Q1386 41 1231.0 6.0Q1076 -29 911 -29Q538 -29 320.0 179.5Q102 388 102 745Q102 1106 324.0 1313.0Q546 1520 932 1520Q1081 1520 1217.5 1492.0Q1354 1464 1475 1409V1100Q1350 1171 1226.5 1206.0Q1103 1241 979 1241Q749 1241 624.5 1112.5Q500 984 500 745Q500 508 620.0 379.0Q740 250 961 250Q1021 250 1072.5 257.5Q1124 265 1165 281V571H930V829H1530Z\"/></svg>");

// First paint must not wait for the stylesheet bundle. Only the SSR boot surface matches.
export const criticalBootCss = `
.gokul-mobile-boot{display:none}
.gokul-mobile-boot[data-gokul-storefront-boot]{display:flex;position:fixed;inset:0;z-index:1000;align-items:center;justify-content:center;flex-direction:column;gap:.75rem;background:#980c31;color:#fff1d7;font:600 1rem/1.4 system-ui,sans-serif;pointer-events:none}
/* This fallback uses only the initial HTML and inline CSS, even if app scripts fail. */
.gokul-boot-recovery{visibility:hidden;text-align:center;animation:gokul-boot-recovery 0s 12s forwards}
.gokul-boot-recovery a{pointer-events:auto;display:inline-flex;align-items:center;min-height:44px;padding:0 .9rem;border-radius:999px;background:#fff1d7;color:#631430;text-decoration:none}
.gokul-boot-live-progress{display:none}
[data-live-launch-recovery=retrying] .gokul-boot-opening{display:none}
[data-live-launch-recovery=retrying] .gokul-boot-live-progress{display:block}
[data-live-launch-recovery=retrying] .gokul-boot-recovery{display:none}
[data-live-launch-recovery=manual] .gokul-boot-recovery{visibility:visible;animation:none}
.gokul-boot-live-progress i{display:inline-block;width:.45em;height:.45em;margin:.65em .18em 0;border-radius:50%;background:currentColor;animation:gokul-live-retry-pulse 1s ease-in-out infinite alternate}
.gokul-boot-live-progress i:nth-child(2){animation-delay:.15s}
.gokul-boot-live-progress i:nth-child(3){animation-delay:.3s}
@keyframes gokul-live-retry-pulse{to{opacity:.45;transform:translateY(-.3em)}}
@media(prefers-reduced-motion:reduce){.gokul-boot-live-progress i{animation:none}}
.gokul-boot-recovery a:focus-visible{outline:3px solid #ffcf89;outline-offset:4px}
@keyframes gokul-boot-recovery{to{visibility:visible}}
@media(max-width:640px){
 .gokul-mobile-boot,.gokul-mobile-launch{--gokul-launch-mark:url("data:image/svg+xml,${launchMark}")}
 .gokul-mobile-boot{display:flex;position:fixed;inset:0;z-index:1000;align-items:center;justify-content:center;flex-direction:column;overflow:hidden;background:#980c31;color:#fff1d7;pointer-events:none}
 /* iOS startup images use the bundled mark. Android launchers may have a custom icon. */
 @supports (-webkit-touch-callout: none){.gokul-mobile-boot::before{content:"";width:min(61vw,270px);aspect-ratio:1;background:var(--gokul-launch-mark) center/contain no-repeat}}
}
`;

// Runs before hydration. A worker can detect a failed stream even when the app
// bundles never arrive. Use the same two attempts as the worker's recovery page.
export const liveLaunchRecoveryScript = `(() => {
    if (!/^(?:\\/|\\/menu\\/?|\\/branches(?:\\/[1-9]\\d*)?\\/?)$/.test(location.pathname) || !navigator.serviceWorker) return;
    const key = "gokulLaunchRetry";
    const delays = [650, 1200];
    const boot = () => document.querySelector("[data-gokul-storefront-boot]");
    let timer;
    function recover(screen) {
        if (!screen || screen.hasAttribute("data-live-launch-recovery")) return;
        const previous = history.state?.[key];
        const continuing = performance.getEntriesByType("navigation")[0]?.type === "reload"
            && previous?.url === location.href && Number.isInteger(previous.attempt) && previous.attempt >= 0;
        const attempt = continuing ? previous.attempt : 0;
        screen.setAttribute("data-live-launch-recovery", "manual");
        if (attempt >= delays.length) return;
        try {
            history.replaceState({...history.state, [key]: {url: location.href, attempt: attempt + 1}}, "");
        } catch { return; }
        screen.setAttribute("data-live-launch-recovery", "retrying");
        timer = setTimeout(() => { if (boot() === screen) location.reload(); }, delays[attempt]);
    }
    navigator.serviceWorker.addEventListener("message", event => {
        if (event.source !== navigator.serviceWorker.controller || event.data?.type !== "gokul-launch-stream-incomplete") return;
        const screen = boot();
        if (screen) recover(screen);
        else if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", () => recover(boot()), {once: true});
        }
    });
    // Allow a stalled body to report failure without waiting for DOMContentLoaded.
    navigator.serviceWorker.startMessages?.();
    document.addEventListener("click", event => {
        if (!event.target?.closest?.(".gokul-boot-recovery a")) return;
        try {
            const state = {...history.state};
            delete state[key];
            history.replaceState(state, "");
        } catch {}
    });
    addEventListener("pagehide", () => clearTimeout(timer));
    addEventListener("pageshow", event => {
        const screen = boot();
        if (event.persisted && screen?.getAttribute("data-live-launch-recovery") === "retrying") {
            screen.setAttribute("data-live-launch-recovery", "manual");
        }
    });
})();`;
