"use client";
import {useId} from "react";

/** Native radios keep selection, keyboard navigation and labels available to assistive tools. */
export default function PreferenceChoice({title, detail, enabled, disabled = false, saving = false, onChange}: {
    title: string; detail?: string; enabled: boolean; disabled?: boolean; saving?: boolean; onChange: (enabled: boolean) => void;
}) {
    const name = useId();
    return <fieldset className="permission-choice" disabled={disabled}>
        <legend>{title}</legend>
        {detail && <p>{detail}</p>}
        <div className="permission-choice-options">
            {[{value: false, label: "Off", detail: "Not enabled"}, {value: true, label: "On", detail: "Enabled"}].map(option =>
                <label key={option.label} className={enabled === option.value ? "is-selected" : ""}>
                    <input type="radio" aria-label={option.label} name={name} checked={enabled === option.value} onChange={() => onChange(option.value)} />
                    <span><strong>{option.label}</strong><small>{option.detail}</small></span>
                </label>)}
        </div>
        {saving && <small role="status">Saving your choice…</small>}
    </fieldset>;
}
