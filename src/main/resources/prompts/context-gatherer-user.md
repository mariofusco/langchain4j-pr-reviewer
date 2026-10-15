## Task: contextGatherer

Collect the code context that the reviewers of this pull request need but cannot see in the diff.

{{PrOverview}}

Triage:
{{PrSummary}}

The reviewed diff:
<diff>
{{DiffText}}
</diff>

You can read the repository with tools. Use the owner, repository and head sha shown in the overview:
- get_file_contents with owner, repo, path and sha set to the head sha, to read a file as of this pull request;
- search_code with a query such as "methodName repo:owner/repo", to find callers and usages;
- get_commit and list_commits, to understand recent history; issue_read, to read referenced issues.
Make at most twelve tool calls, starting with what matters most: callers of changed public or protected methods, implemented interfaces and overridden methods, the existing tests of the changed classes, and the definitions of types the change relies on.

Then answer with the useful context only, at most about 4000 tokens: for each excerpt, its path and line range, the relevant code quoted verbatim, and one sentence on why it matters for the review. Do not review the change yourself and do not repeat the diff. If nothing outside the diff is needed, answer "No additional context needed."
