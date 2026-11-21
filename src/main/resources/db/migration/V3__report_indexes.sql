-- Indexes for the queries the reports actually run.
--
-- None of these change an answer. They change how long the answer takes, which starts to matter at
-- about eight hundred cards and stops being tolerable at about four thousand. Each one is a
-- composite index over the columns one query filters on, in the order it filters on them.

CREATE INDEX ix_repair_fitted ON repair (serial, part, removed);
CREATE INDEX ix_deferral_live ON deferral (lifted, granted);
CREATE INDEX ix_corrosion_here_before ON corrosion_entry (serial, part, found_on);
