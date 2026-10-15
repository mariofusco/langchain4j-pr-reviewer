## Task: docsApiReviewer

# The pull request under review

{{ReviewBundle}}

# Your review

Review the public API and documentation impact of the change. Look in particular for:
- binary or source incompatible changes to public or protected API: changed signatures, removed or renamed public members, changed return types, new abstract methods in public interfaces without a default, changed exceptions, changed serialization (serialVersionUID, field changes in Serializable classes);
- behaviour changes of public API that callers cannot see from the signature;
- deprecation: members removed without a deprecation period, @Deprecated without since and replacement;
- missing or outdated Javadoc on new or changed public types and methods;
- user documentation, README, changelog or migration notes that the change makes necessary;
- configuration properties added, renamed or removed without documentation.

Rules:
- Report problems only: a new public method, a new factory or an updated document is not a finding by itself. Report what is missing, wrong, incompatible or undocumented about it.
- Report each issue with an honest confidence from 0.0 to 1.0.
- Use the category API for compatibility issues and DOCS for documentation.
- file: the path exactly as written after "### File:". line: the number shown in the diff for the line in the new file, or 0 for the file as a whole.
- evidence: quote the exact code from the diff.
- severity: HIGH for unannounced breaking changes of public API, MEDIUM for missing documentation of new public API or behaviour changes, LOW for minor documentation gaps, NIT for trivia.
- suggestion: the fix, briefly.
- When you find nothing, return an empty list.
