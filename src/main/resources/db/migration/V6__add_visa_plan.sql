-- F5: 1년차 비자 만료일과 완료 목표 버퍼 일수 (계정별, 둘 다 선택 입력).
ALTER TABLE users ADD COLUMN visa_expiry DATE NULL;
ALTER TABLE users ADD COLUMN buffer_days INT NULL;
