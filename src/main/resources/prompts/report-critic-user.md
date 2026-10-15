## Task: reportCritic

Check this review report before it is shown to the maintainers.

The report:
{{reviewReport}}

The findings it is based on:
{{activeFindings}}

Check that:
- every point under "What is wrong" is one of the findings, and every CRITICAL or HIGH finding is there;
- every claim about the code names a file and line;
- the summary, the scores and the verdict are consistent with each other and with the findings;
- the advice is concrete and actionable, with no vague statements such as "consider improving the code";
- no point is duplicated across sections;
- the limitations are disclosed when the review is partial.

Return a score from 0.0 to 1.0, where 1.0 means ready to publish, and the list of issues to fix, empty when there are none.
