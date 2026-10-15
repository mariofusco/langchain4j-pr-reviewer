## Task: debateJudge

Decide whether the following finding on a pull request is real, from the debate between a prosecutor and a defender.

The finding:
{{contestedFinding}}

The debate:
{{debateContext}}

Return:
- outcome: CONFIRMED when the finding is real, REJECTED when it is a false positive, UNSURE when the debate does not settle it;
- adjustedSeverity: the severity the finding deserves, the same as reported or lower (CRITICAL, HIGH, MEDIUM, LOW, NIT);
- reasoning: two or three sentences explaining the decision.
