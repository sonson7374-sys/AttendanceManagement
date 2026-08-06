-- V010: 근무지 유형/상세주소/허용정확도/출퇴근 허용여부 컬럼 추가

ALTER TABLE workplaces ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'OFFICE';
ALTER TABLE workplaces ADD COLUMN detail_address VARCHAR(200);
ALTER TABLE workplaces ADD COLUMN max_accuracy_meters INT;
ALTER TABLE workplaces ADD COLUMN check_in_allowed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE workplaces ADD COLUMN check_out_allowed BOOLEAN NOT NULL DEFAULT TRUE;
