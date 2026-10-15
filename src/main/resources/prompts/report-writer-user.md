## Task: reportWriter

Write the prose of the review report.

{{prOverview}}

Triage:
{{prSummary}}

Usefulness assessment:
{{usefulness}}

Findings still standing:
{{activeFindings}}

Scores:
{{scores}}

Verdict: {{verdict}}
Verdict reasoning: {{verdictReasoning}}

Known limitations of this review:
{{limitations}}

Guidance from the human reviewer, if any:
{{reviewerGuidance}}

Your previous draft, if any:
{{reportDraft}}

The critique of your previous draft, if any (fix every issue it lists):
{{reportCritique}}

Return:
- summary: one paragraph saying what the pull request does and your overall assessment, consistent with the verdict;
- good: what is good about the change, specific points, empty if nothing stands out;
- wrongFindingIds: the ids of the findings that belong under "What is wrong", most important first; every CRITICAL, HIGH or MEDIUM finding belongs there;
- improvements: concrete suggestions that are not already findings, such as follow-ups or simplifications, each actionable;
- limitations: anything a reader should know about the reliability of this review that is not in the known limitations above, usually empty.
