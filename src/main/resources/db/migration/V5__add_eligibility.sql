-- F1·F2 연결 (PRD 4.2.8): 일터 저장 시점의 인정 지역 판정 스냅샷.
-- work_type: 농장 작업 종류 (HARVEST_PACKING / MANUFACTURING / GARDENING), 농장 업종이 아니면 NULL.
-- eligibility_status: EligibilityResult.Status 이름. NULL이면 (V5 이전 기록) 조회 시 다시 판정한다.
-- condition_confirmed: "조건부" 판정을 사용자가 충족했다고 체크했는지.
ALTER TABLE work_record ADD COLUMN work_type VARCHAR(20) NULL;
ALTER TABLE work_record ADD COLUMN eligibility_status VARCHAR(30) NULL;
ALTER TABLE work_record ADD COLUMN condition_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
