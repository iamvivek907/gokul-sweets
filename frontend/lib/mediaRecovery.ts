/** Avoid fetching animated media when motion is reduced or data saving is requested. */
export function useStaticCampaignMedia(animated: boolean, reducedMotion: boolean, saveData: boolean,
    failed: boolean, visible: boolean): boolean {
    return animated && (reducedMotion || saveData || failed || !visible);
}
