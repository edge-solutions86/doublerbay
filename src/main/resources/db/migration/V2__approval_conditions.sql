-- Carry the conditions an approval came back with.
--
-- An approval is almost never unconditional. It comes back as a category and a sheet number with
-- two or three lines under it: inspect at every C check, do not extend past the next heavy, seal
-- both faces. Storing the category and dropping the conditions is how a fleet ends up flying a
-- repair to a condition nobody recorded. They go in one column, newline separated: there are never
-- more than a handful, they are free text, and nothing queries them.

ALTER TABLE engineering_order ADD COLUMN conditions VARCHAR(1024);
