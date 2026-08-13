# Security policy

Please report suspected vulnerabilities privately through the repository's
GitHub Security Advisory page. Do not open a public issue for a vulnerability.

Include the affected revision, a minimal input when safe to share, the observed
behavior, and the expected resource or trust boundary. Reports involving parser
limits, decompression, font/image decoding, imported objects, or generated active
content are especially useful.

Security fixes target the current `main` branch. There is no commitment to patch
obsolete preview revisions. See [docs/security.md](docs/security.md) for the
library's parser and execution model.
