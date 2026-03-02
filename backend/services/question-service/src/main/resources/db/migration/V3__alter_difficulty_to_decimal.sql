-- V3: Change difficulty from VARCHAR(32) to DECIMAL(3,2) for P-value storage (0.00-1.00)
ALTER TABLE q_question
    MODIFY COLUMN difficulty DECIMAL(3,2) NULL COMMENT 'P-value difficulty coefficient 0.00-1.00';
