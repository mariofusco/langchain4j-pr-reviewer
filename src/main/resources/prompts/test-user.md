## Task: testReviewer

# The pull request under review

{{reviewBundle}}

# Your review

Review whether the behaviour changed by the pull request is covered by meaningful tests. Look in particular for:
- changed or new behaviour without any test, especially bug fixes without a test reproducing the bug;
- edge cases of the change left untested: null, empty, boundaries, error paths, concurrency;
- weak assertions: tests that only check that no exception is thrown, assertions on toString, assertions that cannot fail;
- tests that mirror the implementation instead of checking behaviour, excessive mocking;
- flaky patterns: Thread.sleep, dependence on time zones, ordering of HashMap or HashSet, shared static state, real network calls in unit tests;
- conventions of the project visible in the diff (JUnit 5, AssertJ, naming, test location).

Rules:
- Report each gap with an honest confidence from 0.0 to 1.0.
- Use the category TESTS.
- file: the production or test file the finding is about, exactly as written after "### File:". line: the number shown in the diff for the line in the new file, or 0 for the file as a whole.
- evidence: quote the exact code from the diff that is untested or the weak test.
- severity: HIGH for a bug fix or risky change without any test, MEDIUM for important untested cases, LOW for minor gaps, NIT for trivia.
- suggestion: the test to add, briefly.
- When the tests are adequate, return an empty list.
