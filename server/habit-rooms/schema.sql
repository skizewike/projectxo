-- Комнаты общих привычек (Р79). Хранится минимум: название привычки, псевдонимы и отметки «сделано» по дням.
-- Ни имён, ни почты, ни телефона, ни адресов. Секрет участника хранится только как хеш.

CREATE TABLE IF NOT EXISTS room (
  id TEXT PRIMARY KEY,
  code TEXT NOT NULL,
  code_expires INTEGER NOT NULL,
  title TEXT NOT NULL,
  freq_mask INTEGER NOT NULL,
  created INTEGER NOT NULL,
  last_active INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS room_code ON room(code);
CREATE INDEX IF NOT EXISTS room_active ON room(last_active);

CREATE TABLE IF NOT EXISTS member (
  id TEXT PRIMARY KEY,
  room_id TEXT NOT NULL,
  nick TEXT NOT NULL,
  secret_hash TEXT NOT NULL,
  joined INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS member_room ON member(room_id);

CREATE TABLE IF NOT EXISTS mark (
  room_id TEXT NOT NULL,
  member_id TEXT NOT NULL,
  day TEXT NOT NULL,
  PRIMARY KEY (room_id, member_id, day)
);

-- Счётчики запросов против перебора кодов и спама. Ключ содержит хеш адреса, сам адрес не хранится.
CREATE TABLE IF NOT EXISTS counter (
  key TEXT PRIMARY KEY,
  n INTEGER NOT NULL,
  expires INTEGER NOT NULL
);
