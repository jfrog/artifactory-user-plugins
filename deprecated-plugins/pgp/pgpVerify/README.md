Artifactory PGP Verify User Plugin
==================================

Verifies downloaded files against their asc signature, by using the signature's
public key from a public key server. For remote repos, tries to fetch the .asc
signature. Result is cached in the `pgp-verified` property on artifacts, so that
subsequent checks are cheap. Artifacts that have not been verified will cause a
`403 Forbidden` response to be returned to downloaders.
