-- D24: Online consultations use a managed room resource like physical rooms.
-- Keep V1-V14 immutable; migrate existing Room and WorkAssignment rules forward.

ALTER TABLE dbo.Room DROP CONSTRAINT CK_Room_type;
ALTER TABLE dbo.Room WITH CHECK ADD CONSTRAINT CK_Room_type
    CHECK (room_type IN ('CONSULTATION', 'LAB', 'ONLINE'));

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
            AND specialty_id IS NOT NULL
            AND shift_id IS NULL
            AND capacity IS NULL)
        OR
        (mode = 'LAB'
            AND room_id IS NOT NULL
            AND capacity IS NULL)
    );
