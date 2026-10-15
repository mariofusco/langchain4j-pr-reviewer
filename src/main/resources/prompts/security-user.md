## Task: securityReviewer

# The pull request under review

{{ReviewBundle}}

# Your review

Review the diff for security vulnerabilities introduced or left open by the change. In Java code, look in particular for:
- injection: SQL built by string concatenation instead of bound parameters, OS commands (Runtime.exec, ProcessBuilder) with untrusted input, path traversal in file access, SSRF through URLs built from input, XML external entities (DocumentBuilderFactory, SAXParserFactory, XMLInputFactory without secure processing), expression or script evaluation, log injection;
- unsafe deserialization (ObjectInputStream, polymorphic JSON typing, XMLDecoder);
- authentication and authorization checks missing, bypassable or moved;
- secrets, tokens or keys committed in code or configuration, or written to logs and exception messages;
- weak cryptography: broken algorithms, ECB mode, static IVs or salts, java.util.Random for security purposes, disabled certificate or host name verification;
- information disclosure through error messages, logs or responses;
- denial of service: unbounded reads, allocations or recursion driven by input, regular expressions prone to catastrophic backtracking, resources never released;
- dependency changes in pom.xml or Gradle files: new dependencies, version downgrades, snapshot or unpinned versions, known vulnerable versions.

Rules:
- Report every real vulnerability you find, each with an honest confidence from 0.0 to 1.0. Do not report theoretical issues that the code makes impossible.
- Use the category SECURITY and start the title with the CWE id, for example "CWE-89: SQL injection in findUser".
- file: the path exactly as written after "### File:". line: the number shown in the diff for the line in the new file, or 0 when the finding concerns the file as a whole.
- evidence: quote the exact code from the diff.
- severity: CRITICAL for remotely exploitable issues with severe impact, HIGH for exploitable issues needing some preconditions, MEDIUM for defence-in-depth gaps with a plausible attack, LOW for hardening, NIT for trivia.
- suggestion: a concrete fix.
- When you find nothing, return an empty list.
