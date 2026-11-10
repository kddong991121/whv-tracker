-- F1 v4: 하루 여러 근무(shift). 고용주마다 "평소 하루 근무 시간"을 둔다.
-- 같은 날짜에 다른 고용주의 근무 행을 여러 개 둘 수 있다 (같은 고용주는 날짜당 1행, uk_work_day_record_date 유지).
-- 하루 판정: 근무 하나가 그 고용주의 평소 시간 이상이면 인정, 아니면 짧게 일한 날 (PRD 4.2.3).
ALTER TABLE work_record ADD COLUMN normal_hours DECIMAL(4,2) NULL;
