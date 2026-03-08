-- V6: Add 'source' column to q_question for tracking question origin.
ALTER TABLE q_question
    ADD COLUMN source VARCHAR(255) DEFAULT '未分类' AFTER difficulty;

UPDATE q_question SET source = '未分类' WHERE source IS NULL;
