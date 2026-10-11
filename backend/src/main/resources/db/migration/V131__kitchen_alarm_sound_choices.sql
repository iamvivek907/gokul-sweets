ALTER TABLE print_station_controls ADD COLUMN alarm_sound_profile VARCHAR(16) NOT NULL DEFAULT 'BUZZER'
 CHECK (alarm_sound_profile IN ('BUZZER','BELL','CHIME'));
ALTER TABLE print_station_controls ADD COLUMN alarm_sound_version INTEGER NOT NULL DEFAULT 0
 CHECK (alarm_sound_version BETWEEN 0 AND 1);
