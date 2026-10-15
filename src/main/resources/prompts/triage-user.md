## Task: prTriage

Triage this pull request.

{{PrOverview}}

The reviewed diff:
<diff>
{{DiffText}}
</diff>

Return:
- intent: one sentence stating what the change is meant to achieve, in your own words;
- category: BUGFIX, FEATURE, REFACTOR, PERFORMANCE, DOCS (documentation only), TEST (tests only), BUILD (build or CI only), DEPENDENCY (dependency updates only) or OTHER;
- size: SMALL below about 100 changed lines of production code, MEDIUM below about 500, LARGE otherwise;
- risk: HIGH when the change touches concurrency, security, persistence, public API, parsing of external input or core algorithms in a non-trivial way; LOW for documentation, tests, comments and mechanical changes; MEDIUM otherwise;
- areasTouched: the modules, packages or components touched, a few words each;
- needsContext: true only when the risk is at least MEDIUM and judging the change correctly requires code that is not in the diff, such as callers of changed methods, implemented interfaces or existing tests;
- openQuestions: up to three questions a human reviewer would need answered to judge the change, empty when everything is clear.
