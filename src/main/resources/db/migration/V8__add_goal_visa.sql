-- F5 확장: 지금 준비 중인 비자. 2 = 세컨 준비(1년차 비자 중), 3 = 서드 준비(2년차 비자 중). 기본 2.
ALTER TABLE users ADD COLUMN goal_visa INT NOT NULL DEFAULT 2;
