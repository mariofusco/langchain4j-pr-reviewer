## Task: correctnessReviewer

# The pull request under review

{{ReviewBundle}}

# Your review

Review the diff for functional defects in the changed code. In Java code, look in particular for:
- null handling: values that can be null used without a check, NullPointerException paths, Optional misused (get() without a presence check, Optional fields or parameters);
- equals, hashCode and compareTo contracts broken, mutable objects used as hash keys;
- resources not closed on every path (streams, readers, connections, executors), missing try-with-resources;
- exceptions swallowed, causes lost, overly broad catch blocks, changed behaviour on error paths;
- concurrency: shared mutable state without synchronization, visibility problems, check-then-act races, blocking calls on shared executors, failures of CompletableFuture chains that are never observed, deadlocks;
- unchecked casts and raw types that can fail at run time;
- collections and streams: modification during iteration, writes to unmodifiable collections, ordering assumed from HashMap or HashSet, wrong collectors;
- arithmetic: integer overflow, off-by-one errors in loops and ranges, integer division, floating point equality;
- logic: inverted conditions, wrong operators, missing switch cases, behaviour that does not match the stated intent of the pull request;
- misuse of JDK or library APIs.

Rules:
- Report every real defect you find, each with an honest confidence from 0.0 to 1.0. Findings are validated, deduplicated and, when doubtful, debated afterwards, so do not hold back a likely defect and do not inflate a speculative one.
- Use the category CORRECTNESS.
- file: the path exactly as written after "### File:". line: the number shown in the diff for the line in the new file, or 0 when the finding concerns the file as a whole.
- evidence: quote the exact code from the diff that shows the defect.
- severity: CRITICAL for data loss or crashes on common paths, HIGH for wrong results or crashes on plausible paths, MEDIUM for edge cases, LOW for minor robustness issues, NIT for trivia.
- suggestion: a concrete fix, ideally a short code change.
- Do not report style, naming, tests or documentation: other reviewers cover them.
- When you find nothing, return an empty list.
