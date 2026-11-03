import {readFileSync} from "node:fs";
import {join} from "node:path";

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

// Reuse the installed icon artwork without an extra first-paint request.
const launchMark = readFileSync(join(process.cwd(), "public", "icons", "icon-192.png")).toString("base64");

// First paint must not wait for the stylesheet bundle. Only the SSR boot surface matches.
export const criticalBootCss = `
.gokul-mobile-boot{display:none}
@media(max-width:640px){
 .gokul-mobile-boot,.gokul-mobile-launch{--gokul-launch-mark:url(data:image/png;base64,${launchMark})}
 .gokul-mobile-boot{display:flex;position:fixed;inset:0;z-index:1000;align-items:center;justify-content:center;flex-direction:column;overflow:hidden;background:#980c31;color:#fff1d7;pointer-events:none}
 .gokul-mobile-boot::before{content:"";width:min(61vw,270px);aspect-ratio:1;background:var(--gokul-launch-mark) center/contain no-repeat}
}
`;
