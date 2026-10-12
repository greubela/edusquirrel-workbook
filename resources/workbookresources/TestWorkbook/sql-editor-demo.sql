-- Synthetic teaching data for the SQL section at homepage/workbookDesign/.
-- Run once with a provisioning account; repeated imports keep existing rows.
-- Grant the separate SQL_EDITOR_USER access only to this teaching database.
CREATE DATABASE IF NOT EXISTS school_exercises;
USE school_exercises;

CREATE TABLE IF NOT EXISTS teachers (
  id INT PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS students (
  id INT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  grade INT NOT NULL,
  teacher_id INT NOT NULL,
  CONSTRAINT fk_sql_demo_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id)
);

INSERT IGNORE INTO teachers (id, name) VALUES
  (1, 'Alex Weber'),
  (2, 'Robin Fischer');

INSERT IGNORE INTO students (id, name, grade, teacher_id) VALUES
  (1, 'Lina', 10, 1),
  (2, 'Sam', 11, 2),
  (3, 'Noah', 9, 1);
