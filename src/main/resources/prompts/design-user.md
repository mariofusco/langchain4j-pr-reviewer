## Task: designReviewer

# The pull request under review

{{reviewBundle}}

# Your review

Review the diff for conciseness and design quality. Look in particular for:
- more code than the problem needs: speculative abstractions, interfaces with a single implementation and no reason, configurability nobody asked for, layers that only delegate;
- dead code, commented-out code, unused parameters, fields, imports or methods introduced by the change;
- duplicated logic that already exists in the diff or in the context shown;
- changes unrelated to the stated intent of the pull request;
- naming that misleads, inconsistent with the surrounding code;
- misplaced responsibilities: classes or methods doing too much, logic in the wrong layer, package or module boundaries crossed;
- Java idioms: records, sealed types, switch expressions, streams or plain loops where they make the code clearly simpler, and only then;
- inconsistency with the conventions of the surrounding code visible in the diff.

Rules:
- Report findings that a maintainer would act on, each with an honest confidence from 0.0 to 1.0. Do not report personal taste.
- Use the category DESIGN.
- file: the path exactly as written after "### File:". line: the number shown in the diff for the line in the new file, or 0 when the finding concerns the file as a whole.
- evidence: quote the exact code from the diff.
- severity: HIGH only for design problems that will clearly cause defects or block maintenance, MEDIUM for significant unnecessary complexity, LOW for local improvements, NIT for trivia.
- suggestion: the simpler alternative.
- Do not report functional defects, security, tests or documentation: other reviewers cover them.
- When you find nothing, return an empty list.
