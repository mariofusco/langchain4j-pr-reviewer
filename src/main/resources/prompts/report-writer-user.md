## Task: reportWriter

Write the prose of the review report.

{{PrOverview}}

Triage:
{{PrSummary}}

Usefulness assessment:
{{Usefulness}}

Findings still standing:
{{ActiveFindings}}

Scores:
{{Scores}}

Verdict: {{Verdict}}
Verdict reasoning: {{VerdictReasoning}}

Known limitations of this review:
{{Limitations}}

Guidance from the human reviewer, if any:
{{ReviewerGuidance}}

Your previous draft, if any:
{{ReportDraft}}

The critique of your previous draft, if any (fix every issue it lists):
{{ReportCritique}}

Return:
- summary: one paragraph saying what the pull request does and your overall assessment, consistent with the verdict;
- good: what is good about the change, specific points, empty if nothing stands out;
- wrongFindingIds: the ids of the findings that belong under "What is wrong", most important first; every CRITICAL, HIGH or MEDIUM finding belongs there;
- improvements: concrete suggestions that are not already findings, such as follow-ups or simplifications, each actionable;
- limitations: anything a reader should know about the reliability of this review that is not in the known limitations above, usually empty.
