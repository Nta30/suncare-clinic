-- Online assignments use the morning and afternoon shifts configured by administrators.
-- V15 remains immutable; online work now requires a ShiftDefinition like onsite work.

ALTER TABLE dbo.WorkAssignment DROP CONSTRAINT CK_WorkAssignment_mode;
ALTER TABLE dbo.WorkAssignment WITH CHECK ADD CONSTRAINT CK_WorkAssignment_mode
    CHECK (
        (mode = 'ONSITE'
            AND room_id IS NOT NULL
            AND shift_id IS NOT NULL
            AND specialty_id IS NOT NULL
            AND capacity IS NOT NULL
            AND capacity > 0)
        OR
        (mode = 'ONLINE'
            AND room_id IS NOT NULL
            AND shift_id IS NOT NULL
            AND specialty_id IS NOT NULL
            AND capacity IS NULL)
        OR
        (mode = 'LAB'
            AND room_id IS NOT NULL
            AND capacity IS NULL)
    );
