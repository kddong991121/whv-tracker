-- 재방문 지표(PRD 4.9b): 근무를 적은 시각. 기존 행은 알 수 없어 NULL로 둔다.
ALTER TABLE work_day ADD COLUMN created_at TIMESTAMP NULL;
